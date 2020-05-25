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
            [ragtime.repl :as repl]
            [ragtime.jdbc :as jdbc]
            [hikari-cp.core :as h]
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

(defonce db-host (:bob-postgres-host env/env "localhost"))
(defonce db-port (int-from-env :bob-postgres-port 5432))
(defonce db-user (:bob-postgres-user env/env "bob"))
(defonce db-name (:bob-postgres-database env/env "bob"))
(defonce db-password (:bob-postgres-password env/env "bob"))

(defonce queue-host (:bob-rmq-host env/env "localhost"))
(defonce queue-port (int-from-env :bob-rmq-port 5672))
(defonce queue-user (:bob-rmq-user env/env "guest"))
(defonce queue-password (:bob-rmq-password env/env "guest"))

(defprotocol IDatabase
  (db-connection [this]))

(defrecord Database
  [jdbc-url connection-timeout]
  component/Lifecycle
  (start [this]
    (let [data-source      (h/make-datasource {:jdbc-url           jdbc-url
                                               :connection-timeout connection-timeout})
          migration-config {:datastore  (jdbc/sql-database {:connection-uri jdbc-url})
                            :migrations (jdbc/load-resources "migrations")}]
      (repl/migrate migration-config)
      (assoc this :conn data-source)))
  (stop [this]
    (log/info "Disconnecting DB")
    (h/close-datasource (:conn this))
    (assoc this :conn nil))
  IDatabase
  (db-connection [this]
    (:conn this)))

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
      (lc/subscribe chan queue-name (partial d/queue-msg-subscriber (:conn database)) {:auto-ack true})
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
    :database (map->Database {:jdbc-url           (format "jdbc:postgresql://%s:%d/%s?user=%s&password=%s"
                                                          db-host
                                                          db-port
                                                          db-name
                                                          db-user
                                                          db-password)
                              :connection-timeout 5000})))

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
