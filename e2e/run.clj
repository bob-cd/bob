#!/usr/bin/env bb

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

(require '[clojure.test :as t]
         '[babashka.classpath :as cp]
         '[org.httpkit.client :as http])

(defn wait-for-healthy
  ([]
   (wait-for-healthy 1000))
  ([wait]
   (println (format "Waiting for %d seconds" wait))
   (Thread/sleep wait)
   (try
     (let [status (:status @(http/get "http://localhost:7777/can-we-build-it"))]
       (when-not (= status 200)
         (println "Server found but not ready, retrying.")
         (wait-for-healthy (+ 200 wait))))
     (catch Exception e
       (println (format "Connection error: %s, retrying." (.getMessage e)))
       (wait-for-healthy (+ 200 wait))))))

(println "Waiting for bob to start up on localhost:7777")
(wait-for-healthy)
(println "Found bob")

(cp/add-classpath "end-to-end-tests")

(require 'tests)
(def test-results
  (t/run-tests 'tests))

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))

(System/exit failures-and-errors)
