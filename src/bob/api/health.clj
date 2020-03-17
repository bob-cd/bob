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

(ns bob.api.health
  (:require [clojure.java.io :as io]
            [clojure.core.async :as a]
            [hugsql.core :as sql]
            [clj-docker-client.core :as docker]
            [manifold.deferred :as d]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [aleph.http :as http]
            [chime :refer [chime-ch]]
            [clj-time.core :as t]
            [clj-time.periodic :as tp]
            [bob.artifact.db :refer [get-artifact-stores]]
            [bob.resource.db :refer [get-external-resources]]
            [bob.states :as states]
            [bob.util :as u]))

(sql/def-db-fns (io/resource "sql/health.sql"))

(defn ping-external-systems []
  (let [external-systems (concat (get-artifact-stores states/db) (get-external-resources states/db))]
    (map #(when (f/failed? (f/try* @(http/get (clojure.string/join "/" [(:url %) "ping"])))) (:name %))
         external-systems)))

(defn health-check
  "Check the systems we depend upon. Returns nil if everything is alright, else returns a sequence
   of strings naming the failing systems."
  []
  (let [docker      (when (not (= "OK" (docker/invoke {:category :_ping
                                                       :conn states/conn}
                                                      {:op :SystemPing})))
                      ["Docker"])
        postgres    (when (f/failed? (f/try* (db-health-check states/db)))
                      ["Postgres"])
        extsys      (when (nil? postgres)
                      (ping-external-systems))]
    (filter some? (concat docker postgres extsys))))

(defn respond-to-health-check
  "Endpoint for answering a health check"
  []
  (d/let-flow [failures (health-check)]
              (if (empty? failures)
                (u/respond "Yes we can! \uD83D\uDD28 \uD83D\uDD28")
                (u/service-unavailable (str "Health check failed: " (clojure.string/join " and " failures) " not healthy")))))

(defn log-health-check
  "Logs if any of the subsystems is unhealthy."
  []
  (let [failures (health-check)]
    (if (empty? failures)
      (log/debugf (str "Health check succeeded!"))
      (log/warn (str "Health check failed: " (clojure.string/join " and " failures) " not healthy")))))

(defn start-heartbeat
  "Starts a periodic heatbeat which performs a health check."
  []
  (let [_      (log/debugf "Starting Heartbeat")
        chimes (chime-ch (rest (tp/periodic-seq (t/now) (-> 1 t/minutes))))]
    (a/go-loop []
      (when-let [_ (a/<! chimes)]
        (log-health-check)
        (recur)))
    chimes))

(defn stop-heartbeat
  "Stops the periodic heartbeat."
  [heartbeat]
  (log/debug "Stopping Heartbeat")
  (a/close! heartbeat))

(comment
  (docker/categories)
  (def ping (docker/client {:category :_ping :conn states/conn}))
  (docker/ops (docker/client {:category :_ping :conn states/conn}))
  (docker/invoke ping {:op :SystemPing})
  (f/failed? (f/try* (docker/invoke ping {:op :SystemPing})))

  (when (nil? nil) (concat (get-artifact-stores states/db) (get-external-resources states/db)))

  (map #(when (f/failed? (f/try* @(http/get (clojure.string/join "/" [(:url %) "ping"]) {:throw-exceptions false}))) (:name %))
       (concat (get-artifact-stores states/db) (get-external-resources states/db)))

  (ping-external-systems)

  (health-check)

  (log-health-check)

  (list (first (tp/periodic-seq (t/now) (-> 1 t/minutes))))

  (let [_      (log/debugf "Starting Heartbeat")
        chimes (let [chan (a/chan 1)] (a/>!! chan "foobar") chan)]
    (a/go-loop []
      (when-let [msg (a/<! chimes)]
        (prn "Foobar!")
        (recur)))
    chimes)

  (start-heartbeat))
