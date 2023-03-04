; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.main
  (:require
   [clojure.repl :as repl]
   [runner.system :as system]
   [clojure.tools.logging :as log])
  (:import
   [java.util.concurrent Executors])
  (:gen-class))

(defn shutdown!
  [& _]
  (log/info "Received SIGINT, Shutting down ...")
  (system/stop)
  (shutdown-agents)
  (log/info "Shutdown complete.")
  (System/exit 0))

(defn -main
  [& _]
  ; Replace future-call's executor with virtual threads
  (set-agent-send-off-executor! (Executors/newVirtualThreadPerTaskExecutor))
  (repl/set-break-handler! shutdown!)
  (system/start))
