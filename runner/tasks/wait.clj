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
  (:require [org.httpkit.client :as http]
            [babashka.process :as proc]))

(defn wait-for
  ([service url]
   (wait-for service url 1000 200))
  ([service url interval backoff]
   (println (format "Waiting for %dms for: %s" interval service))
   (Thread/sleep interval)
   (try
     (let [status (:status @(http/get url))]
       (if-not (= status 200)
         (wait-for service url (+ backoff interval) backoff)
         (println service "connected.")))
     (catch Exception _
       (wait-for service url (+ backoff interval) backoff)))))

(defn wait-for-tcp
  [service host port]
  (println "Waiting for" service)
  (proc/sh (format "wait-for %s:%d -t 60" host port))
  (println service "connected."))

(println "Waiting for cluster readiness.")

(wait-for "podman" "http://localhost:8080/v3.2.3/libpod/_ping")
(wait-for "resource-git" "http://localhost:8000/ping")
(wait-for "artifact-local" "http://localhost:8001/ping")
(wait-for-tcp "postgres" "localhost" 5433)
(wait-for-tcp "rabbitmq" "localhost" 5673)
