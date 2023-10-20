; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.main
  (:require
   [apiserver.system :as system]
   [clojure.tools.logging :as log])
  (:gen-class))

(defn shutdown!
  []
  (log/info "Received SIGINT, Shutting down ...")
  (system/stop)
  (shutdown-agents)
  (log/info "Shutdown complete."))

(defn -main
  [& _]
  (.addShutdownHook (Runtime/getRuntime)
                    (.unstarted (Thread/ofVirtual) shutdown!))
  (system/start))
