; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.handlers
  (:require
   [apiserver.cctray :as cctray]
   [apiserver.entities.externals :as externals]
   [apiserver.entities.pipeline :as pipeline]
   [apiserver.healthcheck :as hc]
   [apiserver.metrics :as metrics]
   [apiserver.runs :as r]
   [babashka.http-client :as http]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.set :as s]
   [clojure.spec.alpha :as spec]
   [clojure.string :as cs]
   [clojure.tools.logging :as log]
   [common.capacity :as cp]
   [common.events :as ev]
   [common.schemas]
   [failjure.core :as f]
   [ring.core.protocols :as p]
   [xtdb.api :as xt])
  (:import
   [com.rabbitmq.stream Environment MessageHandler OffsetSpecification]
   [java.io BufferedReader InputStream OutputStream]
   [java.time Instant]
   [java.util.concurrent Executors]))

;; Helpers

(defn respond
  ([content]
   (respond content 202))
  ([content status]
   {:status status
    :body {:message content}}))

(defn pipeline-data
  [db group name]
  (f/try-all [data (xt/entity
                    (xt/db db)
                    (keyword (str "bob.pipeline." group) name))
              _ (when (and (some? data)
                           (not (spec/valid? :bob.db/pipeline data)))
                  (f/fail (str "Invalid pipeline: " data)))]
    data
    (f/when-failed [err] err)))

(defn get-run
  [db run-id]
  (let [run (xt/entity (xt/db db) (keyword "bob.pipeline.run" run-id))]
    (if (and (some? run)
             (not (spec/valid? :bob.db/run run)))
      (f/fail (str "Invalid run: " run))
      run)))

(defn get-runs
  [db group name]
  (f/try-all [result (xt/q (xt/db db)
                           {:find ['(pull run [*])]
                            :where [['run :type :pipeline-run]
                                    ['run :group group]
                                    ['run :name name]]})]
    (map first result)
    (f/when-failed [err]
      err)))

(defn get-logger
  [db run-id]
  (f/try-all [logger (->> {:find ['(pull logger [*])]
                           :where [['run :xt/id (keyword "bob.pipeline.run" run-id)]
                                   ['run :logger 'logger-name]
                                   ['logger :type :logger]
                                   ['logger :name 'logger-name]]}
                          (xt/q (xt/db db))
                          (ffirst))
              _ (when-not (spec/valid? :bob.db/logger logger)
                  (f/fail (str "Invalid logger: " logger)))]
    logger
    (f/when-failed [err] err)))

;; Handlers

(defn api-spec
  [_]
  {:status 200
   :headers {"Content-Type" "application/yaml"}
   :body (-> "bob/api.yaml"
             io/resource
             io/input-stream)})

(def executor (Executors/newVirtualThreadPerTaskExecutor))

(defn health-check
  [{:keys [db queue]}]
  (let [check (hc/check executor {:db db :queue queue})]
    (if (f/failed? check)
      (respond (f/message check) 500)
      (respond "Yes we can! 🔨 🔨" 200))))

(defn pipeline-create
  [{{pipeline :body} :parameters
    db :db
    stream :stream}]
  (f/try-all [_ (pipeline/create db stream pipeline)]
    (respond "Ok" 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-delete
  [{{pipeline-info :path} :parameters
    db :db
    stream :stream}]
  (f/try-all [{:keys [group name]} pipeline-info
              runs (get-runs db group name)
              running (->> runs
                           (filter #(= (:status %) :running))
                           (map :xt/id)
                           (map clojure.core/name))]
    (if (seq running)
      (respond {:runs running
                :error "Pipeline has active runs. Wait for them to finish or stop them."}
               422)
      (do (pipeline/delete db stream pipeline-info)
          (respond "Ok" 200)))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-start
  [{{{:keys [group name logger]} :path}
    :parameters
    db :db
    queue :queue
    {producer :producer} :stream}]
  (f/try-all [run-id (str "r-" (random-uuid))
              data (pipeline-data db group name)
              _ (when-not data
                  (f/fail :not-found))]
    (if (:paused data)
      (respond "Pipeline is paused. Unpause it first." 422)
      (let [run {:xt/id (keyword "bob.pipeline.run" run-id)
                 :type :pipeline-run
                 :status :pending
                 :scheduled-at (Instant/now)
                 :logger logger
                 :group group
                 :name name}]
        (xt/await-tx db (xt/submit-tx db [[::xt/put run]]))
        (ev/emit producer
                 {:type "Normal"
                  :kind "Pipeline"
                  :reason "PipelineRunScheduled"
                  :message (str "Pipeline run scheduled " run-id)})
        (r/dispatch-start db queue producer (assoc data :logger logger) run-id)
        (respond run-id)))
    (f/when-failed [err]
      (if (= :not-found (f/message err))
        (respond "No such pipeline" 404)
        (respond (f/message err) 500)))))

(defn pipeline-stop
  [{{{:keys [run-id]} :path} :parameters
    db :db
    queue :queue
    {producer :producer} :stream}]
  (f/try-all [{:keys [status] :as run} (get-run db run-id)]
    (if-not status
      (respond "Cannot find run" 404)
      (do (case status
            :running (r/dispatch-stop db queue producer run-id)
            :pending (->> [[::xt/put (assoc run
                                            :status :stopped
                                            :completed-at (Instant/now))]]
                          (xt/submit-tx db)
                          (xt/await-tx db))
            (log/warnf "Ignoring stop cmd for run %s due to status %s" run-id status))
          (respond "Ok")))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-pause-unpause
  [pause?
   {{{:keys [group name]} :path} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [data (pipeline-data db group name)
              _ (when-not data
                  (f/fail :not-found))
              changed (if pause?
                        (assoc data :paused true)
                        (dissoc data :paused))
              _ (xt/await-tx db (xt/submit-tx db [[::xt/put changed]]))
              _ (ev/emit producer
                         {:type "Normal"
                          :kind "Pipeline"
                          :reason (if pause? "PipelinePaused" "PipelineUnpaused")
                          :message (format "Pipeline %s/%s %s" group name (if pause? "paused" "unpaused"))})]
    (respond "Ok")
    (f/when-failed [err]
      (if (= :not-found (f/message err))
        (respond "No such pipeline" 404)
        (respond (f/message err) 500)))))

(defn pipeline-logs
  [{{{:keys [id]} :path
     {:keys [follow]} :query} :parameters
    db :db}]
  (f/try-all [{:keys [url]} (get-logger db id)
              url (str url "/bob_logs/" id)
              url (if follow
                    (str url "?follow=true")
                    url)
              {:keys [body status]} (http/get url
                                              {:as :stream
                                               :throw false})
              _ (when (>= status 400)
                  (f/fail (slurp body)))]
    {:status 200
     :headers {"Connection" "Keep-Alive"
               "Transfer-Encoding" "chunked"
               "X-Content-Type-Options" "nosniff"}
     :body (if-not follow
             body
             (reify p/StreamableResponseBody
               (write-body-to-stream [_ _ output-stream]
                 (with-open [r (io/reader body)
                             w (io/writer output-stream)]
                   (try
                     (loop []
                       (let [line (BufferedReader/.readLine r)]
                         (doto w
                           (.write (str line \newline)) ; need the \newline to signal end of chunk
                           (.flush)))
                       (recur))
                     (catch Exception _
                       (InputStream/.close body)
                       (OutputStream/.close output-stream)))))))}
    (f/when-failed [err]
      (let [msg (f/message err)]
        (case (f/message err)
          "Run not found" (respond msg 404)
          (respond (f/message err) 500))))))

(defn pipeline-status
  [{{{:keys [run-id]} :path} :parameters
    db :db}]
  (f/try-all [{:keys [status]} (get-run db run-id)]
    (if (some? status)
      (respond (name status) 200)
      (respond "Cannot find status" 404))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-runs-list
  [{{{:keys [group name]} :path} :parameters
    db :db}]
  (f/try-all [runs (get-runs db group name)
              response (->> runs
                            (map #(dissoc % :group :name :type))
                            (map #(s/rename-keys % {:xt/id :run-id}))
                            (map #(update % :run-id clojure.core/name)))]
    (respond response 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-artifact
  [{{{:keys [group name id store-name artifact-name]} :path} :parameters
    db :db}]
  (f/try-all [store (xt/entity (xt/db db) (keyword "bob.artifact-store" store-name))
              _ (when (nil? store)
                  (f/fail {:type :external
                           :msg (str "Cannot locate artifact store " store-name)}))
              base-url (if-not (spec/valid? :bob.db/artifact-store store)
                         (f/fail (str "Invalid artifact-store: " store))
                         (:url store))
              url (cs/join "/" [base-url "bob_artifact" group name id artifact-name])
              {:keys [body status]} (http/get url
                                              {:as :stream
                                               :throw false})
              _ (when (>= status 400)
                  (f/fail {:type :external
                           :msg (slurp body)}))]
    {:status 200
     :headers {"Content-Type" "application/tar"}
     :body body}
    (f/when-failed [err]
      (case (get (f/message err) :type)
        :external (-> err
                      f/message
                      :msg
                      (respond 400))
        (respond (str err) 500)))))

(defn pipeline-list
  [{{{:keys [group name status]
      :as query}
     :query}
    :parameters
    db :db}]
  (f/try-all [base-query '{:find [(pull pipeline [*])]
                           :where [[pipeline :type :pipeline]]}
              clauses {:group [['pipeline :group group]]
                       :name [['pipeline :name name]]
                       :status [['run :type :pipeline-run]
                                ['run :status status]]}
              filters (mapcat #(get clauses (key %)) query)
              result (xt/q (xt/db db)
                           (update-in base-query [:where] into filters))
              cleaned (->> result
                           (map first)
                           (map #(dissoc % :xt/id :type)))]
    (respond cleaned 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn resource-provider-create
  [{{resource-provider :body} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/create db producer "ResourceProvider" resource-provider)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn resource-provider-delete
  [{{{:keys [name]} :path} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/delete db producer "ResourceProvider" name)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn resource-provider-list
  [{{{:keys [name]} :query} :parameters
    db :db}]
  (f/try-all [clauses [['resource-provider :type :resource-provider]]
              clauses (if name
                        (conj clauses ['resource-provider :name name])
                        clauses)
              result (xt/q (xt/db db)
                           {:find '[(pull resource-provider [:name :url])]
                            :where clauses})]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn artifact-store-create
  [{{artifact-store :body} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/create db producer "ArtifactStore" artifact-store)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn artifact-store-delete
  [{{{:keys [name]} :path} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/delete db producer "ArtifactStore" name)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn artifact-store-list
  [{{{:keys [name]} :query} :parameters
    db :db}]
  (f/try-all [clauses [['artifact-store :type :artifact-store]]
              clauses (if name
                        (conj clauses ['artifact-store :name name])
                        clauses)
              result (xt/q (xt/db db)
                           {:find '[(pull artifact-store [:name :url])]
                            :where clauses})]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn logger-create
  [{{logger :body} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/create db producer "Logger" logger)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn logger-delete
  [{{{:keys [name]} :path} :parameters
    db :db
    {producer :producer} :stream}]
  (f/try-all [_ (externals/delete db producer "Logger" name)]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn logger-list
  [{{{:keys [name]} :query} :parameters
    db :db}]
  (f/try-all [clauses [['logger :type :logger]]
              clauses (if name
                        (conj clauses ['logger :name name])
                        clauses)
              result (xt/q (xt/db db)
                           {:find '[(pull logger [:name :url])]
                            :where clauses})]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn query
  [{{{:keys [q t]} :query} :parameters
    db :db}]
  (f/try-all [query (read-string q)
              db-in-time (if (nil? t)
                           (xt/db db)
                           (xt/db db t))]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/write-str (xt/q db-in-time query))}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn events
  [{{:keys [^Environment env name]} :stream}]
  {:status 200
   :headers {"content-type" "text/event-stream"
             "transfer-encoding" "chunked"}
   :body (reify p/StreamableResponseBody
           (write-body-to-stream [_ _ output-stream]
             (with-open [w (io/writer output-stream)]
               (let [complete (promise)
                     consumer (.. env
                                  consumerBuilder
                                  (stream name)
                                  (offset (OffsetSpecification/first))
                                  (messageHandler
                                   (reify MessageHandler
                                     (handle [_ _ message]
                                       (try
                                         (doto w
                                           (.write (str "data: " (String/new (.getBodyAsBinary message)) "\n\n")) ;; SSE format: data: foo\n\n
                                           (.flush))
                                         (catch Exception _
                                           (log/info "Event streaming client disconnected")
                                           (deliver complete :done)))))) ;; unblock
                                  build)]
                 @complete ;; block til done
                 (.close consumer)
                 (OutputStream/.close output-stream)))))})

(defn metrics
  [{queue :queue
    db :db
    queue-conn-opts :queue-conn-opts}]
  (f/try-all [metrics (metrics/collect-metrics queue db queue-conn-opts)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body metrics}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn cctray
  [{db :db}]
  (f/try-all [report (cctray/generate-report db)]
    {:status 200
     :headers {"Content-Type" "application/xml"}
     :body report}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn cluster-info
  [{db :db}]
  (f/try-all [info (cp/cluster-info db)]
    (respond info)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(def handlers
  {"GetApiSpec" api-spec
   "HealthCheck" health-check
   "PipelineCreate" pipeline-create
   "PipelineDelete" pipeline-delete
   "PipelineStart" pipeline-start
   "PipelineStop" pipeline-stop
   "PipelinePause" #(pipeline-pause-unpause true %)
   "PipelineUnpause" #(pipeline-pause-unpause false %)
   "PipelineLogs" pipeline-logs
   "PipelineStatus" pipeline-status
   "PipelineRuns" pipeline-runs-list
   "PipelineArtifactFetch" pipeline-artifact
   "PipelineList" pipeline-list
   "ResourceProviderCreate" resource-provider-create
   "ResourceProviderDelete" resource-provider-delete
   "ResourceProviderList" resource-provider-list
   "ArtifactStoreCreate" artifact-store-create
   "ArtifactStoreDelete" artifact-store-delete
   "ArtifactStoreList" artifact-store-list
   "LoggerCreate" logger-create
   "LoggerDelete" logger-delete
   "LoggerList" logger-list
   "Query" query
   "GetEvents" events
   "GetMetrics" metrics
   "CCTray" cctray
   "ClusterInfo" cluster-info})

(comment
  (set! *warn-on-reflection* true)

  (-> "http://localhost:8001/bob_artifact/dev/test/r-1/test.tar"
      (http/get {:as :stream})
      :body
      slurp))
