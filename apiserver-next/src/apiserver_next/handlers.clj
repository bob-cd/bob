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
            [failjure.core :as f]
            [jsonista.core :as json]
            [langohr.basic :as lb]
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
  [{{{group :group
      name  :name}
     :path
     pipeline :body}
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

(def handlers
  {"GetApiSpec"      api-spec
   "HealthCheck"     health-check
   "PipelineCreate"  pipeline-create
   "PipelineDelete"  pipeline-delete
   "PipelineStart"   pipeline-start
   "PipelineStop"    pipeline-stop
   "PipelinePause"   #(pipeline-pause-unpause true %)
   "PipelineUnpause" #(pipeline-pause-unpause false %)})
