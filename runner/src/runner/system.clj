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

(defn queue-conf
  [db]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber      (partial d/queue-msg-subscriber (sys/db-client db) routes)]
    {:exchanges     {"bob.direct" {:type    "direct"
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
                     broadcast-queue subscriber}}))

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
