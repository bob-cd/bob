; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

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

(wait-for "podman" "http://localhost:8080/v3.4.1/libpod/_ping")
(wait-for "resource-git" "http://localhost:8000/ping")
(wait-for "artifact-local" "http://localhost:8001/ping")
(wait-for-tcp "postgres" "localhost" 5433)
(wait-for-tcp "rabbitmq" "localhost" 5673)
