; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [common.dispatch :as d]
   [common.heartbeat :as hb]
   [common.system :as cs]
   [integrant.core :as ig]
   [runner.pipeline :as p])
  (:import
   [com.rabbitmq.stream.impl StreamEnvironment StreamProducer]
   [java.util.concurrent Future]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop" p/stop})

(def config
  (-> "bob/conf.edn"
      (io/resource)
      (aero/read-config {:resolver cs/resource-resolver})
      (dissoc :common)))

(defmethod ig/init-key
  :runner/queue-config
  [_ {:keys [queue producer] :as config}]
  (let [broadcast-queue (str "bob.broadcasts." (random-uuid))
        subscriber (partial d/queue-msg-subscriber config routes)
        jobs-queue "bob.container.jobs"]
    (merge-with merge
                queue
                {:producer producer}
                {:queues {jobs-queue {}
                          broadcast-queue {:arguments {}
                                           :exclusive true
                                           :auto-delete true}}
                 :bindings {jobs-queue "bob.direct"
                            broadcast-queue "bob.fanout"}
                 :subscriptions {jobs-queue subscriber
                                 broadcast-queue subscriber}})))

(defmethod ig/init-key
  :runner/event-producer
  [_ {:keys [^StreamEnvironment stream-env ^String stream-name]}]
  (log/info "Setting up producer for RabbitMQ stream")
  (cs/try-connect
   #(.. stream-env
        producerBuilder
        (stream stream-name)
        (name "bob-container-runner")
        build)))

(defmethod ig/halt-key!
  :runner/event-producer
  [_ producer]
  (log/info "Tearing down producer for RabbitMQ stream")
  (StreamProducer/.close producer))

(defmethod ig/init-key
  :bob/runner-heartbeat
  [_ {:keys [queue db freq]}]
  (hb/schedule #(hb/beat-it db queue :bob/node-type :runner/container)
               "heartbeat"
               freq))

(defmethod ig/halt-key!
  :bob/runner-heartbeat
  [_ task]
  (Future/.cancel task true))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (ig/init config))))

(defn stop
  []
  (alter-var-root #'system #(when % (ig/halt! %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (set! *warn-on-reflection* true)

  (reset))
