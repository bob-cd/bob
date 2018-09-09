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

(ns bob.pipeline.core
  (:require [ring.util.response :refer [not-found]]
            [korma.db :refer [defdb]]
            [korma.core :refer [defentity table has-many
                                insert values where
                                select fields order
                                delete join]]
            [manifold.deferred :refer [let-flow]]
            [failjure.core :as f]
            [bob.execution.internals :refer [default-image]]
            [bob.pipeline.internals :refer [exec-steps stop-pipeline pipeline-logs]]
            [bob.db.core :refer [pipelines steps runs logs evars]]
            [bob.util :refer [respond unsafe! clob->str sh-tokenize!]]))

(def name-of (memoize #(str %1 ":" %2)))

(defn create
  "Creates a new pipeline.
  Takes the group, name, a list of steps, a list of environment vars
  and an optional starting Docker image.
  The group defines a logical grouping of pipelines like dev or staging
  and the name is the name of the pipeline like build or test.
  Steps is a list of strings of the commands that need to be executed in sequence.
  The steps are assumed to be valid BASH commands.
  Stores the pipeline info in the pipeline table, steps in steps table.
  Returns Ok or the error if any."
  ([group name pipeline-steps vars] (create group name pipeline-steps vars default-image))
  ([group name pipeline-steps vars image]
   (let-flow [pipeline (name-of group name)
              pairs    (map #(hash-map :key (clojure.core/name (first (keys %)))
                                       :value (first (vals %))
                                       :pipeline pipeline)
                            vars)
              result   (f/attempt-all [_ (unsafe! (insert pipelines (values {:name  pipeline
                                                                             :image image})))
                                       _ (when (not= (count pairs) 0)
                                           (unsafe! (insert evars (values pairs))))
                                       _ (unsafe! (doseq [step pipeline-steps]
                                                    (insert steps (values {:cmd      step
                                                                           :pipeline pipeline}))))]
                         "Ok"
                         (f/when-failed [err] (f/message err)))]
     (respond result))))

;; TODO: Unit test this?
(defn start
  "Asynchronously starts a pipeline in a group by name.
  Returns Ok or any starting errors."
  [group name]
  (let-flow [pipeline (name-of group name)
             result   (f/attempt-all [steps (unsafe! (select steps
                                                             (where {:pipeline pipeline})
                                                             (order :id)))
                                      steps (map #(hash-map :cmd (sh-tokenize! (clob->str (:cmd %)))
                                                            :id (:id %))
                                                 steps)
                                      image (unsafe! (-> (select pipelines
                                                                 (fields :image)
                                                                 (where {:name pipeline}))
                                                         (first)
                                                         (:image)))
                                      vars  (unsafe! (->> (select evars
                                                                  (fields :key :value)
                                                                  (where {:pipeline pipeline}))
                                                          (map #(format "%s=%s"
                                                                        (:key %)
                                                                        (:value %)))))]
                        (do (exec-steps image steps pipeline vars)
                            "Ok")
                        (f/when-failed [err] (f/message err)))]
    (respond result)))

;; TODO: Unit test this?
(defn stop
  "Stops a running pipeline with SIGKILL.
  Returns Ok or any stopping errors."
  [group name number]
  (let-flow [pipeline (name-of group name)
             result   (stop-pipeline pipeline number)]
    (if (nil? result)
      (not-found {:message "Pipeline not running"})
      (respond result))))

;; TODO: Unit test this?
(defn status
  "Fetches the status of a particular run of a pipeline.
  Returns the status or 404."
  [group name number]
  (let-flow [pipeline (name-of group name)
             status   (unsafe! (-> (select runs
                                           (fields :status)
                                           (where {:pipeline pipeline
                                                   :number   number}))
                                   (first)
                                   (:status)))]
    (if (nil? status)
      (not-found {:message "No such pipeline"})
      (respond status))))

;; TODO: Unit test this?
(defn remove
  "Removes a pipeline.
  Returns Ok or 404."
  [group name]
  (let-flow [pipeline (name-of group name)
             _        (unsafe! (delete pipelines
                                       (where {:name pipeline})))]
    (respond "Ok")))

;; TODO: Unit test this?
(defn logs-of
  "Handler to fetch logs for a particular run of a pipeline.
  Take the starting offset to read and the number of lines to read after it.
  Returns logs as a list."
  [group name number offset lines]
  (let-flow [pipeline (name-of group name)
             result   (pipeline-logs pipeline number offset lines)]
    (respond result)))

;; TODO: Unit test this?
(defn running-pipelines
  "Collects all pipeline names that have status 'running'.
  Returns pipeline names as a list."
  []
  (let-flow [pipeline-names (unsafe! (->> (select pipelines
                                                 (fields :name)
                                                 (where {:runs.status "running"})
                                                 (join runs (= :runs.pipeline :name)))
                                          (map #(:name %))))]
    (if (empty? pipeline-names)
      (not-found {:message "No running pipelines"})
      (respond pipeline-names))))
