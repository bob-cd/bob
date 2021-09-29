; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.system
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :as jetty]
            [common.system :as sys]
            [apiserver.server :as s])
  (:import [org.eclipse.jetty.server Server]))

(defonce api-host (sys/int-from-env :bob-api-host "0.0.0.0"))
(defonce api-port (sys/int-from-env :bob-api-port 7777))
(defonce health-check-freq (sys/int-from-env :bob-health-check-freq 60000))

(defrecord APIServer
  [database queue host port]
  component/Lifecycle
  (start [this]
    (log/info "Starting APIServer")
    (let [server (assoc this
                        :api-server
                        (jetty/run-jetty (s/server (:client database) (:chan queue) health-check-freq)
                                         {:host   host
                                          :port   port
                                          :join?  false
                                          :async? true
                                          :send-server-version? false
                                          :send-date-header? false}))]
      (log/infof "Listening on %d" port)
      server))
  (stop [this]
    (log/info "Stopping APIServer")
    (.stop ^Server (:api-server this))
    (assoc this :server nil)))

(def queue-conf
  {:exchanges {"bob.direct" {:type    "direct"
                             :durable true}
               "bob.fanout" {:type    "fanout"
                             :durable true}}
   :queues    {"bob.jobs"     {:exclusive   false
                               :auto-delete false
                               :durable     true}
               "bob.errors"   {:exclusive   false
                               :auto-delete false
                               :durable     true}
               "bob.entities" {:exclusive   false
                               :auto-delete false
                               :durable     true}}
   :bindings  {"bob.jobs"     "bob.direct"
               "bob.entities" "bob.direct"}})

(def system (atom nil))

(defn start
  []
  (let [db        (component/start (sys/db))
        queue     (component/start
                    (sys/queue queue-conf))
        apiserver (component/start (APIServer. db queue api-host api-port))]
    (reset! system [db queue apiserver])))

(defn stop
  []
  (when @system
    (run! component/stop (reverse @system))
    (reset! system nil)))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
