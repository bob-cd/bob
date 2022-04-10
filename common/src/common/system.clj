; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.system
  (:require [integrant.core :as ig]
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
    (parse-long (get env/env key (str default)))
    (catch Exception _ default)))

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

(defn configure
  [{:keys [storage queue]}]
  {:bob/storage storage :bob/queue queue})

(defmethod ig/init-key
  :bob/storage
  [_ {:keys [url user password]}]
  (log/info "Connecting to DB")
  (try-connect
    #(xt/start-node {:xtdb.jdbc/connection-pool {:dialect {:xtdb/module 'xtdb.jdbc.psql/->dialect}
                                                 :db-spec {:jdbcUrl  url
                                                           :user     user
                                                           :password password}}
                     :xtdb/tx-log               {:xtdb/module     'xtdb.jdbc/->tx-log
                                                 :connection-pool :xtdb.jdbc/connection-pool}
                     :xtdb/document-store       {:xtdb/module     'xtdb.jdbc/->document-store
                                                 :connection-pool :xtdb.jdbc/connection-pool}})))

(defmethod ig/halt-key!
  :bob/storage
  [_ ^IXtdb node]
  (log/info "Disconnecting DB")
  (.close node))

(defn fanout?
  [conf ex]
  (= "fanout" (get-in conf [:exchanges ex :type])))

(defmethod ig/init-key
  :bob/queue
  [_ {:keys [url user password conf]}]
  (let [conn (try-connect #(rmq/connect {:uri      url
                                         :username user
                                         :password password}))
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
    {:conn conn :chan chan}))

(defmethod ig/halt-key!
  :bob/queue
  [_ {:keys [conn]}]
  (log/info "Disconnecting Queue")
  (when-not (rmq/open? conn)
    (rmq/close conn)))
