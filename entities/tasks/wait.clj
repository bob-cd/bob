; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

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
