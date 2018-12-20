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
  (:require [clojure.core.async :as a]
            [korma.core :as k]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.db.core :as db]
            [bob.execution.internals :as e]
            [bob.util :as u])
  (:import (java.util List)))

;; TODO: Reduce and optimize DB interactions to a single place

(defn update-pid
  "Sets the current container id to both logs and runs tables.
  This is used to track the last executed container in logging as well as stopping.
  Returns pid or error if any."
  [pid run-id]
  (f/attempt-all [_ (u/unsafe! (k/insert db/logs (k/values {:pid pid
                                                            :run run-id})))
                  _ (u/unsafe! (k/update db/runs
                                         (k/set-fields {:last_pid pid})
                                         (k/where {:id run-id})))]
    pid
    (f/when-failed [err] err)))

(defn- next-step
  "Generates the next container from a previously run container.
  Works by saving the last container state in a diffed image and
  creating a new container from it, thereby managing state externally.
  Returns the new container id or errors if any."
  [^String id ^List next-command ^List evars]
  (let [repo (format "%s/%d" id (System/currentTimeMillis))
        tag  "latest"]
    (f/attempt-all [_ (u/unsafe! (docker/commit-container
                                   e/conn
                                   id
                                   repo
                                   tag
                                   next-command))]
      (e/build (format "%s:%s" repo tag) next-command evars)
      (f/when-failed [err] err))))

(defn- exec-step
  "Reducer function to implement the sequential execution of steps.
  Used to reduce an initial state with the list of steps, executing
  them to the final state.
  Stops the reduce if the pipeline stop has been signalled or any
  non-zero step outcome.
  Returns the next state or errors if any."
  [run-id evars id step]
  (let [stopped? (u/unsafe! (-> (k/select db/runs
                                          (k/fields :stopped)
                                          (k/where {:id run-id}))
                                (first)
                                (:stopped)))]
    (if (or stopped?
            (f/failed? id))
      (reduced id)
      (f/attempt-all [result (f/ok-> (next-step id (:cmd step) evars)
                                     (update-pid run-id)
                                     (e/run))]
        result
        (f/when-failed [err] err)))))

(defn- next-build-number-of
  "Generates a sequential build number for a pipeline."
  [name]
  (f/attempt-all [result (u/unsafe! (last (k/select db/runs
                                                    (k/where {:pipeline name}))))]
    (if (nil? result)
      1
      (inc (result :number)))
    (f/when-failed [err] err)))

(defn exec-steps
  "Implements the sequential execution of the list of steps with a starting image.
  Dispatches asynchronously and uses a composition of the above functions.
  Returns the final id or errors if any."
  [^String image ^List steps ^String name ^List evars]
  (let [run-id (u/get-id)]
    (a/go (f/attempt-all [_  (u/unsafe! (k/insert db/runs (k/values {:id       run-id
                                                                     :number   (next-build-number-of name)
                                                                     :pipeline name
                                                                     :status   "running"})))
                          id (f/ok-> (e/pull image)
                                     (e/build (:cmd (first steps)) evars)
                                     (update-pid run-id)
                                     (e/run))
                          id (reduce (partial exec-step run-id evars) id (rest steps))
                          _  (u/unsafe! (k/update db/runs
                                                  (k/set-fields {:status "passed"})
                                                  (k/where {:id run-id})))]
            id
            (f/when-failed [err] (do (u/unsafe! (k/update db/runs
                                                          (k/set-fields {:status "failed"})
                                                          (k/where {:id run-id})))
                                     (f/message err)))))))

(defn stop-pipeline
  "Stops a pipeline if running.
  Performs the following:
  - Sets the field *stopped* in the runs table to true such that exec-step won't reduce any further.
  - Gets the *last_pid* field from the runs table.
  - Checks if that container with that id is running; if yes, kills it.
  Returns Ok or any errors if any."
  [name number]
  (let [criteria {:pipeline name
                  :number   number}
        status   (u/unsafe! (-> (k/select db/runs
                                          (k/fields :status)
                                          (k/where criteria))
                                (first)
                                (:status)))]
    (when (= status "running")
      (f/attempt-all [_      (u/unsafe! (k/update db/runs
                                                  (k/set-fields {:stopped true})
                                                  (k/where criteria)))
                      pid    (u/unsafe! (-> (k/select db/runs
                                                      (k/fields :last_pid)
                                                      (k/where criteria))
                                            (first)
                                            (:last_pid)))
                      status (e/status-of pid)
                      _      (when (status :running?)
                               (e/kill-container pid))]
        "Ok"
        (f/when-failed [err] (f/message err))))))

(defn pipeline-logs
  "Aggregates and reads the logs from all of the containers in a pipeline.
  Performs the following:
  - Fetch the run UUID of a pipeline of a group.
  - Fetch all container ids associated with that UUID.
  - Lazily read the streams from each and append and truncate the lines.
  Returns the list of lines or errors if any."
  [name number offset lines]
  (f/attempt-all [run-id     (u/unsafe! (-> (k/select db/runs
                                                      (k/fields :id)
                                                      (k/where {:pipeline name
                                                                :number   number}))
                                            (first)
                                            (:id)))
                  containers (u/unsafe! (->> (k/select db/logs
                                                       (k/fields :pid)
                                                       (k/where {:run run-id})
                                                       (k/order :id))
                                             (map #(:pid %))))]
    (->> containers
         (map #(e/log-stream-of %))
         (filter #(not (nil? %)))
         (flatten)
         (drop (dec offset))
         (take lines))
    (f/when-failed [err] (f/message err))))
