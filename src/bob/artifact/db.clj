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

(ns bob.artifact.db
  (:require [hugsql.core :as sql]
            [clojure.java.io :as io]))

(sql/def-db-fns (io/resource "sql/artifact.sql"))

(comment
  (sql/def-sqlvec-fns (io/resource "sql/artifact.sql"))

  (register-artifact-store-sqlvec {:name "artifact/s3"
                                   :url  "http://localhost:8001"})

  (un-register-artifact-store-sqlvec {:name "artifact/s3"}))
