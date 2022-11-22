; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.handlers
  (:require
    [apiserver.cctray :as cctray]
    [apiserver.healthcheck :as hc]
    [apiserver.metrics :as metrics]
    [clojure.data.json :as json]
    [clojure.instant :as ins]
    [clojure.java.io :as io]
    [clojure.set :as s]
    [clojure.spec.alpha :as spec]
    [clojure.string :as cs]
    [common.schemas]
    [failjure.core :as f]
    [java-http-clj.core :as http]
    [langohr.basic :as lb]
    [xtdb.api :as xt])
  (:import
    [java.util.concurrent Executors]))

(def executor (Executors/newVirtualThreadPerTaskExecutor))

(defn respond
  ([content]
   (respond content 202))
  ([content status]
   {:status status
    :body   {:message content}}))

(defn publish
  [chan msg-type exchange routing-key message]
  (lb/publish chan
              exchange
              routing-key
              (json/write-str message :key-fn #(subs (str %) 1))
              {:content-type "application/json"
               :type         msg-type}))

(defn pipeline-data
  [db group name]
  (f/try-all [data (xt/entity
                     (xt/db db)
                     (keyword (str "bob.pipeline." group) name))
              _ (when (and (some? data)
                           (not (spec/valid? :bob.db/pipeline data)))
                  (f/fail (str "Invalid pipeline: " data)))]
    data
    (f/when-failed [err]
      err)))

(defn exec
  ([task]
   (exec task "Ok"))
  ([task response]
   (let [result (f/try*
                  (task))]
     (if (f/failed? result)
       (respond (f/message result) 500)
       (respond response)))))

(defn api-spec
  [_]
  {:status  200
   :headers {"Content-Type" "application/yaml"}
   :body    (-> "bob/api.yaml"
                io/resource
                io/input-stream)})

(defn health-check
  [{:keys [db queue]}]
  (let [check (hc/check executor {:db db :queue queue})]
    (if (f/failed? check)
      (respond (f/message check) 500)
      (respond "Yes we can! 🔨 🔨" 200))))

(defn get-runs
  [db group name]
  (f/try-all [result (xt/q (xt/db db)
                           {:find  ['(pull run [*])]
                            :where [['run :type :pipeline-run]
                                    ['run :group group]
                                    ['run :name name]]})]
    (map first result)
    (f/when-failed [err]
      err)))

(defn pipeline-create
  [{{{:keys [group name]} :path
     pipeline             :body}
    :parameters
    queue :queue}]
  (exec #(publish queue
                  "pipeline/create"
                  "bob.direct"
                  "bob.entities"
                  (-> pipeline
                      (assoc :group group)
                      (assoc :name name)))))

(defn pipeline-delete
  [{{pipeline-info :path} :parameters
    db                    :db
    queue                 :queue}]
  (f/try-all [{:keys [group name]} pipeline-info
              runs                 (get-runs db group name)
              running              (->> runs
                                        (filter #(= (:status %) :running))
                                        (map :xt/id)
                                        (map clojure.core/name))]
    (if (seq running)
      (respond {:runs  running
                :error "Pipeline has active runs. Wait for them to finish or stop them."}
               422)
      (exec #(publish queue
                      "pipeline/delete"
                      "bob.direct"
                      "bob.entities"
                      pipeline-info)))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-start
  [{{pipeline-info :path
     metadata      :body}
    :parameters
    db :db
    queue :queue}]
  (f/try-all [id               (str "r-" (random-uuid))
              {:keys [paused]} (pipeline-data db (:group pipeline-info) (:name pipeline-info))]
    (if paused
      (respond "Pipeline is paused. Unpause it first." 422)
      (exec #(publish queue
                      "pipeline/start"
                      "bob.direct"
                      (format "bob.%s.jobs" (or (:runner/type metadata) "container"))
                      (assoc pipeline-info :run_id id))
            id))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-stop
  [{{pipeline-info :path} :parameters
    queue                 :queue}]
  (exec #(publish queue
                  "pipeline/stop"
                  "bob.fanout"
                  ""
                  (s/rename-keys pipeline-info {:id :run_id}))))

(defn pipeline-pause-unpause
  [pause?
   {{{:keys [group name]} :path} :parameters
    db                           :db}]
  (f/try-all [data    (pipeline-data db group name)
              changed (if pause?
                        (assoc data :paused true)
                        (dissoc data :paused))
              _ (xt/await-tx db (xt/submit-tx db [[::xt/put changed]]))]
    (respond "Ok")
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-logs
  [{{{:keys [id offset lines]} :path} :parameters
    db                                :db}]
  (f/try-all [result   (xt/q (xt/db db)
                             {:find     '[(pull log [:line]) time]
                              :where    [['log :type :log-line]
                                         ['log :time 'time]
                                         ['log :run-id id]]
                              :order-by [['time :asc]]
                              :limit    lines
                              :offset   offset})
              response (->> result
                            (map first)
                            (map :line))]
    (respond response 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-status
  [{{{:keys [id]} :path} :parameters
    db                   :db}]
  (f/try-all [run    (xt/entity (xt/db db) (keyword "bob.pipeline.run" id))
              status (if (and (some? run)
                              (not (spec/valid? :bob.db/run run)))
                       (f/fail (str "Invalid run: " run))
                       (:status run))]
    (if (some? status)
      (respond status 200)
      (respond "Cannot find status" 404))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-runs-list
  [{{{:keys [group name]} :path} :parameters
    db                           :db}]
  (f/try-all [runs     (get-runs db group name)
              response (->> runs
                            (map #(dissoc % :group :name :type))
                            (map #(s/rename-keys % {:xt/id :run_id}))
                            (map #(update % :run_id clojure.core/name)))]
    (respond response 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-artifact
  [{{{:keys [group name id store-name artifact-name]} :path} :parameters
    db                                                       :db}]
  (f/try-all [store    (xt/entity (xt/db db) (keyword "bob.artifact-store" store-name))
              _ (when (nil? store)
                  (f/fail {:type :external
                           :msg  (str "Cannot locate artifact store " store-name)}))
              base-url (if-not (spec/valid? :bob.db/artifact-store store)
                         (f/fail (str "Invalid artifact-store: " store))
                         (:url store))
              url      (cs/join "/" [base-url "bob_artifact" group name id artifact-name])
              resp     (http/get url {:follow-redirects true} {:as :input-stream})
              _ (when (>= (:status resp) 400)
                  (f/fail {:type :external
                           :msg  (-> resp
                                     :body
                                     slurp)}))]
    {:status  200
     :headers {"Content-Type" "application/tar"}
     :body    (:body resp)}
    (f/when-failed [err]
      (case (get (f/message err) :type)
        :external (-> err
                      f/message
                      :msg
                      (respond 400))
        (respond (f/message err) 500)))))

;; TODO: Better way hopefully?
(defn pipeline-list
  [{{{:keys [group name status]
      :as   query}
     :query}
    :parameters
    db :db}]
  (f/try-all [base-query '{:find  [(pull pipeline [:steps :vars :resources :image :group :name :paused])]
                           :where [[pipeline :type :pipeline]]}
              clauses    {:group  [['pipeline :group group]]
                          :name   [['pipeline :name name]]
                          :status [['run :type :pipeline-run]
                                   ['run :status status]]}
              filters    (mapcat #(get clauses (key %)) query)
              result     (xt/q (xt/db db)
                               (update-in base-query [:where] into filters))]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn resource-provider-create
  [{{{:keys [name]}    :path
     resource-provider :body}
    :parameters
    queue :queue}]
  (exec #(publish queue
                  "resource-provider/create"
                  "bob.direct"
                  "bob.entities"
                  (assoc resource-provider :name name))))

(defn resource-provider-delete
  [{{{:keys [name]} :path} :parameters
    queue                  :queue}]
  (exec #(publish queue
                  "resource-provider/delete"
                  "bob.direct"
                  "bob.entities"
                  {:name name})))

(defn resource-provider-list
  [{db :db}]
  (f/try-all [result (xt/q (xt/db db)
                           '{:find  [(pull resource-provider [:name :url])]
                             :where [[resource-provider :type :resource-provider]]})]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn artifact-store-create
  [{{{:keys [name]}    :path
     resource-provider :body}
    :parameters
    queue :queue}]
  (exec #(publish queue
                  "artifact-store/create"
                  "bob.direct"
                  "bob.entities"
                  (assoc resource-provider :name name))))

(defn artifact-store-delete
  [{{{:keys [name]} :path} :parameters
    queue                  :queue}]
  (exec #(publish queue
                  "artifact-store/delete"
                  "bob.direct"
                  "bob.entities"
                  {:name name})))

(defn artifact-store-list
  [{db :db}]
  (f/try-all [result (xt/q (xt/db db)
                           '{:find  [(pull artifact-store [:name :url])]
                             :where [[artifact-store :type :artifact-store]]})]
    (respond (map first result) 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn query
  [{{{:keys [q t]} :query} :parameters
    db                     :db}]
  (f/try-all [query      (read-string q)
              db-in-time (if (nil? t)
                           (xt/db db)
                           (xt/db db (ins/read-instant-date t)))]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str (xt/q db-in-time query))}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn errors
  [{queue :queue}]
  (f/try-all [[_ result] (lb/get queue "bob.errors" true)
              response   (if (nil? result)
                           "No more errors"
                           result)]
    (respond response 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn metrics
  [{queue           :queue
    db              :db
    queue-conn-opts :queue-conn-opts}]
  (f/try-all [metrics (metrics/collect-metrics queue db queue-conn-opts)]
    {:status  200
     :headers {"Content-Type" "text/plain"}
     :body    metrics}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn cctray
  [{db :db}]
  (f/try-all [report (cctray/generate-report db)]
    {:status  200
     :headers {"Content-Type" "application/xml"}
     :body    report}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(def handlers
  {"GetApiSpec"             api-spec
   "HealthCheck"            health-check
   "PipelineCreate"         pipeline-create
   "PipelineDelete"         pipeline-delete
   "PipelineStart"          pipeline-start
   "PipelineStop"           pipeline-stop
   "PipelinePause"          #(pipeline-pause-unpause true %)
   "PipelineUnpause"        #(pipeline-pause-unpause false %)
   "PipelineLogs"           pipeline-logs
   "PipelineStatus"         pipeline-status
   "PipelineRuns"           pipeline-runs-list
   "PipelineArtifactFetch"  pipeline-artifact
   "PipelineList"           pipeline-list
   "ResourceProviderCreate" resource-provider-create
   "ResourceProviderDelete" resource-provider-delete
   "ResourceProviderList"   resource-provider-list
   "ArtifactStoreCreate"    artifact-store-create
   "ArtifactStoreDelete"    artifact-store-delete
   "ArtifactStoreList"      artifact-store-list
   "Query"                  query
   "GetError"               errors
   "GetMetrics"             metrics
   "CCTray"                 cctray})

(comment
  (-> "http://localhost:8001/bob_artifact/dev/test/r-1/test.tar"
      (http/get {:follow-redirects true} {:as :input-stream})
      :body
      slurp))
