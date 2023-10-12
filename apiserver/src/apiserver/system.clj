; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.system
  (:require
   [aero.core :as aero]
   [apiserver.server :as s]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [common.system :as cs]
   [integrant.core :as ig]
   [s-exp.mina :as m]))

(defmethod ig/init-key
  :bob/apiserver
  [_ {:keys [host port health-check-freq database queue stream]}]
  (log/info "Starting APIServer")
  ;; See: https://github.com/mpenet/mina/issues/11
  (let [server (m/start! (s/server database (:chan queue) (:conn-opts queue) health-check-freq stream)
                         {:host host
                          :port port})]
    (log/infof "Listening on %d" port)
    server))

(defmethod ig/halt-key!
  :bob/apiserver
  [_ server]
  (log/info "Stopping APIServer")
  (m/stop! server))

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
  (aero/read-config (io/resource "bob/conf.edn"))

  (reset))
