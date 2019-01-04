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
  (:require [ring.util.response :as res]
            [korma.core :as k]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [bob.pipeline.internals :as p]
            [bob.db.core :as db]
            [bob.util :as u]
            [bob.resource.core :as r]
            [bob.resource.internals :as ri]))

(defn name-of
  [group name]
  (format "%s:%s" group name))

(defn create
  "Creates a new pipeline.

  Takes the following:
  - group
  - name
  - a list of steps
  - a map of environment vars
  - a map of artifacts
  - a list of resources
  - a starting Docker image.

  The group defines a logical grouping of pipelines like dev or staging
  and the name is the name of the pipeline like build or test.
  Steps is a list of strings of the commands that need to be executed in sequence.
  The steps are assumed to be valid shell commands.

  Returns Ok or the error if any."
  [group name pipeline-steps vars pipeline-artifacts resources image]
  (d/let-flow [pipeline       (name-of group name)
               vars-pairs     (map #(hash-map :key (clojure.core/name (first %))
                                              :value (last %)
                                              :pipeline pipeline)
                                   vars)
               artifact-pairs (map #(hash-map :name (clojure.core/name (first %))
                                              :path (last %)
                                              :pipeline pipeline)
                                   pipeline-artifacts)
               result         (f/attempt-all [_ (u/unsafe! (k/insert db/pipelines (k/values {:name  pipeline
                                                                                             :image image})))
                                              _ (u/unsafe! (doseq [resource resources]
                                                             (let [{name   :name
                                                                    params :params
                                                                    type   :type} resource]
                                                               (ri/add-params name params pipeline)
                                                               (k/insert db/resources
                                                                         (k/values {:name     name
                                                                                    :type     (clojure.core/name type)
                                                                                    :pipeline pipeline})))))
                                              _ (when (not (empty? vars-pairs))
                                                  (u/unsafe! (k/insert db/evars (k/values vars-pairs))))
                                              _ (when (not (empty? artifact-pairs))
                                                  (u/unsafe! (k/insert db/artifacts (k/values artifact-pairs))))
                                              _ (u/unsafe! (doseq [step pipeline-steps]
                                                             (k/insert db/steps (k/values {:cmd      step
                                                                                           :pipeline pipeline}))))]
                                (u/respond "Ok")
                                (f/when-failed [err]
                                  (u/unsafe! (k/delete db/pipelines
                                                       (k/where {:name name})))
                                  (res/bad-request {:message (f/message err)})))]
    result))

;; TODO: Unit test this?
(defn start
  "Asynchronously starts a pipeline in a group by name.
  Returns Ok or any starting errors."
  [group name]
  (d/let-flow [pipeline (name-of group name)
               result   (f/attempt-all [image (r/mount-resources pipeline)
                                        steps (u/unsafe! (k/select db/steps
                                                                   (k/where {:pipeline pipeline})
                                                                   (k/order :id)))
                                        steps (map #(hash-map :cmd (u/clob->str (:cmd %))
                                                              :id (:id %))
                                                   steps)
                                        vars  (u/unsafe! (->> (k/select db/evars
                                                                        (k/fields :key :value)
                                                                        (k/where {:pipeline pipeline}))
                                                              (map #(hash-map
                                                                      (keyword (:key %)) (:value %)))
                                                              (into {})))]
                          (do (p/exec-steps image steps pipeline vars)
                              (u/respond "Ok"))
                          (f/when-failed [err]
                            (res/bad-request
                              {:message (f/message err)})))]
    result))

;; TODO: Unit test this?
(defn stop
  "Stops a running pipeline with SIGKILL.
  Returns Ok or any stopping errors."
  [group name number]
  (d/let-flow [pipeline (name-of group name)
               result   (p/stop-pipeline pipeline number)]
    (if (nil? result)
      (res/not-found {:message "Pipeline not running"})
      (u/respond result))))

;; TODO: Unit test this?
(defn status
  "Fetches the status of a particular run of a pipeline.
  Returns the status or 404."
  [group name number]
  (d/let-flow [pipeline (name-of group name)
               status   (u/unsafe! (-> (k/select db/runs
                                                 (k/fields :status)
                                                 (k/where {:pipeline pipeline
                                                           :number   number}))
                                       (first)
                                       (:status)))]
    (if (nil? status)
      (res/not-found {:message "No such pipeline"})
      (u/respond status))))

;; TODO: Unit test this?
(defn remove
  "Removes a pipeline.
  Returns Ok or 404."
  [group name]
  (d/let-flow [pipeline (name-of group name)
               _        (u/unsafe! (k/delete db/pipelines
                                             (k/where {:name pipeline})))]
    (u/respond "Ok")))

;; TODO: Unit test this?
(defn logs-of
  "Handler to fetch logs for a particular run of a pipeline.
  Take the starting offset to read and the number of lines to read after it.
  Returns logs as a list."
  [group name number offset lines]
  (d/let-flow [pipeline (name-of group name)
               result   (p/pipeline-logs pipeline number offset lines)]
    (u/respond result)))

;; TODO: Unit test this?
(defn running-pipelines
  "Collects all pipeline names that have status 'running'.
  Returns pipeline names as a list."
  []
  (d/let-flow [pipeline-names (u/unsafe! (->> (k/select db/pipelines
                                                        (k/fields :name)
                                                        (k/where {:runs.status "running"})
                                                        (k/join db/runs (= :runs.pipeline :name)))
                                              (map #(:name %))))]
    (if (empty? pipeline-names)
      (res/not-found {:message "No running pipelines"})
      (u/respond (->> pipeline-names
                      (map #(clojure.string/split % #":"))
                      (map #(hash-map :group (first %)
                                      :name (second %))))))))
