; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.system
  (:require
   [aero.core :as aero]
   [apiserver.healthcheck :as hc]
   [apiserver.server :as s]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [common.heartbeat :as hb]
   [common.system :as cs]
   [integrant.core :as ig]
   [s-exp.hirundo :as srv])
  (:import
   [java.util.concurrent Executors Future]))

(defmethod ig/init-key
  :bob/apiserver
  [_ {:keys [host port database queue stream]}]
  (log/info "Starting APIServer")
  (let [server (srv/start! {:http-handler (s/server database
                                                    (:chan queue)
                                                    (:conn-opts queue)
                                                    stream)
                            :host host
                            :port port})]
    (log/infof "Listening on %d" port)
    server))

(defmethod ig/halt-key!
  :bob/apiserver
  [_ server]
  (log/info "Stopping APIServer")
  (srv/stop! server))

(defmethod ig/init-key
  :bob/apiserver-heartbeat
  [_ {:keys [queue db freq]}]
  (hb/schedule #(hb/beat-it db queue :bob/node-type :apiserver)
               "heartbeat"
               freq))

(defmethod ig/halt-key!
  :bob/apiserver-heartbeat
  [_ task]
  (Future/.cancel task true))

(defmethod ig/init-key
  :bob/apiserver-healthcheck
  [_ {:keys [queue db freq]}]
  (hb/schedule #(hc/check (Executors/newVirtualThreadPerTaskExecutor) {:queue (:chan queue) :db db})
               "healthcheck"
               freq))

(defmethod ig/halt-key!
  :bob/apiserver-healthcheck
  [_ task]
  (Future/.cancel task true))

(defonce system nil)

(defn start
  []
  (alter-var-root #'system
                  (constantly (-> "bob/conf.edn"
                                  (io/resource)
                                  (aero/read-config {:resolver cs/resource-resolver})
                                  (dissoc :common)
                                  (ig/init)))))

(defn stop
  []
  (alter-var-root #'system #(when % (ig/halt! %))))

(defn reset
  []
  (stop)
  (start))

(comment
  (set! *warn-on-reflection* true)

  (aero/read-config (io/resource "bob/conf.edn"))

  (reset))
