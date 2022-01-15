#!/usr/bin/env bb

; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(require '[clojure.test :as t]
         '[babashka.classpath :as cp]
         '[babashka.process :as p]
         '[org.httpkit.client :as http])

(p/sh "docker-compose up -d")

(defn wait-for-healthy
  ([]
   (wait-for-healthy 1000))
  ([wait]
   (println (format "Waiting for %dms" wait))
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

(p/sh "docker-compose kill")
(p/sh "docker-compose rm -f")

(System/exit failures-and-errors)
