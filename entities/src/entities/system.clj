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

(ns entities.system
  (:require [com.stuartsierra.component :as component]
            [common.system :as sys]
            [entities.dispatch :as d]))

(defn queue-conf
  [db]
  {:exchanges     {"bob.direct" {:type    "direct"
                                 :durable true}}
   :queues        {"bob.errors"   {:exclusive   false
                                   :auto-delete false
                                   :durable     true}
                   "bob.entities" {:exclusive   false
                                   :auto-delete false
                                   :durable     true}}
   :bindings      {"bob.entities" "bob.direct"}
   :subscriptions {"bob.entities" (partial d/queue-msg-subscriber (sys/db-client db))}})

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
