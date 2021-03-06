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

(ns apiserver.handlers
  (:require [clojure.java.io :as io]
            [clojure.set :as s]
            [clojure.instant :as ins]
            [clojure.string :as cs]
            [failjure.core :as f]
            [clojure.data.json :as json]
            [langohr.basic :as lb]
            [crux.api :as crux]
            [java-http-clj.core :as http]
            [apiserver.healthcheck :as hc]
            [apiserver.metrics :as metrics]
            [apiserver.cctray :as cctray])
  (:import [java.util UUID]))

(defn respond
  ([content]
   (respond content 202))
  ([content status]
   {:status status
    :body   {:message content}}))

(defn- publish
  [chan msg-type exchange routing-key message]
  (lb/publish chan
              exchange
              routing-key
              (json/write-str message :key-fn #(subs (str %) 1))
              {:content-type "application/json"
               :type         msg-type}))

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
  (let [check (hc/check {:db    db
                         :queue queue})]
    (if (f/failed? check)
      (respond (f/message check) 500)
      (respond "Yes we can! 🔨 🔨" 200))))

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
    queue                 :queue}]
  (exec #(publish queue
                  "pipeline/delete"
                  "bob.direct"
                  "bob.entities"
                  pipeline-info)))

(defn pipeline-start
  [{{pipeline-info :path
     metadata      :body}
    :parameters
    queue :queue}]
  (let [id      (str "r-" (UUID/randomUUID))
        message (assoc pipeline-info
                       :metadata
                       (if metadata
                         metadata
                         {:runner/type "docker"}))]
    (exec #(publish queue
                    "pipeline/start"
                    "bob.direct"
                    "bob.jobs"
                    (assoc message :run_id id))
          id)))

(defn pipeline-stop
  [{{pipeline-info :path} :parameters
    queue                 :queue}]
  (exec #(publish queue
                  "pipeline/stop"
                  "bob.fanout"
                  ""
                  (s/rename-keys pipeline-info {:id :run_id}))))

(defn- pausable?
  [db run-id]
  (let [{:keys [status]} (crux/entity (crux/db db) (keyword "bob.pipeline.run" run-id))]
    (= :running status)))

(defn pipeline-pause-unpause
  [pause?
   {{pipeline-info :path} :parameters
    db                    :db
    queue                 :queue}]
  (if (and pause? (not (pausable? db (:id pipeline-info))))
    (respond "Pipeline cannot be paused/is already paused now. Try again when running or stop it." 422)
    (exec #(publish queue
                    (if pause?
                      "pipeline/pause"
                      "pipeline/unpause")
                    "bob.fanout"
                    ""
                    (s/rename-keys pipeline-info {:id :run_id})))))

(defn pipeline-logs
  [{{{:keys [id offset lines]} :path} :parameters
    db                                :db}]
  (f/try-all [result   (crux/q (crux/db db)
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
  (f/try-all [{:keys [status]} (crux/entity (crux/db db) (keyword "bob.pipeline.run" id))]
    (if (some? status)
      (respond status 200)
      (respond "Cannot find status" 404))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-runs-list
  [{{{:keys [group name]} :path} :parameters
    db                           :db}]
  (f/try-all [result   (crux/q (crux/db db)
                               {:find  ['(pull run [:status :crux.db/id])]
                                :where [['run :type :pipeline-run]
                                        ['run :group group]
                                        ['run :name name]]})
              response (->> result
                            (map first)
                            (map #(s/rename-keys % {:crux.db/id :run_id}))
                            (map #(update % :run_id clojure.core/name)))]
    (respond response 200)
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-artifact
  [{{{:keys [group name id store-name artifact-name]} :path} :parameters
    db                                                       :db}]
  (f/try-all [base-url (-> (crux/entity (crux/db db) (keyword "bob.artifact-store" store-name))
                           (get :url))
              _        (when (nil? base-url)
                         (f/fail {:type :external
                                  :msg  (str "Cannot locate artifact store " store-name)}))
              url      (cs/join "/" [base-url "bob_artifact" group name id artifact-name])
              resp     (http/get url {:follow-redirects true} {:as :input-stream})
              _        (when (>= (:status resp) 400)
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
  (f/try-all [base-query '{:find  [(pull pipeline [:steps :vars :resources :image :group :name])]
                           :where [[pipeline :type :pipeline]]}
              clauses    {:group  [['pipeline :group group]]
                          :name   [['pipeline :name name]]
                          :status [['run :type :pipeline-run]
                                   ['run :status status]]}
              filters    (mapcat #(get clauses (key %)) query)
              result     (crux/q (crux/db db)
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
  (f/try-all [result (crux/q (crux/db db)
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
  (f/try-all [result (crux/q (crux/db db)
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
                           (crux/db db)
                           (crux/db db (ins/read-instant-date t)))]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (json/write-str (crux/q db-in-time query))}
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
  [{queue :queue
    db    :db}]
  (f/try-all [metrics (metrics/collect-metrics queue db)]
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
