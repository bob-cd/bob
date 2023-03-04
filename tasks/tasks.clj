; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns tasks
  (:require
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as process])
  (:import
   [java.net Socket URI]))

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

(defn container-engine
  []
  (let [engine (->> ["podman" "docker"]
                    (map fs/which)
                    (some (fn [e] (when e e))))]
    (or engine (throw (ex-info "Podman or Docker not found." {:babashka/exit 1})))))

(defn crun
  ([cmd]
   (crun cmd {}))
  ([cmd opts]
   (process/shell (merge {:out :inherit :in :inherit :err :inherit} opts) (str (container-engine) " " cmd))))

(defn scrun
  [cmd]
  (crun cmd {:continue true}))
