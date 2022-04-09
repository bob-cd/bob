; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require [com.stuartsierra.component :as component]
            [common.system :as sys]
            [common.dispatch :as d]
            [runner.pipeline :as p]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop"  p/stop})

(defrecord QueueConf
  [database]
  component/Lifecycle
  (start [this]
    (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
          subscriber      (partial d/queue-msg-subscriber (sys/db-client database) routes)
          conf            {:exchanges     {"bob.direct" {:type    "direct"
                                                         :durable true}
                                           "bob.fanout" {:type    "fanout"
                                                         :durable true}}
                           :queues        {"bob.jobs"      {:exclusive   false
                                                            :auto-delete false
                                                            :durable     true}
                                           "bob.errors"    {:exclusive   false
                                                            :auto-delete false
                                                            :durable     true}
                                           broadcast-queue {:exclusive   true
                                                            :auto-delete true
                                                            :durable     true}}
                           :bindings      {"bob.jobs"      "bob.direct"
                                           broadcast-queue "bob.fanout"}
                           :subscriptions {"bob.jobs"      subscriber
                                           broadcast-queue subscriber}}]
      (assoc this :conf conf)))
  (stop [this]
    (assoc this :conf nil)))

(def system-map
  (component/system-map
    {:database   (sys/map->Database {})
     :queue-conf (component/using (map->QueueConf {})
                                  [:database])
     :queue      (component/using (sys/map->Queue {})
                                  [:queue-conf])}))

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
