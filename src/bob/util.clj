;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.util
  (:require [ring.util.http-response :as res]
            [bob.states :as states]
            [bob.pipeline.db :as db])
  (:import (java.util UUID)))

(def id-length 12)

(defn respond
  "Simple decorator for wrapping a message in the response format."
  [msg]
  (res/ok {:message msg}))

(defn service-unavailable
  "Decorator for returning code 503 Service Unavailable."
  [error]
  (res/service-unavailable {:message error}))

(defn format-id
  "Return docker container ids in the standard length"
  [^String id]
  (subs id 0 id-length))

(defn get-id [] (str (UUID/randomUUID)))

(defn name-of
  [group name]
  (format "%s:%s" group name))

(defn log-to-db
  [data run-id]
  (db/upsert-log states/db {:run     run-id
                            :content data}))
