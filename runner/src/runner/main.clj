; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.main
  (:require [clojure.repl :as repl]
            [taoensso.timbre :as log]
            [runner.system :as system])
  (:import [clojure.lang Agent]
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
  (set! Agent/soloExecutor (Executors/newVirtualThreadPerTaskExecutor))
  (repl/set-break-handler! shutdown!)
  (system/start))
