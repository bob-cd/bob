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
  (:require [ring.util.http-response :as res]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [bob.pipeline.internals :as p]
            [bob.util :as u]
            [bob.resource.internals :as ri]
            [bob.states :as states]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]))

(defn create
  "Creates a new pipeline.

  Takes the following:
  - group
  - name
  - a list of steps
  - a map of environment vars
  - a list of resources
  - a starting Docker image.

  The group defines a logical grouping of pipelines like dev or staging
  and the name is the name of the pipeline like build or test.
  Steps is a list of strings of the commands that need to be executed in sequence.
  The steps are assumed to be valid shell commands.

  Returns Ok or the error if any."
  [group name pipeline-steps vars resources image]
  (d/let-flow [pipeline (u/name-of group name)
               evars    (map #(vector (clojure.core/name (first %))
                                      (last %)
                                      pipeline)
                             vars)
               result   (f/try-all [_ (log/debugf "Inserting pipeline %s" name)
                                    _ (db/insert-pipeline states/db
                                                          {:name  pipeline
                                                           :image image})
                                    _ (doseq [resource resources]
                                        (let [{:keys [name params type provider]} resource]
                                          (log/debugf "Inserting resource:
                                                      name: %s
                                                      type: %s
                                                      provider: %s"
                                                      name
                                                      type
                                                      provider)
                                          (rdb/insert-resource states/db
                                                               {:name     name
                                                                :type     type
                                                                :pipeline pipeline
                                                                :provider provider})
                                          (ri/add-params name params pipeline)))
                                    _ (when (seq evars)
                                        (log/debugf "Inserting environment vars: %s" (into [] evars))
                                        (f/try* (db/insert-evars states/db {:evars evars})))
                                    _ (doseq [step pipeline-steps]
                                        (let [{cmd                     :cmd
                                               needs-resource          :needs_resource
                                               {artifact-name  :name
                                                artifact-path  :path
                                                artifact-store :store} :produces_artifact} step]
                                          (log/debugf "Inserting step:
                                                      cmd: %s
                                                      needs resource: %s
                                                      produces artifact: %s"
                                                      cmd
                                                      needs-resource
                                                      (:produces_artifact step))
                                          (db/insert-step states/db
                                                          {:cmd               cmd
                                                           :needs_resource    needs-resource
                                                           :produces_artifact artifact-name
                                                           :artifact_path     artifact-path
                                                           :artifact_store    artifact-store
                                                           :pipeline          pipeline})))]
                                   (u/respond "Ok")
                                   (f/when-failed [err]
                                                  (log/errorf "Pipeline creation failed: %s." (f/message err))
                                                  ;; TODO: See if this can be done in a txn instead
                                                  (when-not (clojure.string/includes? (f/message err) "duplicate key")
                                                    (f/try* (db/delete-pipeline states/db {:name pipeline})))
                                                  (res/bad-request {:message "Pipeline creation error: Check params or if its already created"})))]
              result))

(defn start
  "Asynchronously starts a pipeline in a group by name.
  Returns Ok or any starting errors."
  [group name]
  (d/let-flow [pipeline (u/name-of group name)
               result   (f/try-all [image (p/image-of pipeline)
                                    steps (db/ordered-steps states/db
                                                            {:pipeline pipeline})
                                    vars  (->> (db/evars-by-pipeline states/db
                                                                     {:pipeline pipeline})
                                               (map #(hash-map
                                                      (keyword (:key %)) (:value %)))
                                               (into {}))]
                                   (do (log/infof "Starting pipeline %s" pipeline)
                                       (p/exec-steps image steps pipeline vars)
                                       (u/respond "Ok"))
                                   (f/when-failed [err]
                                                  (log/errorf "Error starting pipeline: %s" (f/message err))
                                                  (res/bad-request
                                                   {:message (f/message err)})))]
              result))

(defn stop
  "Stops a running pipeline with SIGKILL.
  Returns Ok or any stopping errors."
  [group name number]
  (d/let-flow [pipeline (u/name-of group name)
               result   (p/stop-pipeline pipeline number)]
              (if (nil? result)
                (do (log/warn "Attempt to stop an invalid pipeline")
                    (res/not-found {:message "Pipeline not running"}))
                (u/respond result))))

(defn status
  "Fetches the status of a particular run of a pipeline.
  Returns the status or 404."
  [group name number]
  (d/let-flow [pipeline (u/name-of group name)
               status   (f/try* (-> (db/status-of states/db
                                                  {:pipeline pipeline
                                                   :number   number})
                                    (:status)
                                    (keyword)))]
              (if (nil? status)
                (do (log/warn "Attempt to fetch status for an invalid pipeline")
                    (res/not-found {:message "No such pipeline"}))
                (u/respond status))))

(defn remove-pipeline
  "Removes a pipeline.
  Returns Ok or 404."
  [group name]
  (d/let-flow [pipeline (u/name-of group name)
               _        (log/debugf "Deleting pipeline %s" pipeline)
               _        (f/try* (db/delete-pipeline states/db
                                                    {:name pipeline}))]
              (u/respond "Ok")))

(defn logs-of
  "Handler to fetch logs for a particular run of a pipeline.
  Take the starting offset to read and the number of lines to read after it.
  Returns logs as a list."
  [group name number offset lines]
  (d/let-flow [pipeline (u/name-of group name)
               result   (p/pipeline-logs pipeline number offset lines)]
              (if (f/failed? result)
                (res/bad-request {:message (f/message result)})
                (u/respond result))))

(defn make-step
  "Convertes step from the database to conform with the schema"
  [{:keys [cmd needs_resource produces_artifact artifact_path artifact_store]}]
  (merge {:cmd cmd}
         (when (some? needs_resource) {:needs_resource needs_resource})
         (when (some? produces_artifact)
           {:produces_artifact
            {:name produces_artifact
             :path artifact_path
             :store artifact_store}})))

(defn make-resource
  "Convert and enrich resource from the databse to comform with schema"
  [{:keys [name type provider pipeline]}]
  {:name name
   :type type
   :provider provider
   :params (ri/get-resource-params pipeline name)})

(defn get-pipelines
  "Handler to fetch list of defined piplies"
  [group name status]
  (log/debugf "Fetching list of defined pipelines for group=%s name=%s status=%s"  group name status)
  (d/let-flow [pipeline-query (if (every? not-empty [group name])
                                (u/name-of group name)
                                (some not-empty [group name]))
               query-params {:pipeline pipeline-query :status status}
               result (f/try-all [pipelines (db/get-pipelines states/db query-params)
                                  _ (log/debugf "Found pipelines %s" (vec pipelines))
                                  result (mapv (fn [{:keys [name image]}]
                                                 (f/try-all [filter {:pipeline name}
                                                             steps (mapv make-step (db/ordered-steps states/db filter))
                                                             resources (mapv make-resource
                                                                             (rdb/resources-by-pipeline states/db filter))]
                                                            {:name name
                                                             :data {:image image
                                                                    :steps steps
                                                                    :resources resources}}))
                                               pipelines)]
                                 (do
                                   (log/debugf "Fetched pipelines: %s" result)
                                   (res/ok result))
                                 (f/when-failed [err]
                                                (let [error (format "Failed to fetch pipelines %s " (f/message err))]
                                                  (log/warn error err)
                                                  (u/respond error))))]
              result))

(comment
  (create "test"
          "test"
          ["echo hello"
           {:needs_resource "source"
            :cmd            "ls"}]
          {}
          [{:name     "source"
            :type     "external"
            :provider "git"
            :params   {:repo   "https://github.com/bob-cd/bob",
                       :branch "master"}}
           {:name     "source2"
            :type     "external"
            :provider "git"
            :params   {:repo   "https://github.com/lispyclouds/clj-docker-client",
                       :branch "master"}}]
          "busybox:musl")
  (start "test" "test")
  (status "dev" "test" 1)
  (logs-of "test" "test" 1 0 20)
  (remove-pipeline "test" "test"))
