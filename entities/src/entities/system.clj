; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.system
  (:require [com.stuartsierra.component :as component]
            [common.system :as sys]
            [common.dispatch :as d]
            [entities.pipeline :as pipeline]
            [entities.artifact-store :as artifact-store]
            [entities.resource-provider :as resource-provider]))

(def ^:private routes
  {"pipeline/create"          pipeline/create
   "pipeline/delete"          pipeline/delete
   "artifact-store/create"    artifact-store/register-artifact-store
   "artifact-store/delete"    artifact-store/un-register-artifact-store
   "resource-provider/create" resource-provider/register-resource-provider
   "resource-provider/delete" resource-provider/un-register-resource-provider})

(defrecord QueueConf
  [database]
  component/Lifecycle
  (start [this]
    (assoc this
           :conf
           {:exchanges     {"bob.direct" {:type "direct"}
                            :durable     true}
            :queues        {"bob.errors"   {:exclusive   false
                                            :auto-delete false
                                            :durable     true}
                            "bob.entities" {:exclusive   false
                                            :auto-delete false
                                            :durable     true}}
            :bindings      {"bob.entities" "bob.direct"}
            :subscriptions {"bob.entities" (partial d/queue-msg-subscriber
                                                    (sys/db-client database)
                                                    routes)}}))
  (stop [this]
    (assoc this :conf nil)))

(def system-map
  (component/system-map
    :database (sys/map->Database {})
    :conf     (component/using (map->QueueConf {})
                               [:database])
    :queue    (component/using (sys/map->Queue {})
                               [:conf])))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (component/start system-map))))

(defn stop
  []
  (alter-var-root #'system
                  #(when %
                     (component/stop %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
