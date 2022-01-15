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

(defn queue-conf
  [db]
  {:exchanges     {"bob.direct" {:type    "direct"
                                 :durable true}}
   :queues        {"bob.errors"   {:exclusive   false
                                   :auto-delete false
                                   :durable     true}
                   "bob.entities" {:exclusive   false
                                   :auto-delete false
                                   :durable     true}}
   :bindings      {"bob.entities" "bob.direct"}
   :subscriptions {"bob.entities" (partial d/queue-msg-subscriber (sys/db-client db) routes)}})

(def system (atom nil))

(defn start
  []
  (let [db    (component/start (sys/db))
        queue (component/start
                (sys/queue (queue-conf db)))]
    (reset! system [db queue])))

(defn stop
  []
  (when @system
    (run! component/stop (reverse @system))
    (reset! system nil)))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
