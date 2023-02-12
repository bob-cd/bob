; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns wait
  (:require [babashka.http-client :as http])
  (:import [java.net Socket URI]))

(defn retry
  [service logic interval backoff retries]
  (println (format "Waiting %dms for %s. %d retries remaining."
                   interval
                   service
                   retries))
  (Thread/sleep interval)
  (try
    (logic)
    (println service "connected.")
    (catch Exception _
      (when (zero? retries)
        (println service "timed out.")
        (System/exit 1))
      (retry service logic (+ interval backoff) backoff (dec retries)))))

(defn wait-for
  [conf]
  (doseq [[service url] conf]
    (let [uri (URI. url)
          logic (case (.getScheme uri)
                  "http" #(http/get url)
                  "tcp" #(doto (Socket. (.getHost uri) (.getPort uri))
                           (.close)))]
      (retry service logic 1000 200 20))))
