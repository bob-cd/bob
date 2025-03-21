; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.pipeline
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.tools.logging :as log]
   [common.capacity :as cp]
   [common.events :as ev]
   [common.schemas]
   [failjure.core :as f]
   [langohr.basic :as lb]
   [runner.artifact :as a]
   [runner.engine :as eng]
   [runner.resource :as r]
   [xtdb.api :as xt])
  (:import
   [java.time Instant]))

(defonce node-state
  (atom {:images-for-gc {}
         :runs {}}))

(defn log->db
  [database run-id line]
  (xt/submit-tx database
                [[::xt/put
                  {:xt/id (keyword (str "bob.pipeline.log/l-" (random-uuid)))
                   :type :log-line
                   :time (Instant/now)
                   :run-id run-id
                   :line line}]]))

(defn log-event
  [database run-id line]
  (log->db database run-id (str "[bob] " line)))

(defn mark-image-for-gc
  [image run-id]
  (let [images (get-in @node-state [:images-for-gc run-id])
        new-images (if (some #{image} images)
                     images
                     (cons image images))] ; Should be collected in reverse due to dependency
    (swap! node-state
           update
           :images-for-gc
           assoc
           run-id
           (or new-images (list image)))))

(defn gc-images
  "garbage-collect images by run-id."
  [run-id]
  (log/debugf "Deleting all images for run %s" run-id)
  (run! eng/delete-image (get-in @node-state [:images-for-gc run-id]))
  (swap! node-state update :images-for-gc dissoc run-id))

(defn resourceful-step
  "Create a resource mounted image for a step if it needs it."
  [{:keys [database stream]}
   {:keys [group name image run-id]}
   step]
  (if-let [resource (:needs_resource step)]
    (f/try-all [_ (ev/emit (:producer stream)
                           {:type "Normal"
                            :reason "ResourceFetch"
                            :kind "Pipeline"
                            :message (str "Fetching resource for " run-id)})
                _ (log-event database
                             run-id
                             (str "Fetching and mounting resource " resource))
                pipeline (xt/entity (xt/db database)
                                    (keyword (str "bob.pipeline." group) name))
                _ (when-not (spec/valid? :bob.db/pipeline pipeline)
                    (f/fail "Invalid pipeline: " pipeline))
                resource-info (->> pipeline
                                   :resources
                                   (filter #(= resource (:name %)))
                                   first)]
      (r/mounted-image-from database
                            resource-info
                            image)
      (f/when-failed [err] err))
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
  {:image current-image-id
   :mounted set-of-mounted-resources
   :run-id run-id
   :env env-vars
   :group pipeline-group
   :name pipeline-name}
  or a Failure shorting the reduce.

  Returns the final build state or a Failure."
  [{:keys [database stream] :as config}
   {:keys [image run-id env group name] :as build-state}
   step]
  (if (f/failed? build-state)
    (reduced build-state)
    (f/try-all [image (if (mount-needed? build-state step)
                        (resourceful-step config build-state step)
                        image)
                _ (mark-image-for-gc image run-id)
                id (eng/create-container image step env)
                ;; Globally note the current container id, useful for stopping/pausing
                _ (swap! node-state
                         update-in
                         [:runs run-id]
                         assoc
                         :container-id
                         id)
                _ (let [result (eng/start-container id #(log->db database run-id %))]
                    (when (f/failed? result)
                      (log/debugf "Removing failed container %s" id)
                      (clean-up-container id run-id))
                    result)
                _ (when-let [artifact (:produces_artifact step)]
                    (log-event database
                               run-id
                               (str "Uploading artifact " artifact))
                    (ev/emit (:producer stream)
                             {:type "Normal"
                              :reason "ArtifactUpload"
                              :kind "Pipeline"
                              :message (str "Uploading artifact for " run-id)})
                    (a/upload-artifact database
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
             {:image image
              :mounted (if-let [resource (:needs_resource step)]
                         (conj (:mounted build-state) resource)
                         (:mounted build-state))})
      (f/when-failed [err]
        (log/errorf "Failed executing step %s with error %s"
                    step
                    (f/message err))
        err))))

(defn clean-up-run
  [run-id]
  (swap! node-state
         update
         :runs
         dissoc
         run-id))

(defn- get-pipeline
  [database group name]
  (f/try-all [pipeline (xt/entity (xt/db database)
                                  (keyword (str "bob.pipeline." group) name))
              _ (when-not pipeline
                  (f/fail (format "Unable to find pipeline %s/%s" group name)))
              _ (when-not (spec/valid? :bob.db/pipeline pipeline)
                  (f/fail (str "Invalid pipeline in DB: " pipeline)))]
    pipeline
    (f/when-failed [err] err)))

(defn- set-run-status
  [db run-id status time-key]
  (f/try-all [run (-> (xt/db db)
                      (xt/entity (keyword "bob.pipeline.run" run-id))
                      (assoc :status status time-key (Instant/now)))
              _ (->> [[::xt/put run]]
                     (xt/submit-tx db)
                     (xt/await-tx db))]
    :ok
    (f/when-failed [err] err)))

(defn- start*
  [{:keys [database stream] :as config} queue-chan group name run-id delivery-tag]
  (f/try-all [{:keys [image steps vars]} (get-pipeline database group name)
              _ (log/infof "Starting new run: %s" run-id)
              producer (:producer stream)
              _ (ev/emit producer {:type "Normal"
                                   :reason "StartPipelineRun"
                                   :kind "Pipeline"
                                   :message (format "Starting pipeline %s/%s with id %s" group name run-id)})
              _ (set-run-status database run-id :initializing :initiated-at)
              _ (ev/emit producer {:type "Normal"
                                   :reason "ImagePull"
                                   :kind "Pipeline"
                                   :message (format "Pulling image %s for %s" image run-id)})
              _ (eng/pull-image image)
              _ (set-run-status database run-id :initialized :initialized-at)
              _ (mark-image-for-gc image run-id)
              build-state {:image image
                           :mounted #{}
                           :run-id run-id
                           :queue-chan queue-chan
                           :env vars
                           :group group
                           :name name}
              _ (set-run-status database run-id :running :started-at)
              _ (ev/emit producer {:type "Normal"
                                   :reason "PipelineRunning"
                                   :kind "Pipeline"
                                   :message (str "Pipeline now running " run-id)})
              _ (reduce #(exec-step config %1 %2) build-state steps) ;; This is WHOLE of Bob!
              _ (gc-images run-id)
              _ (clean-up-run run-id)
              _ (set-run-status database run-id :passed :completed-at)
              _ (log/infof "Run successful %s" run-id)
              _ (log-event database run-id "Run successful")
              _ (ev/emit producer {:type "Normal"
                                   :reason "PipelineSuccessful"
                                   :kind "Pipeline"
                                   :message (str "Pipeline successful " run-id)})
              _ (when delivery-tag
                  (lb/ack queue-chan delivery-tag))]
    run-id
    (f/when-failed [err]
      (let [{:keys [status] :as run} (xt/entity (xt/db database) (keyword "bob.pipeline.run" run-id))
            error (f/message err)]
        (when (and (spec/valid? :bob.db/run run)
                   (not= status :stopped))
          (log/infof "Marking run %s as failed with reason: %s"
                     run-id
                     error)
          (log-event database run-id (str "Run failed: " error))
          (ev/emit (:producer stream)
                   {:type "Warning"
                    :reason "PipelineFailed"
                    :kind "Pipeline"
                    :message (format "Pipeline %s failed: %s" run-id error)})
          (set-run-status database run-id :failed :completed-at)))
      (gc-images run-id)
      (clean-up-run run-id)
      (when delivery-tag
        (lb/ack queue-chan delivery-tag))
      (f/fail run-id))))

(defn start
  "Attempts to asynchronously start a pipeline by group and name.

  Rejects it if there isn't any capacity on the current runner."
  [{:keys [database stream] :as config} queue-chan {:keys [group name run-id] :as data} {:keys [delivery-tag]}]
  (if-not (spec/valid? :bob.command.pipeline-start/data data)
    (do (log/error "Invalid pipeline start command: " data)
        (lb/ack queue-chan delivery-tag))
    (if (cp/has-capacity? (get-pipeline database group name))
      (let [run-ref (future (start* config queue-chan group name run-id delivery-tag))]
        (swap! node-state
               update-in
               [:runs run-id]
               assoc
               :ref
               run-ref)
        run-ref)
      (let [msg (format "Rejected run %s due to insufficient capacity" run-id)]
        (lb/nack queue-chan delivery-tag false false)
        (log/warn msg)
        (ev/emit (:producer stream)
                 {:kind "Pipeline"
                  :type "Warning"
                  :reason "PipelineRunRejected"
                  :message msg})))))

(defn stop
  "Idempotently stops a pipeline by the run-id

  Sets the :status in Db to :stopped if pending or kills the container if present.
  This triggers a pipeline failure if running which is specially dealt with."
  [{:keys [database stream]} queue-chan {:keys [run-id] :as data} {:keys [delivery-tag]}]
  (when delivery-tag
    (lb/ack queue-chan delivery-tag))
  (if-not (spec/valid? :bob.command.pipeline-stop/data data)
    (log/error "Invalid pipeline stop command: " data)
    (do
      (log/info "Stopping run" run-id)
      (ev/emit (:producer stream)
               {:type "Normal"
                :reason "PipelineStopping"
                :kind "Pipeline"
                :message (str "Stopping run " run-id)})
      (set-run-status database run-id :stopped :completed-at)
      (when-let [run (get-in @node-state [:runs run-id])]
        (when-let [container (:container-id run)]
          (eng/kill-container container)
          (eng/delete-container container)
          (gc-images run-id))
        (when-let [run (get-in @node-state [:runs run-id :ref])]
          (when-not (future-done? run)
            (future-cancel run)))))))

(comment
  (set! *warn-on-reflection* true)

  (reset! node-state
          {:images-for-gc {:run-id-1 (list "rabbitmq:3-alpine" "postgres:alpine")}
           :runs {}})

  (mark-image-for-gc "apline:latest" "r-1")

  (gc-images :run-id-1)

  (reset! node-state
          {:images-for-gc {}
           :runs {}})

  @node-state

  (require '[runner.system :as sys])
  (def database (:bob/storage sys/system))
  (def queue-chan (get-in sys/system [:bob/queue :chan]))
  (def producer (get-in sys/system [:bob/stream :producer]))
  (def run-id "r-60a0d2e8-ec6e-4004-8136-978f4e042f25")

  (xt/submit-tx database
                [[::xt/put
                  {:xt/id :bob.pipeline.test/test
                   :group "test"
                   :name "test"
                   :steps [{:cmd "echo hello"}
                           {:needs_resource "source"
                            :cmd "ls"}]
                   :vars {:k1 "v1"
                          :k2 "v2"}
                   :resources [{:name "source"
                                :type "external"
                                :provider "git"
                                :params {:repo "https://github.com/bob-cd/bob"
                                         :branch "main"}}]
                   :type :pipeline
                   :image "busybox:musl"}]])

  (xt/entity (xt/db database) :bob.pipeline.test/test)

  (xt/submit-tx database
                [[::xt/put
                  {:xt/id :bob.resource-provider/git
                   :url "http://localhost:8000"
                   :type :resource-provider
                   :name "git"}]])

  (xt/entity (xt/db database) :bob.resource-provider/git)

  (resourceful-step {:database database
                     :producer producer}
                    {:group "test"
                     :name "test"
                     :run-id "a-run-id"}
                    {:needs_resource "source"
                     :image "busybox:musl"
                     :cmd "ls"})

  (xt/submit-tx database
                [[::xt/put
                  {:xt/id :bob.artifact-store/local
                   :url "http://localhost:8001"}]])

  (xt/entity (xt/db database) :bob.artifact-store/local)

  (exec-step {:database database
              :producer producer}
             {:image "docker.io/busybox:musl"
              :mounted #{}
              :run-id "a-run-id"
              :env {:type "yes"}
              :group "test"
              :name "test"
              :type :pipeline}
             {:needs_resource "source"
              :cmd "ls"
              :produces_artifact {:path "README.md"
                                  :name "readme"
                                  :store "local"}})

  (def my-start (start {:database database
                        :producer producer}
                       queue-chan
                       {:group "test"
                        :name "test"
                        :run-id run-id}
                       {:delivery-tag 1}))

  (def my-stop (stop {:database database
                      :producer producer}
                     queue-chan
                     {:group "test"
                      :name "test"
                      :run-id run-id}
                     {}))

  (xt/submit-tx database
                [[::xt/put
                  {:xt/id :bob.pipeline.test/stop-test
                   :steps [{:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"}]
                   :image "busybox:musl"}]])

  (xt/entity (xt/db database) :bob.pipeline.test/stop-test)

  (start database
         queue-chan
         {:group "test"
          :name "stop-test"}
         {:delivery-tag 1})

  (stop database
        nil
        {:group "test"
         :name "stop-test"
         :run-id "r-ff185a8a-b6a6-48df-8630-650b025cafad"}
        {}))
