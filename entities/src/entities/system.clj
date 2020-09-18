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

(ns entities.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :as env]
            [crux.api :as crux]
            [crux.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.consumers :as lc]
            [entities.dispatch :as d]))

(defn int-from-env
  [key default]
  (try
    (Integer/parseInt (get env/env key (str default)))
    (catch Exception _ default)))

(defonce storage-host (:bob-storage-host env/env "localhost"))
(defonce storage-port (int-from-env :bob-storage-port 5432))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-name (:bob-storage-database env/env "bob"))
(defonce storage-password (:bob-storage-password env/env "bob"))

(defonce queue-host (:bob-queue-host env/env "localhost"))
(defonce queue-port (int-from-env :bob-queue-port 5672))
(defonce queue-user (:bob-queue-user env/env "guest"))
(defonce queue-password (:bob-queue-password env/env "guest"))

(defprotocol IDatabase
  (db-client [this]))

(defrecord Database
  [db-name db-host db-port db-user db-password]
  component/Lifecycle
  (start [this]
    (log/info "Connecting to DB")
    (assoc this
           :client
           (crux/start-node {::jdbc/connection-pool {:dialect 'crux.jdbc.psql/->dialect
                                                     :db-spec {:dbname   db-name
                                                               :host     db-host
                                                               :port     db-port
                                                               :user     db-user
                                                               :password db-password}}
                             :crux/tx-log           {:crux/module     `crux.jdbc/->tx-log
                                                     :connection-pool ::jdbc/connection-pool}
                             :crux/document-store   {:crux/module     `crux.jdbc/->document-store
                                                     :connection-pool ::jdbc/connection-pool}})))
  (stop [this]
    (log/info "Disconnecting DB")
    (.close (:client this))
    (assoc this :client nil))
  IDatabase
  (db-client [this]
             (:client this)))

(defprotocol IQueue
  (queue-chan [this]))

(defrecord Queue
  [database queue-host queue-port queue-user queue-password]
  component/Lifecycle
  (start [this]
    (let [conn       (rmq/connect {:host     queue-host
                                   :port     queue-port
                                   :username queue-user
                                   :vhost    "/"
                                   :password queue-password})
          chan       (lch/open conn)
          queue-name "entities"]
      (log/infof "Connected on channel id: %d" (.getChannelNumber chan))
      (lq/declare chan
                  queue-name
                  {:exclusive   false
                   :auto-delete false})
      (lq/declare chan
                  "errors"
                  {:exclusive   false
                   :auto-delete false})
      (lc/subscribe chan queue-name (partial d/queue-msg-subscriber (db-client database)) {:auto-ack true})
      (log/infof "Subscribed to %s" queue-name)
      (assoc this :conn conn :chan chan)))
  (stop [this]
    (log/info "Disconnecting queue")
    (rmq/close (:conn this))
    (assoc this :conn nil :chan nil))
  IQueue
  (queue-chan [this] (:chan this)))

(def system-map
  (component/system-map
    :queue    (component/using (map->Queue {:queue-host     queue-host
                                            :queue-port     queue-port
                                            :queue-user     queue-user
                                            :queue-password queue-password})
                               [:database])
    :database (map->Database {:db-name     storage-name
                              :db-host     storage-host
                              :db-port     storage-port
                              :db-user     storage-user
                              :db-password storage-password})))

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
  (reset)

  (int-from-env :yalla 42))
