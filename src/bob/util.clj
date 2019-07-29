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
  (:require [ring.util.http-response :as res])
  (:import (java.sql Clob)
           (java.util UUID)))

(def id-length 12)

(defn respond
  "Simple decorator for wrapping a message in the response format."
  [msg]
  (res/ok {:message msg}))

(defn format-id
  "Return docker container ids in the standard length"
  [^String id]
  (subs id 0 id-length))

(defn clob->str
  "Transforms a Java Clob object to a Java String"
  [^Clob clob]
  (.getSubString clob 1 (int (.length clob))))

(defn get-id [] (str (UUID/randomUUID)))

(defn name-of
  [group name]
  (format "%s:%s" group name))
