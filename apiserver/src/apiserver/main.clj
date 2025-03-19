; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.main
  (:require
   [apiserver.system :as system]
   [clojure.tools.logging :as log])
  (:gen-class)
  (:import
   [java.util.concurrent Executors]))

(defn shutdown!
  []
  (log/info "Received SIGINT, Shutting down ...")
  (system/stop)
  (shutdown-agents)
  (log/info "Shutdown complete."))

(defn -main
  [& _]
  ; Replace future-call's executor with virtual threads
  (set-agent-send-off-executor! (Executors/newVirtualThreadPerTaskExecutor))
  (.addShutdownHook (Runtime/getRuntime)
                    (.unstarted (Thread/ofVirtual) shutdown!))
  (system/start))
