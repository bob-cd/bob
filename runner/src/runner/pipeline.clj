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

(ns runner.pipeline
  (:require [failjure.core :as f]
            [taoensso.timbre :as log]
            [xtdb.api :as xt]
            [common.errors :as errors]
            [runner.engine :as eng]
            [runner.resource :as r]
            [runner.artifact :as a])
  (:import [java.util UUID]
           [java.time Instant]))

(defonce ^:private node-state
         (atom {:images-for-gc {}
                :runs          {}}))

(defn log->db
  [db-client run-id line]
  (xt/submit-tx db-client
                [[:xt.tx/put
                  {:xt.db/id (keyword (str "bob.pipeline.log/l-" (UUID/randomUUID)))
                   :type     :log-line
                   :time     (Instant/now)
                   :run-id   run-id
                   :line     line}]]))

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
  (run! eng/delete-image (get-in @node-state [:images-for-gc run-id]))
  (swap! node-state update :images-for-gc dissoc run-id))

(defn resourceful-step
  "Create a resource mounted image for a step if it needs it."
  [db-client step group name image run-id]
  (if-let [resource (:needs_resource step)]
    (f/try-all [_ (log-event db-client
                             run-id
                             (str "Fetching and mounting resource " resource))
                pipeline      (xt/entity (xt/db db-client
                                                (keyword (format "bob.pipeline.%s/%s"
                                                                 group
                                                                 name))))
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

(defn clean-up-container
  [id run-id]
  (eng/delete-container id)
  (swap! node-state
    update-in
    [:runs run-id]
    dissoc
    :container-id))

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
    (f/try-all [image (if (mount-needed? build-state step)
                        (resourceful-step db-client
                                          step
                                          group
                                          name
                                          image
                                          run-id)
                        image)
                _ (mark-image-for-gc image run-id)
                id    (eng/create-container image step env)
                ;; Globally note the current container id, useful for stopping/pausing
                _ (swap! node-state
                    update-in
                    [:runs run-id]
                    assoc
                    :container-id
                    id)
                _ (let [result (eng/start-container id #(log->db db-client run-id %))]
                    (when (f/failed? result)
                      (log/debugf "Removing failed container %s" id)
                      (clean-up-container id run-id))
                    result)
                _ (when-let [artifact (:produces_artifact step)]
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
                                              (eng/inspect-container id)
                                              [:Config :WorkingDir])
                                            "/"
                                            (:path artifact))
                                       (:store artifact)))
                image (eng/commit-container id)
                _ (mark-image-for-gc image run-id)
                _ (log/debugf "Removing successful container %s" id)
                _ (clean-up-container id run-id)]
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

(defn run-info-of
  [db-client run-id]
  (xt/entity (xt/db db-client) (keyword (str "bob.pipeline.run/" run-id))))

(defn clean-up-run
  [run-id]
  (swap! node-state
    update
    :runs
    dissoc
    run-id))

(defn- start*
  [db-client queue-chan group name run-id run-info run-db-id]
  (f/try-all [pipeline                   (xt/entity (xt/db db-client
                                                           (keyword (format "bob.pipeline.%s/%s"
                                                                            group
                                                                            name))))
              _ (when-not pipeline
                  (f/fail (format "Unable to find pipeline %s/%s"
                                  group
                                  name)))
              {:keys [image steps vars]} pipeline
              _ (log/infof "Starting new run: %s" run-id)
              _ (xt/await-tx db-client
                             (xt/submit-tx db-client
                                           [[:xt.tx/put
                                             (assoc run-info
                                                    :status  :initializing
                                                    :started (Instant/now))]]))
              _ (log-event db-client run-id (str "Pulling image " image))
              _ (eng/pull-image image)
              _ (mark-image-for-gc image run-id)
              build-state                {:image     image
                                          :mounted   #{}
                                          :run-id    run-id
                                          :db-client db-client
                                          :env       vars
                                          :group     group
                                          :name      name}
              _ (xt/await-tx db-client
                             (xt/submit-tx db-client
                                           [[:xt.tx/put
                                             (assoc (run-info-of db-client run-id)
                                                    :status
                                                    :running)]]))
              _ (reduce exec-step build-state steps) ;; This is WHOLE of Bob!
              _ (gc-images run-id)
              _ (clean-up-run run-id)
              _ (xt/await-tx db-client
                             (xt/submit-tx db-client
                                           [[:xt.tx/put
                                             (assoc (run-info-of db-client run-id)
                                                    :status    :passed
                                                    :completed (Instant/now))]]))
              _ (log/infof "Run successful %s" run-id)
              _ (log-event db-client run-id "Run successful")]
    run-id
    (f/when-failed [err]
      (let [status (:status (xt/entity (xt/db db-client) run-db-id))
            error  (f/message err)]
        (when-not (= status :stopped)
          (log/infof "Marking run %s as failed with reason: %s"
                     run-id
                     error)
          (log-event db-client run-id (str "Run failed: %s" error))
          (xt/await-tx
            db-client
            (xt/submit-tx
              db-client
              [[:xt.tx/put
                (assoc (run-info-of db-client run-id) :status :failed :completed (Instant/now))]]))))
      (gc-images run-id)
      (clean-up-run run-id)
      (errors/publish-error queue-chan (str "Pipeline failure: " (f/message err)))
      (f/fail run-id))))

(defn start
  "Attempts to asynchronously start a pipeline by group and name."
  [db-client queue-chan {:keys [group name run_id]}]
  (let [run-db-id (keyword (str "bob.pipeline.run/" run_id))
        run-info  {:xt.db/id run-db-id
                   :type     :pipeline-run
                   :group    group
                   :name     name}
        run-ref   (future (start* db-client queue-chan group name run_id run-info run-db-id))]
    (swap! node-state
      update-in
      [:runs run_id]
      assoc
      :ref
      run-ref)
    run-ref))

(defn stop
  "Idempotently stops a pipeline by group, name and run_id

  Sets the :status in Db to :stopped and kills the container if present.
  This triggers a pipeline failure which is specially dealt with."
  [db-client _queue-chan {:keys [group name run_id]}]
  (when-let [run (get-in @node-state [:runs run_id])]
    (log/infof "Stopping run %s for pipeline %s %s"
               run_id
               group
               name)
    (xt/await-tx db-client
                 (xt/submit-tx
                   db-client
                   [[:xt.tx/put
                     (assoc (run-info-of db-client run_id) :status :stopped :completed (Instant/now))]]))
    (when-let [container (:container-id run)]
      (eng/kill-container container)
      (eng/delete-container container)
      (gc-images run_id))
    (when-let [run (get-in @node-state [:runs run_id :ref])]
      (when-not (future-done? run)
        (future-cancel run)))))

(comment
  (reset! node-state
    {:images-for-gc {:run-id-1 (list "rabbitmq:3-alpine" "postgres:alpine")}
     :runs          {}})

  (mark-image-for-gc "apline:latest" "r-1")

  (gc-images :run-id-1)

  (reset! node-state
    {:images-for-gc {}
     :runs          {}})

  @node-state

  (require '[common.system :as sys])

  (def db-client (sys/db))

  (xt/submit-tx db-client
                [[:xt.tx/put
                  {:xt.db/id  :bob.pipeline.test/test
                   :group     "test"
                   :name      "test"
                   :steps     [{:cmd "echo hello"}
                               {:needs_resource "source"
                                :cmd            "ls"}]
                   :vars      {:k1 "v1"
                               :k2 "v2"}
                   :resources [{:name     "source"
                                :type     "external"
                                :provider "git"
                                :params   {:repo   "https://github.com/bob-cd/bob"
                                           :branch "main"}}]
                   :image     "busybox:musl"}]])

  (xt/entity (xt/db db-client) :bob.pipeline.test/test)

  (xt/submit-tx db-client
                [[:xt.tx/put
                  {:xt.db/id :bob.resource-provider/git
                   :url      "http://localhost:8000"}]])

  (xt/entity (xt/db db-client) :bob.resource-provider/git)

  (resourceful-step db-client
                    {:needs_resource "source"
                     :cmd            "ls"}
                    "test"         "test"
                    "busybox:musl" "a-run-id")

  (xt/submit-tx db-client
                [[:xt.tx/put
                  {:xt.db/id :bob.artifact-store/local
                   :url      "http://localhost:8001"}]])

  (xt/entity (xt/db db-client) :bob.artifact-store/local)

  (exec-step {:image     "busybox:musl"
              :mounted   #{}
              :run-id    "a-run-id"
              :db-client db-client
              :env       {:type "yes"}
              :group     "test"
              :name      "test"}
             {:needs_resource    "source"
              :cmd               "ls"
              :produces_artifact {:path  "README.md"
                                  :name  "readme"
                                  :store "local"}})

  (start db-client
    nil
    {:group "test"
     :name  "test"})

  (xt/q (xt/db db-client
               '{:find  [(pull log [:line])]
                 :where [[log :type :log-line] [log :run-id "r-60a0d2e8-ec6e-4004-8136-978f4e042f25"]]}))

  (->> (xt/q (xt/db db-client
                    '{:find  [(pull run [:xt.db/id])]
                      :where [[run :type :pipeline-run]]}))
       first
       (map :xt.db/id)
       (map name))

  (xt/submit-tx db-client
                [[:xt.tx/put
                  {:xt.db/id :bob.pipeline.test/stop-test
                   :steps    [{:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"}]
                   :image    "busybox:musl"}]])

  (xt/entity (xt/db db-client) :bob.pipeline.test/stop-test)

  (start db-client
    nil
    {:group "test"
     :name  "stop-test"})

  (stop db-client
    nil
    {:group  "test"
     :name   "stop-test"
     :run_id "r-ff185a8a-b6a6-48df-8630-650b025cafad"}))
