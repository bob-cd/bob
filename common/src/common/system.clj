; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [failjure.core :as f]
   [integrant.core :as ig]
   [langohr.channel :as lch]
   [langohr.consumers :as lc]
   [langohr.core :as rmq]
   [langohr.exchange :as le]
   [langohr.queue :as lq]
   [xtdb.api :as xt])
  (:import
   [com.rabbitmq.stream Environment StreamException]
   [com.rabbitmq.stream.impl StreamProducer]
   [java.net ConnectException]
   [java.time Duration]
   [xtdb.api IXtdb]))

(def config
  (-> "bob/common.edn"
      (io/resource)
      (aero/read-config)))

(defn try-connect
  ([conn-fn]
   (try-connect conn-fn (:bob/connection-retry-attempts config)))
  ([conn-fn n]
   (if (zero? n)
     (throw (ConnectException. "Cannot connect to system"))
     (let [res (f/try* (conn-fn))]
       (if (f/failed? res)
         (do
           (log/warnf "Connection failed with %s, retrying %d" (f/message res) n)
           (Thread/sleep ^Long (:bob/connection-retry-delay config))
           (recur conn-fn (dec n)))
         res)))))

(defn resource-resolver
  [_ file-path]
  (io/resource file-path))

(defmethod ig/init-key
  :bob/storage
  [_ {:keys [url user password]}]
  (log/info "Connecting to DB")
  (try-connect
   #(xt/start-node {:xtdb.jdbc/connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                                :db-spec {:jdbcUrl url
                                                          :user user
                                                          :password password}}
                    :xtdb/tx-log {:xtdb/module 'xtdb.jdbc/->tx-log
                                  :connection-pool :xtdb.jdbc/connection-pool}
                    :xtdb/document-store {:xtdb/module 'xtdb.jdbc/->document-store
                                          :connection-pool :xtdb.jdbc/connection-pool}})))

(defmethod ig/halt-key!
  :bob/storage
  [_ node]
  (log/info "Disconnecting DB")
  (IXtdb/.close node))

(defmethod ig/init-key
  :bob/queue
  [_ {:keys [url user password conf api-url node-id]}]
  (let [conn-opts {:uri url
                   :username user
                   :password password
                   :api-url api-url
                   :connection-name node-id}
        conn (try-connect #(rmq/connect conn-opts))
        chan (lch/open conn)]
    (log/infof "Connected on channel id: %d" (.getChannelNumber chan))
    (doseq [[ex props] (:exchanges conf)]
      (log/infof "Declaring exchange %s" ex)
      (le/declare chan ex (:type props) (select-keys props [:durable])))
    (doseq [[queue {:keys [props args]}] (:queues conf)]
      (log/infof "Declaring queue %s" queue)
      (let [opts (merge {:auto-delete false :durable true} props)
            opts (assoc opts :arguments (merge {"x-queue-type" "quorum"} args))]
        (lq/declare chan queue opts)))
    (doseq [[queue ex] (:bindings conf)]
      (log/infof "Binding %s -> %s" queue ex)
      (lq/bind chan
               queue
               ex
               {:routing-key queue}))
    (doseq [[queue subscriber] (:subscriptions conf)]
      (log/infof "Subscribing to %s" queue)
      (lc/subscribe chan queue subscriber))
    {:conn conn :chan chan :conn-opts conn-opts}))

(defmethod ig/halt-key!
  :bob/queue
  [_ {:keys [conn]}]
  (log/info "Disconnecting Queue")
  (when-not (rmq/open? conn)
    (rmq/close conn)))

(defmethod ig/init-key
  :bob/stream
  [_ {:keys [url name retention-days]}]
  (log/info "Setting up RabbitMQ stream" name)
  (try-connect
   #(let [env (.. Environment builder (uri url) build)]
      (try
        (.. env
            streamCreator
            (maxAge (Duration/ofDays retention-days))
            (stream name)
            create)
        (catch StreamException _
          (log/debug "Stream already exists")))
      {:env env
       :producer (.. env
                     producerBuilder
                     (stream name)
                     (name (str (random-uuid))) ;; TODO: Better name
                     build)})))

(defmethod ig/halt-key!
  :bob/stream
  [_ {:keys [env producer]}]
  (log/info "Tearing down RabbitMQ stream")
  (StreamProducer/.close producer)
  (Environment/.close env))

(defmethod ig/init-key
  :bob/node-id
  [_ {:keys [node-type]}]
  (str node-type "-" (random-uuid)))

(defmethod aero/reader
  'ig/ref
  [_ _ value]
  (ig/ref value))

(comment
  (set! *warn-on-reflection* true))
