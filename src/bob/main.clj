;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.main
  (:require [mount.core :as m]
            [aleph.http :as http]
            [clojure.repl :as repl]
            [taoensso.timbre :as log]
            [bob.states :refer :all]
            [bob.api.health :as health]
            [bob.api.routes :as routes])
  (:gen-class))

(def PORT 7777)

;; TODO: These states are here to avoid a cyclic import, figure out a better way.
(m/defstate server
  :start (do (log/infof "Bob's listening on http://0.0.0.0:%d/" PORT)
             (http/start-server routes/bob-api {:port PORT}))
  :stop  (do (log/info "Stopping HTTP")
             (.close server)))

(m/defstate heartbeat
  :start (health/start-heartbeat)
  :stop (health/stop-heartbeat heartbeat))

(defn shutdown!
  [_]
  (log/info "Bob's shutting down")
  (m/stop)
  (shutdown-agents)
  (System/exit 0))

(defn -main
  "Defines the entry point of Bob.
  Starts up the HTTP API on port *7777* by default."
  [& _]
  (repl/set-break-handler! shutdown!)
  (m/start))
