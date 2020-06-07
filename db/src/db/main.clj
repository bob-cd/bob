;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns db.main
  (:require [clojure.repl :as repl]
            [taoensso.timbre :as log]
            [db.system :as sys])
  (:gen-class))

(defn shutdown!
  [& _]
  (log/info "Received SIGINT, Shutting down ...")
  (sys/stop)
  (shutdown-agents)
  (log/info "Shutdown complete.")
  (System/exit 0))

(defn -main
  [& _]
  (repl/set-break-handler! shutdown!)
  (sys/start))
