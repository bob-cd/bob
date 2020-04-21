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

(ns entities.pipeline.db
  (:require [clojure.java.io :as io]
            [hugsql.core :as sql]
            [hugsql.adapter.next-jdbc :as adapter]))

(sql/set-adapter! (adapter/hugsql-adapter-next-jdbc))

(sql/def-db-fns (io/resource "sql/pipeline.sql"))

(comment
  (sql/def-sqlvec-fns (io/resource "sql/pipeline.sql"))

  (insert-pipeline-sqlvec {:name  "dev/test"
                           :image "busybox:musl"})

  (insert-evars-sqlvec {:evars [{:key      "test"
                                 :value    "test"
                                 :pipeline "test"}
                                {:key      "test1"
                                 :value    "test1"
                                 :pipeline "test"}]})

  (delete-pipeline-sqlvec {:name "test"})

  (delete-pipeline-sqlvec {:name "test"}))
