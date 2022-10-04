; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.system
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [aero.core :as aero]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :as jetty]
            [common.system :as cs]
            [apiserver.server :as s])
  (:import [org.eclipse.jetty.server Server]))

; Ignore key
(defmethod ig/init-key
  :common
  [_ _])

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
                  (constantly (-> "bob/conf.edn"
                                  (io/resource)
                                  (aero/read-config {:resolver cs/resource-resolver})
                                  (ig/init)))))

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
  (aero/read-config (io/resource "bob/conf.edn"))

  (reset))
