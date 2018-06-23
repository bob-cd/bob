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
  (:require [ring.util.response :refer [response]])
  (:import (java.sql Clob)))

(def id-length 12)

(defmacro perform!
  [& body]
  `(try
     ~@body
     (catch Exception e# e#)))

(defn respond
  [msg]
  (response {:message msg}))

(defn format-id
  [^String id]
  (subs id 0 id-length))

(defn clob->str
  [^Clob clob]
  (.getSubString clob 1 (int (.length clob))))
