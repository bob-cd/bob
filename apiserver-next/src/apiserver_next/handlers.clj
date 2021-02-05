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

(ns apiserver_next.handlers
  (:require [clojure.java.io :as io]
            [clojure.set :as s]
            [clojure.instant :as ins]
            [clojure.string :as cs]
            [failjure.core :as f]
            [jsonista.core :as json]
            [langohr.basic :as lb]
            [crux.api :as crux]
            [java-http-clj.core :as http]
            [apiserver_next.healthcheck :as hc])
  (:import [java.util UUID]))

(defn respond
  ([content]
   (respond content 200))
  ([content status]
   {:status status
    :body   {:message content}}))

(defn publish
  [chan msg-type exchange routing-key message]
  (lb/publish chan
              exchange
              routing-key
              (json/write-value-as-string message)
              {:content-type "application/json"
               :type         msg-type}))

(defn- exec
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
      (respond "Yes we can! 🔨 🔨"))))

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
  [{{pipeline-info :path} :parameters
    queue                 :queue}]
  (let [id (str "r-" (UUID/randomUUID))]
    (exec #(publish queue
                    "pipeline/start"
                    "bob.direct"
                    "bob.jobs"
                    (assoc pipeline-info :run_id id))
          id)))

(defn pipeline-stop
  [{{pipeline-info :path} :parameters
    queue                 :queue}]
  (exec #(publish queue
                  "pipeline/stop"
                  "bob.direct"
                  "bob.jobs"
                  (s/rename-keys pipeline-info {:id :run_id}))))

(defn pipeline-pause-unpause
  [pause?
   {{pipeline-info :path} :parameters
    queue                 :queue}]
  (exec #(publish queue
                  (if pause?
                    "pipeline/pause"
                    "pipeline/unpause")
                  "bob.fanout"
                  ""
                  (s/rename-keys pipeline-info {:id :run_id}))))

(defn pipeline-logs
  [{{{:keys [id offset lines]} :path} :parameters
    db                                :db}]
  (f/try-all [result (crux/q (crux/db db)
                             `{:find     [(eql/project log [:line]) time]
                               :where    [[log :type :log-line]
                                          [log :time time]
                                          [log :run-id ~id]]
                               :order-by [[time :asc]]
                               :limit    ~lines
                               :offset   ~offset})]
    (respond (->> result
                  (map first)
                  (map :line)))
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn pipeline-status
  [{{{:keys [id]} :path} :parameters
    db                   :db}]
  (f/try-all [result (crux/q (crux/db db)
                             `{:find  [(eql/project run [:status])]
                               :where [[run :type :pipeline-run]
                                       [run :crux.db/id ~(keyword "bob.pipeline.run" id)]]})
              status (->> result
                          (map first)
                          (map :status)
                          first)]
    (if (some? status)
      (respond status)
      (respond "Cannot find status" 404))
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
  (f/try-all [base-query '{:find  [(eql/project pipeline [:steps :vars :resources :image :group :name])]
                           :where [[pipeline :type :pipeline]]}
              clauses    {:group  [['pipeline :group group]]
                          :name   [['pipeline :name name]]
                          :status [['run :type :pipeline-run]
                                   ['run :status status]]}
              filters    (mapcat #(get clauses (key %)) query)
              result     (crux/q (crux/db db)
                                 (update-in base-query [:where] into filters))]
    (respond (map first result))
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
                             '{:find  [(eql/project resource-provider [:name :url])]
                               :where [[resource-provider :type :resource-provider]]})]
    (respond (map first result))
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
                             '{:find  [(eql/project artifact-store [:name :url])]
                               :where [[artifact-store :type :artifact-store]]})]
    (respond (map first result))
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
     :body    (json/write-value-as-string (crux/q db-in-time query))}
    (f/when-failed [err]
      (respond (f/message err) 500))))

(defn errors
  [{queue :queue}]
  (f/try-all [result   (lb/get queue "bob.errors" true)
              response (if (nil? result)
                         "No more errors"
                         result)]
    (respond response)
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
   "PipelineArtifactFetch"  pipeline-artifact
   "PipelineList"           pipeline-list
   "ResourceProviderCreate" resource-provider-create
   "ResourceProviderDelete" resource-provider-delete
   "ResourceProviderList"   resource-provider-list
   "ArtifactStoreCreate"    artifact-store-create
   "ArtifactStoreDelete"    artifact-store-delete
   "ArtifactStoreList"      artifact-store-list
   "Query"                  query
   "GetError"               errors})

(comment
  (-> "http://localhost:8001/bob_artifact/dev/test/r-1/test.tar"
      (http/get {:follow-redirects true} {:as :input-stream})
      :body
      slurp))
