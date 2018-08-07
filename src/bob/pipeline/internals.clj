;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.pipeline.internals
  (:require [clojure.core.async :refer [go]]
            [korma.db :refer [defdb]]
            [korma.core :refer [update set-fields where
                                select insert values
                                fields]]
            [failjure.core :as f]
            [bob.db.core :refer [logs runs]]
            [bob.execution.internals :as e]
            [bob.util :refer [unsafe! format-id get-id]])
  (:import (java.util List)))

;; TODO: Reduce and optimize DB interactions to a single place

(defn update-pid
  [pid run-id]
  (f/attempt-all [_ (unsafe! (insert logs (values {:pid pid
                                                   :run run-id})))
                  _ (unsafe! (update runs
                                     (set-fields {:last_pid pid})
                                     (where {:id run-id})))]
    pid
    (f/when-failed [err] err)))

(defn- next-step
  [^String id ^List next-command]
  (let [repo (format "%s/%d" id (System/currentTimeMillis))
        tag  "latest"]
    (f/attempt-all [_ (unsafe! (.commitContainer e/docker
                                                 id
                                                 repo
                                                 tag
                                                 (e/config-of (-> e/docker
                                                                  (.inspectContainer id)
                                                                  (.config)
                                                                  (.image))
                                                              next-command)
                                                 nil
                                                 nil))]
      (e/build (format "%s:%s" repo tag) next-command)
      (f/when-failed [err] err))))

(defn- exec-step
  [run-id id step]
  (let [stopped? (unsafe! (-> (select runs
                                      (fields [:stopped])
                                      (where {:id run-id}))
                              (first)
                              (:stopped)))]
    (if (or stopped?
            (f/failed? id))
      (reduced id)
      (f/attempt-all [result (f/ok-> (next-step id (:cmd step))
                                     (update-pid run-id)
                                     (e/run))]
        result
        (f/when-failed [err] err)))))

(defn- next-build-number-of
  [name]
  (f/attempt-all [result (unsafe! (last (select runs
                                                (where {:pipeline name}))))]
    (if (nil? result)
      1
      (inc (result :number)))
    (f/when-failed [err] err)))

(defn exec-steps
  [^String image ^List steps ^String name]
  (let [run-id (get-id)]
    (go (f/attempt-all [_  (unsafe! (insert runs (values {:id       run-id
                                                          :number   (next-build-number-of name)
                                                          :pipeline name
                                                          :status   "running"})))
                        id (f/ok-> (e/pull image)
                                   (e/build (:cmd (first steps)))
                                   (update-pid run-id)
                                   (e/run))
                        id (reduce (partial exec-step run-id) id (rest steps))
                        _  (unsafe! (update runs
                                            (set-fields {:status "passed"})
                                            (where {:id run-id})))]
          id
          (f/when-failed [err] (do (unsafe! (update runs
                                                    (set-fields {:status "failed"})
                                                    (where {:id run-id})))
                                   (f/message err)))))))

(defn stop-pipeline
  [name number]
  (let [criteria {:pipeline name
                  :number   number}
        status   (unsafe! (-> (select runs
                                      (fields [:status])
                                      (where criteria))
                              (first)
                              (:status)))]
    (when (= status "running")
      (f/attempt-all [_      (unsafe! (update runs
                                              (set-fields {:stopped true})
                                              (where criteria)))
                      pid    (unsafe! (-> (select runs
                                                  (fields [:last_pid])
                                                  (where criteria))
                                          (first)
                                          (:last_pid)))
                      status (e/status-of pid)
                      _      (when (status :running)
                               (e/kill-container pid))]
        "Ok"
        (f/when-failed [err] (f/message err))))))
