; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :as env]
            [taoensso.timbre :as log]
            [failjure.core :as f]
            [xtdb.api :as xt]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc])
  (:import [java.net ConnectException]
           [xtdb.api IXtdb]))

(defn int-from-env
  [key default]
  (try
    (Integer/parseInt (get env/env key (str default)))
    (catch Exception _ default)))

(defonce storage-url (:bob-storage-url env/env "jdbc:postgresql://localhost:5432/bob"))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-password (:bob-storage-password env/env "bob"))

(defonce queue-url (:bob-queue-url env/env "amqp://localhost:5672"))
(defonce queue-user (:bob-queue-user env/env "guest"))
(defonce queue-password (:bob-queue-password env/env "guest"))

(defonce connection-retry-attempts (int-from-env :bob-connection-retry-attempts 10))
(defonce connection-retry-delay (int-from-env :bob-connection-retry-delay 2000))

(defn try-connect
  ([conn-fn]
   (try-connect conn-fn connection-retry-attempts))
  ([conn-fn n]
   (if (= n 0)
     (throw (ConnectException. "Cannot connect to system"))
     (let [res (f/try*
                 (conn-fn))]
       (if (f/failed? res)
         (do
           (log/warnf "Connection failed with %s, retrying %d" (f/message res) n)
           (Thread/sleep connection-retry-delay)
           (recur conn-fn (dec n)))
         res)))))

(defprotocol IDatabase
  (db-client [this]))

(defrecord Database
  [db-url db-user db-password]
  component/Lifecycle
  (start [this]
    (log/info "Connecting to DB")
    (assoc this
           :client
           (try-connect
             #(xt/start-node {:xtdb.jdbc/connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                                          :db-spec {:jdbcUrl  db-url
                                                                    :user     db-user
                                                                    :password db-password}}
                              :xtdb/tx-log               {:xtdb/module     'xtdb.jdbc/->tx-log
                                                          :connection-pool :xtdb.jdbc/connection-pool}
                              :xtdb/document-store       {:xtdb/module     'xtdb.jdbc/->document-store
                                                          :connection-pool :xtdb.jdbc/connection-pool}}))))
  (stop [this]
    (log/info "Disconnecting DB")
    (.close ^IXtdb (:client this))
    (assoc this :client nil))
  IDatabase
  (db-client [this]
             (:client this)))

(defn fanout?
  [conf ex]
  (= "fanout" (get-in conf [:exchanges ex :type])))

(defprotocol IQueue
  (queue-chan [this]))

(defrecord Queue
  [queue-url queue-user queue-password conf]
  component/Lifecycle
  (start [this]
    (let [conn (try-connect #(rmq/connect {:uri      queue-url
                                           :username queue-user
                                           :password queue-password}))
          chan (lch/open conn)]
      (log/infof "Connected on channel id: %d" (.getChannelNumber chan))
      (doseq [[ex props] (:exchanges conf)]
        (log/infof "Declared exchange %s" ex)
        (le/declare chan ex (:type props) (select-keys props [:durable])))
      (doseq [[queue props] (:queues conf)]
        (log/infof "Declared queue %s" queue)
        (lq/declare chan queue props))
      (doseq [[queue ex] (:bindings conf)]
        (log/infof "Bound %s -> %s"
                   queue
                   ex)
        (lq/bind chan
                 queue
                 ex
                 (if (fanout? conf ex)
                   {}
                   {:routing-key queue})))
      (doseq [[queue subscriber] (:subscriptions conf)]
        (log/infof "Subscribed to %s" queue)
        (lc/subscribe chan queue subscriber {:auto-ack true}))
      (assoc this :conn conn :chan chan)))
  (stop [this]
    (log/info "Disconnecting queue")
    (rmq/close (:conn this))
    (assoc this :conn nil :chan nil))
  IQueue
  (queue-chan [this]
              (:chan this)))

(defn db
  ([]
   (Database. storage-url storage-user storage-password))
  ([url user password]
   (Database. url user password)))

(defn queue
  ([conf]
   (Queue. queue-url queue-user queue-password conf))
  ([url user password conf]
   (Queue. url user password conf)))
