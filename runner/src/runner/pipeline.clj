; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.pipeline
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.tools.logging :as log]
   [common.errors :as errors]
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
                                    (keyword (format "bob.pipeline.%s/%s" group name)))
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

(defn run-info-of
  [database run-id]
  (f/try-all [run-info (xt/entity (xt/db database) (keyword "bob.pipeline.run" run-id))
              _ (when-not (spec/valid? :bob.db/run run-info)
                  (f/fail (str "Invalid run: " run-info)))]
    run-info
    (f/when-failed [err] err)))

(defn clean-up-run
  [run-id]
  (swap! node-state
         update
         :runs
         dissoc
         run-id))

(defn- start*
  [{:keys [database stream] :as config} queue-chan group name run-id run-info run-db-id delivery-tag]
  (f/try-all [pipeline (xt/entity (xt/db database)
                                  (keyword (format "bob.pipeline.%s/%s" group name)))
              _ (when-not (spec/valid? :bob.db/pipeline pipeline)
                  (f/fail (str "Invalid pipeline in DB: " pipeline)))
              _ (when-not pipeline
                  (f/fail (format "Unable to find pipeline %s/%s"
                                  group
                                  name)))
              {:keys [image steps vars]} pipeline
              _ (log/infof "Starting new run: %s" run-id)
              producer (:producer stream)
              _ (ev/emit producer {:type "Normal"
                                   :reason "StartPipelineRun"
                                   :kind "Pipeline"
                                   :message (format "Starting pipeline %s/%s with id %s" group name run-id)})
              _ (xt/await-tx database
                             (xt/submit-tx database
                                           [[::xt/put
                                             (assoc run-info
                                                    :status :initializing
                                                    :initiated-at (Instant/now))]]))
              _ (ev/emit producer {:type "Normal"
                                   :reason "ImagePull"
                                   :kind "Pipeline"
                                   :message (format "Pulling image %s for %s" image run-id)})
              _ (eng/pull-image image)
              _ (xt/await-tx database
                             (xt/submit-tx database
                                           [[::xt/put
                                             (assoc (run-info-of database run-id)
                                                    :status :initialized
                                                    :initialized-at (Instant/now))]]))
              _ (mark-image-for-gc image run-id)
              build-state {:image image
                           :mounted #{}
                           :run-id run-id
                           :queue-chan queue-chan
                           :env vars
                           :group group
                           :name name}
              _ (xt/await-tx database
                             (xt/submit-tx database
                                           [[::xt/put
                                             (assoc (run-info-of database run-id)
                                                    :status :running
                                                    :started-at (Instant/now))]]))
              _ (ev/emit producer {:type "Normal"
                                   :reason "PipelineRunning"
                                   :kind "Pipeline"
                                   :message (str "Pipeline now running " run-id)})
              _ (reduce #(exec-step config %1 %2) build-state steps) ;; This is WHOLE of Bob!
              _ (gc-images run-id)
              _ (clean-up-run run-id)
              _ (xt/await-tx database
                             (xt/submit-tx database
                                           [[::xt/put
                                             (assoc (run-info-of database run-id)
                                                    :status :passed
                                                    :completed-at (Instant/now))]]))
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
      (let [{:keys [status] :as run} (xt/entity (xt/db database) run-db-id)
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
          (xt/await-tx
           database
           (xt/submit-tx
            database
            [[::xt/put
              (assoc (run-info-of database run-id) :status :failed :completed-at (Instant/now))]]))))
      (gc-images run-id)
      (clean-up-run run-id)
      (errors/publish-error queue-chan (str "Pipeline failure: " (f/message err)))
      (when delivery-tag
        (lb/ack queue-chan delivery-tag))
      (f/fail run-id))))

(defn start
  "Attempts to asynchronously start a pipeline by group and name."
  [config queue-chan {:keys [group name run-id] :as data} {:keys [delivery-tag]}]
  (if-not (spec/valid? :bob.command.pipeline-start/data data)
    (errors/publish-error queue-chan (str "Invalid pipeline start command: " data))
    (let [run-db-id (keyword "bob.pipeline.run" run-id)
          run-info {:xt/id run-db-id
                    :type :pipeline-run
                    :group group
                    :name name}
          ;; TODO: nack if current node cannot process this
          run-ref (future (start* config queue-chan group name run-id run-info run-db-id delivery-tag))]
      (swap! node-state
             update-in
             [:runs run-id]
             assoc
             :ref
             run-ref)
      run-ref)))

(defn stop
  "Idempotently stops a pipeline by group, name and run-id

  Sets the :status in Db to :stopped and kills the container if present.
  This triggers a pipeline failure which is specially dealt with."
  [{:keys [database stream]} queue-chan {:keys [group name run-id] :as data} _meta]
  (if-not (spec/valid? :bob.command.pipeline-stop/data data)
    (errors/publish-error queue-chan (str "Invalid pipeline stop command: " data))
    (when-let [run (get-in @node-state [:runs run-id])]
      (log/infof "Stopping run %s for pipeline %s %s"
                 run-id
                 group
                 name)
      (xt/await-tx database
                   (xt/submit-tx
                    database
                    [[::xt/put
                      (assoc (run-info-of database run-id) :status :stopped :completed-at (Instant/now))]]))
      (ev/emit (:producer stream)
               {:type "Normal"
                :reason "PipelineStopped"
                :kind "Pipeline"
                :message (str "Pipeline stopped " run-id)})
      (when-let [container (:container-id run)]
        (eng/kill-container container)
        (eng/delete-container container)
        (gc-images run-id))
      (when-let [run (get-in @node-state [:runs run-id :ref])]
        (when-not (future-done? run)
          (future-cancel run))))))

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

  (xt/q (xt/db database)
        '{:find [(pull log [:line])]
          :where [[log :type :log-line] [log :run-id run-id]]})

  (->> (xt/q (xt/db database)
             '{:find [(pull run [:xt/id])]
               :where [[run :type :pipeline-run]]})
       first
       (map :xt/id)
       (map name))

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
