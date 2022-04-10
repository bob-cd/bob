; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.system
  (:require [integrant.core :as ig]
            [environ.core :as env]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :as jetty]
            [common.system :as sys]
            [apiserver.server :as s])
  (:import [org.eclipse.jetty.server Server]))

(defonce storage-url (:bob-storage-url env/env "jdbc:postgresql://localhost:5432/bob"))
(defonce storage-user (:bob-storage-user env/env "bob"))
(defonce storage-password (:bob-storage-password env/env "bob"))

(defonce queue-url (:bob-queue-url env/env "amqp://localhost:5672"))
(defonce queue-user (:bob-queue-user env/env "guest"))
(defonce queue-password (:bob-queue-password env/env "guest"))

(defonce api-host (:bob-api-host env/env "0.0.0.0"))
(defonce api-port (sys/int-from-env :bob-api-port 7777))
(defonce health-check-freq (sys/int-from-env :bob-health-check-freq 60000))

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

(def config
  {:bob/storage   {:url      storage-url
                   :user     storage-user
                   :password storage-password}
   :bob/queue     {:url      queue-url
                   :user     queue-user
                   :password queue-password
                   :conf     queue-conf}
   :bob/apiserver {:host              api-host
                   :port              api-port
                   :health-check-freq health-check-freq
                   :database          (ig/ref :bob/storage)
                   :queue             (ig/ref :bob/queue)}})

(defmethod ig/init-key
  :bob/apiserver
  [_ {:keys [host port health-check-freq database queue]}]
  (log/info "Starting APIServer")
  (let [server (jetty/run-jetty (s/server database (:chan queue) health-check-freq)
                                {:host                 host
                                 :port                 port
                                 :join?                false
                                 :async?               true
                                 :send-server-version? false
                                 :send-date-header?    false})]
    (log/infof "Listening on %d" port)
    server))

(defmethod ig/halt-key!
  :bob/apiserver
  [_ ^Server server]
  (log/info "Stopping APIServer")
  (.stop server))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (ig/init config))))

(defn stop
  []
  (alter-var-root #'system
                  #(when %
                     (ig/halt! %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
