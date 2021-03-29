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

(ns apiserver.healthcheck
  (:require [failjure.core :as f]
            [crux.api :as crux]
            [taoensso.timbre :as log])
  (:import [java.util.concurrent Executors TimeUnit]))

(defn queue
  [{:keys [queue]}]
  (when (not (.isOpen queue))
    (f/fail "Queue is unreachable")))

;; TODO: Better health check
(defn db
  [{:keys [db]}]
  (when (not (crux/status db))
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
  (let [health-check-cron #(check {:queue queue
                                      :db    database})
        scheduler         (Executors/newScheduledThreadPool 1)]
    (.scheduleAtFixedRate scheduler health-check-cron 0 health-check-freq TimeUnit/MILLISECONDS)))
