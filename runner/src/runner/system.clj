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
   [common.system :as cs]
   [failjure.core :as f]
   [integrant.core :as ig]
   [runner.pipeline :as p])
  (:import
   [com.rabbitmq.stream.impl StreamEnvironment StreamProducer]
   [java.net ConnectException]))

(def ^:private routes
  {"pipeline/start" p/start
   "pipeline/stop" p/stop})

(def config
  (-> "bob/conf.edn"
      (io/resource)
      (aero/read-config {:resolver cs/resource-resolver})
      (dissoc :common)))

(defn retry
  [attempts ^Long delay conn-fn]
  (if (zero? attempts)
    (throw (ConnectException. "Cannot establish stream connection"))
    (let [res (f/try* (conn-fn))]
      (if (f/failed? res)
        (do
          (log/warnf "Stream connection failed with %s, retrying %d" (f/message res) attempts)
          (Thread/sleep delay)
          (recur conn-fn (dec attempts) delay))
        res))))

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
  [_ {:keys [^StreamEnvironment stream-env ^String stream-name retry-attempts retry-delay]}]
  (log/info "Setting up producer for RabbitMQ stream")
  (retry
   retry-attempts
   retry-delay
   #(.. stream-env
        producerBuilder
        (stream stream-name)
        (name "bob-container-runner")
        build)))

(defmethod ig/halt-key!
  :runner/event-producer
  [_ ^StreamProducer producer]
  (log/info "Tearing down producer for RabbitMQ stream")
  (.close producer))

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
  (reset))
