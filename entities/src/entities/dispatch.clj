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

(ns entities.dispatch
  (:require [taoensso.timbre :as log]
            [jsonista.core :as json]
            [entities.pipeline.core :as pipeline]))

(def ^:private routes
  {:pipeline/create pipeline/create
   :pipeline/delete pipeline/delete})

(defn route
  [db-conn message]
  (log/debugf "Routing message: %s" message)
  (let [msg-type  (keyword (:type message))
        routed-fn (msg-type routes)]
    (if routed-fn
      (routed-fn db-conn (:payload message))
      (log/errorf "Unknown message type: %s" msg-type))))

(def mapper (json/object-mapper {:decode-key-fn true}))

(defn queue-msg-subscriber
  [db-conn _chan meta-data payload]
  (let [payload (json/read-value payload mapper)]
    (log/infof "payload %s" payload)
    (log/infof "meta %s" meta-data)
    (route db-conn
           {:type    (-> meta-data
                         :type
                         keyword)
            :payload payload})))
