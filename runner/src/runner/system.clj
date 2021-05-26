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

(ns runner.system
  (:require [com.stuartsierra.component :as component]
            [common.system :as sys]
            [common.dispatch :as d]
            [runner.pipeline :as p])
  (:import [java.util UUID]))

(def ^:private routes
  {"pipeline/start"   p/start
   "pipeline/stop"    p/stop
   "pipeline/pause"   p/pause
   "pipeline/unpause" p/unpause})

(defn queue-conf
  [db]
  (let [broadcast-queue (str "bob.broadcasts." (UUID/randomUUID))
        subscriber      (partial d/queue-msg-subscriber (sys/db-client db) routes)]
    {:exchanges     {"bob.direct" {:type    "direct"
                                   :durable true}
                     "bob.fanout" {:type    "fanout"
                                   :durable true}}
     :queues        {"bob.jobs"      {:exclusive   false
                                      :auto-delete false
                                      :durable     true}
                     "bob.errors"    {:exclusive   false
                                      :auto-delete false
                                      :durable     true}
                     broadcast-queue {:exclusive   true
                                      :auto-delete true
                                      :durable     true}}
     :bindings      {"bob.jobs"      "bob.direct"
                     broadcast-queue "bob.fanout"}
     :subscriptions {"bob.jobs"      subscriber
                     broadcast-queue subscriber}}))

(def system (atom nil))

(defn start
  []
  (let [db    (component/start (sys/db))
        queue (component/start
                (sys/queue (queue-conf db)))]
    (reset! system [db queue])))

(defn stop
  []
  (when @system
    (run! component/stop (reverse @system))
    (reset! system nil)))

(defn reset
  []
  (stop)
  (start))

(comment
  (reset))
