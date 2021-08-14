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
;   You should have received a copy of the GNU Affero Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns wait
  (:require [babashka.process :as proc]))

(defn wait-for-tcp
  [service host port]
  (println "Waiting for" service)
  (proc/sh (format "wait-for %s:%d -t 60" host port))
  (println service "connected."))

(println "Waiting for cluster readiness.")

(wait-for-tcp "postgres" "localhost" 5433)
(wait-for-tcp "rabbitmq" "localhost" 5673)
