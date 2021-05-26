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

(ns common.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :as env]
            [taoensso.timbre :as log]
            [failjure.core :as f]
            [crux.api :as crux]
            [crux.jdbc :as jdbc]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc])
  (:import [java.net ConnectException]
           [crux.api ICruxAPI]))

(defn int-from-env
  [key default]
  (try
    (Integer/parseInt (get env/env key (str default)))
    (catch Exception _ default)))

(defonce storage-url (:bob-storage-url env/env "jdbc:postgresql://localhost:5432/bob"))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-name (:bob-storage-database env/env "bob"))
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
             #(crux/start-node {::jdbc/connection-pool {:dialect 'crux.jdbc.psql/->dialect
                                                        :db-spec {:jdbcUrl  db-url
                                                                  :user     db-user
                                                                  :password db-password}}
                                :crux/tx-log           {:crux/module     `crux.jdbc/->tx-log
                                                        :connection-pool ::jdbc/connection-pool}
                                :crux/document-store   {:crux/module     `crux.jdbc/->document-store
                                                        :connection-pool ::jdbc/connection-pool}}))))
  (stop [this]
    (log/info "Disconnecting DB")
    (.close ^ICruxAPI (:client this))
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
