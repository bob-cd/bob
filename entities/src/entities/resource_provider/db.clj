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

(ns entities.resource-provider.db
  (:require [clojure.java.io :as io]
            [hugsql.core :as sql]
            [hugsql.adapter.next-jdbc :as adapter]))

(sql/set-adapter! (adapter/hugsql-adapter-next-jdbc))

(sql/def-db-fns (io/resource "sql/resource.sql"))

(comment
  (sql/def-sqlvec-fns (io/resource "sql/resource.sql"))

  (register-resource-provider-sqlvec {:name "github"
                                      :url  "http://localhost:8000"})

  (un-register-resource-provider-sqlvec {:name "github"}))
