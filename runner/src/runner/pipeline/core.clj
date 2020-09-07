;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns runner.pipeline.core
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [crux.api :as crux]
            [runner.errors :as errors]
            [runner.docker :as docker]
            [runner.resource.core :as r]
            [runner.artifact.core :as a])
  (:import [java.util UUID]))

(defonce ^:private node-state
         (atom {:images-for-gc      {}
                :current-containers {}}))

(defn log->db
  [db-client run-id line]
  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id (keyword (str "bob.pipeline.log/l-" (UUID/randomUUID)))
                     :type       :log-line
                     :run-id     run-id
                     :line       line}]]))

(defn log-event
  [db-client run-id line]
  (log->db db-client run-id (str "[bob] " line)))

(defn mark-image-for-gc
  [image run-id]
  (let [images     (get-in @node-state [:images-for-gc run-id])
        new-images (if (some #{image} images)
                     images
                     (cons image images))] ; Should be collected in reverse due to dependency
    (swap! node-state
      update
      :images-for-gc
      assoc
      run-id
      (if (nil? new-images)
        (list image)
        new-images))))

(defn gc-images
  "garbage-collect images by run-id."
  [run-id]
  (log/debugf "Deleting all images for run %s" run-id)
  (run! docker/delete-image (get-in @node-state [:images-for-gc run-id]))
  (swap! node-state update :images-for-gc dissoc run-id))

(defn resourceful-step
  "Create a resource mounted image for a step if it needs it."
  [db-client step group name image run-id]
  (if-let [resource (:needs_resource step)]
    (f/try-all [_             (log-event db-client
                                         run-id
                                         (str "Fetching and mounting resource " resource))
                pipeline      (crux/entity (crux/db db-client)
                                           (keyword (format "bob.pipeline.%s/%s"
                                                            group
                                                            name)))
                resource-info (->> pipeline
                                   :resources
                                   (filter #(= resource (:name %)))
                                   first)]
      (r/mounted-image-from db-client
                            resource-info
                            image)
      (f/when-failed [err]
        err))
    image))

(defn mount-needed?
  "Checks if the step needs a resource and if it needs to be mounted"
  [{:keys [mounted]} {:keys [:needs_resource]}]
  (and needs_resource (not (contains? mounted needs_resource))))

(defn exec-step
  "Reducer function to excute a step from the previous build state.

  Build state:
  {:image     current-image-id
   :mounted   set-of-mounted-resources
   :run-id    run-id
   :db-client db-client
   :env       env-vars
   :group     pipeline-group
   :name      pipeline-name}
  or a Failure shorting the reduce.

  Returns the final build state or a Failure."
  [{:keys [db-client image run-id env group name]
    :as   build-state} step]
  (if (f/failed? build-state)
    (reduced build-state)
    (f/try-all [image  (if (mount-needed? build-state step)
                         (resourceful-step db-client
                                           step
                                           group
                                           name
                                           image
                                           run-id)
                         image)
                _      (mark-image-for-gc image run-id)
                id     (docker/create-container image step env)
                ;; Globally note the current container id, useful for stopping/pausing
                _      (swap! node-state
                         update
                         :current-containers
                         assoc
                         run-id
                         id)
                result (docker/start-container id
                                               #(log->db db-client run-id %))
                _      (if (f/failed? result)
                         (do
                           (log/debugf "Removing failed container %s" id)
                           (docker/delete-container id)
                           (swap! node-state
                             update
                             :current-containers
                             dissoc
                             run-id))
                         result)
                _      (when-let [artifact (:produces_artifact step)]
                         (log-event db-client
                                    run-id
                                    (str "Uploading artifact " artifact))
                         (a/upload-artifact db-client
                                            group
                                            name
                                            run-id
                                            (:name artifact)
                                            id
                                            (str (get-in
                                                   (docker/inspect-container
                                                     id)
                                                   [:Config :WorkingDir])
                                                 "/"
                                                 (:artifact_path artifact))
                                            (:artifact_store artifact)))
                image  (docker/commit-image id "")
                _      (mark-image-for-gc image run-id)
                _      (log/debugf "Removing successful container %s" id)
                _      (docker/delete-container id)
                _      (swap! node-state
                         update
                         :current-containers
                         dissoc
                         run-id)]
      (merge build-state
             {:image   image
              :mounted (if-let [resource (:needs_resource step)]
                         (conj (:mounted build-state) resource)
                         (:mounted build-state))})
      (f/when-failed [err]
        (log/errorf "Failed executing step %s with error %s"
                    step
                    (f/message err))
        err))))

(defn start
  "Attempts to asynchronously start a pipeline by group and name."
  [db-client queue-chan {:keys [group name]}]
  (let [run-id    (str "r-" (UUID/randomUUID))
        run-db-id (keyword (format "bob.pipeline.%s.%s.run/%s"
                                   group
                                   name
                                   run-id))
        run-info  {:crux.db/id run-db-id
                   :type       :pipeline-run
                   :group      group
                   :name       name}]
    (future
      (f/try-all [pipeline                   (crux/entity (crux/db db-client)
                                                          (keyword (format "bob.pipeline.%s/%s"
                                                                           group
                                                                           name)))
                  _                          (when-not pipeline
                                               (f/fail (format "Unable to find pipeline %s/%s"
                                                               group
                                                               name)))
                  {:keys [image steps vars]} pipeline
                  _                          (log/infof "Starting new run: %s" run-id)
                  txn                        (crux/submit-tx db-client
                                                             [[:crux.tx/put (assoc run-info :status :running)]])
                  _                          (crux/await-tx db-client txn)
                  _                          (log-event db-client run-id (str "Pulling image " image))
                  _                          (docker/pull-image image)
                  _                          (mark-image-for-gc image run-id)
                  build-state                {:image     image
                                              :mounted   #{}
                                              :run-id    run-id
                                              :db-client db-client
                                              :env       vars
                                              :group     group
                                              :name      name}
                  _                          (reduce exec-step build-state steps) ;; This is WHOLE of Bob!
                  _                          (gc-images run-id)
                  txn                        (crux/submit-tx db-client
                                                             [[:crux.tx/put (assoc run-info :status :passed)]])
                  _                          (crux/await-tx db-client txn)
                  _                          (log/infof "Run successful %s" run-id)
                  _                          (log-event db-client run-id "Run successful")]
        "Success"
        (f/when-failed [err]
          (let [status (:status (crux/entity (crux/db db-client) run-db-id))
                error  (f/message err)]
            (when-not (= status :stopped)
              (log/infof "Marking run %s as failed with reason: %s"
                         run-id
                         error)
              (log-event db-client run-id (str "Run failed: %s" error))
              (crux/submit-tx db-client
                              [[:crux.tx/put (assoc run-info :status :failed)]])))
          (gc-images run-id)
          (errors/publish-error queue-chan (str "Pipeline failure: " (f/message err))))))))

(comment
  (reset! node-state
          {:images-for-gc      {:run-id-1 (list "rabbitmq:3-alpine" "postgres:alpine")}
           :current-containers {}})

  (mark-image-for-gc "apline:latest" "r-1")

  (gc-images :run-id-1)

  (reset! node-state
          {:images-for-gc      {}
           :current-containers {}})

  @node-state

  (require '[runner.system :as sys])

  (def db-client
    (-> sys/system
        :database
        sys/db-client))

  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.pipeline.test/test
                     :group      "test"
                     :name       "test"
                     :steps      [{:cmd "echo hello"}
                                  {:needs_resource "source"
                                   :cmd            "ls"}]
                     :vars       {:k1 "v1"
                                  :k2 "v2"}
                     :resources  [{:name     "source"
                                   :type     "external"
                                   :provider "git"
                                   :params   {:repo   "https://github.com/bob-cd/bob"
                                              :branch "master"}}]
                     :image      "busybox:musl"}]])

  (crux/entity (crux/db db-client) :bob.pipeline.test/test)

  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.resource-provider/git
                     :url        "http://localhost:8000"}]])

  (crux/entity (crux/db db-client) :bob.resource-provider/git)

  (resourceful-step db-client
                    {:needs_resource "source"
                     :cmd            "ls"}
                    "test"         "test"
                    "busybox:musl" "a-run-id")

  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.artifact-store/local
                     :url        "http://localhost:8001"}]])

  (crux/entity (crux/db db-client) :bob.artifact-store/local)

  (exec-step {:image     "busybox:musl"
              :mounted   #{}
              :run-id    "a-run-id"
              :db-client db-client
              :env       {:type "yes"}
              :group     "test"
              :name      "test"}
             {:needs_resource    "source"
              :cmd               "ls"
              :produces_artifact {:artifact_path  "README.md"
                                  :name           "readme"
                                  :artifact_store "local"}})

  (start db-client
    nil
    {:group "test"
     :name  "test"})

  (crux/q (crux/db db-client)
          '{:find  [(eql/project log [:line])]
            :where [[log :type :log-line] [log :run-id "r-60a0d2e8-ec6e-4004-8136-978f4e042f25"]]})

  (crux/q (crux/db db-client)
          '{:find  [(eql/project run [*])]
            :where [[run :type :pipeline-run]]}))
