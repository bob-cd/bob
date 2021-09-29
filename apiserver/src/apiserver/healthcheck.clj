; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.healthcheck
  (:require [failjure.core :as f]
            [xtdb.api :as xt]
            [taoensso.timbre :as log])
  (:import [java.util.concurrent Executors TimeUnit]))

(defn queue
  [{:keys [queue]}]
  (when (not (.isOpen queue))
    (f/fail "Queue is unreachable")))

;; TODO: Better health check
(defn db
  [{:keys [db]}]
  (when (not (xt/status db))
    (f/fail "DB is unhealthy")))

(defn check
  [opts]
  (let [results (->> [queue db]
                     (pmap #(% opts))
                     (filter #(f/failed? %)))]
    (when (seq results)
      (run! #(log/errorf "Health checks failing: %s" (f/message %)) results)
      (f/fail results))))

(defn schedule
  [queue database health-check-freq]
  (.scheduleAtFixedRate (Executors/newScheduledThreadPool 1)
                        #(check {:queue queue :db database})
                        0
                        health-check-freq
                        TimeUnit/MILLISECONDS))
