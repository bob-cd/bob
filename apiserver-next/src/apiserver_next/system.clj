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
            [apiserver_next.server :as s])
  (:import [java.net ConnectException]
           [org.eclipse.jetty.server Server]))

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
  [host port]
  component/Lifecycle
  (start [this]
    (log/info "Starting APIServer")
    (let [server (assoc this
                        :api-server
                        (jetty/run-jetty (var s/server)
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

(def system-map
  (component/system-map
    :api-server (map->APIServer {:host api-host
                                 :port api-port})))

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
