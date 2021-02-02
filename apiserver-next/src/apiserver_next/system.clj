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

(ns apiserver_next.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :as env]
            [taoensso.timbre :as log]
            [failjure.core :as f]
            [ring.adapter.jetty :as jetty]
            [crux.api :as crux]
            [crux.jdbc :as jdbc]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [apiserver_next.server :as s])
  (:import [java.net ConnectException]
           [org.eclipse.jetty.server Server]
           [crux.api ICruxAPI]))

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
(defonce api-host (int-from-env :bob-api-host "0.0.0.0"))
(defonce api-port (int-from-env :bob-api-port 7777))
(defonce connection-retry-attempts (int-from-env :bob-connection-retry-attempts 10))
(defonce connection-retry-delay (int-from-env :bob-connection-retry-delay 2000))
(defonce health-check-freq (int-from-env :bob-health-check-freq 5000))

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

(defrecord APIServer
  [database queue host port]
  component/Lifecycle
  (start [this]
    (log/info "Starting APIServer")
    (let [server (assoc this
                        :api-server
                        (jetty/run-jetty (s/server (:client database) (:chan queue))
                                         {:host                 host
                                          :port                 port
                                          :join?                false
                                          :async?               true
                                          :send-server-version? false
                                          :send-date-header?    false}))]
      (log/infof "Listening on %d" port)
      server))
  (stop [this]
    (log/info "Stopping APIServer")
    (.stop ^Server (:api-server this))
    (assoc this :server nil)))

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
    (assoc this :client nil)))

(defrecord Queue
  [queue-url queue-user queue-password]
  component/Lifecycle
  (start [this]
    (let [conn            (try-connect #(rmq/connect {:uri      queue-url
                                                      :username queue-user
                                                      :password queue-password}))
          chan            (lch/open conn)
          job-queue       "bob.jobs"
          direct-exchange "bob.direct"
          error-queue     "bob.errors"
          entities-queue  "bob.entities"
          fanout-exchange "bob.fanout"]
      (log/infof "Connected on channel id: %d" (.getChannelNumber chan))
      (le/declare chan direct-exchange "direct" {:durable true})
      (le/declare chan fanout-exchange "fanout" {:durable true})
      (lq/declare chan
                  job-queue
                  {:exclusive   false
                   :auto-delete false
                   :durable     true})
      (lq/declare chan
                  entities-queue
                  {:exclusive   false
                   :auto-delete false
                   :durable     true})
      (lq/declare chan
                  error-queue
                  {:exclusive   false
                   :auto-delete false
                   :durable     true})
      (lq/bind chan job-queue direct-exchange {:routing-key job-queue})
      (lq/bind chan entities-queue direct-exchange)
      (assoc this :conn conn :chan chan)))
  (stop [this]
    (log/info "Disconnecting queue")
    (rmq/close (:conn this))
    (assoc this :conn nil :chan nil)))

(def system-map
  (component/system-map
    :database   (map->Database {:db-url      storage-url
                                :db-user     storage-user
                                :db-password storage-password})
    :queue      (map->Queue {:queue-url      queue-url
                             :queue-user     queue-user
                             :queue-password queue-password})
    :api-server (component/using (map->APIServer {:host api-host
                                                  :port api-port})
                                 [:database :queue])))

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
