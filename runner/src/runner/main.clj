; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.main
  (:require [clojure.repl :as repl]
            [taoensso.timbre :as log]
            [runner.system :as system])
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
  (repl/set-break-handler! shutdown!)
  (system/start))
