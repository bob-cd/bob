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

(ns runner.pipeline.db
  (:require [clojure.java.io :as io]
            [hugsql.core :as sql]
            [hugsql.adapter.next-jdbc :as adapter]))

(sql/set-adapter! (adapter/hugsql-adapter-next-jdbc))

(sql/def-db-fns (io/resource "sql/pipeline.sql"))

(comment
  (sql/def-sqlvec-fns (io/resource "sql/pipeline.sql"))

  (ordered-steps-sqlvec {:pipeline "test"})

  (evars-by-pipeline-sqlvec {:pipeline "test"})

  (update-runs-sqlvec {:pid "aaa" :id "aaaaa"})

  (pipeline-runs-sqlvec {:pipeline "test"})

  (insert-run-sqlvec {:id       "id"
                      :number   1
                      :pipeline "test"
                      :status   "test"})

  (update-run-sqlvec {:status "test" :id 1})

  (stop-run-sqlvec {:pipeline "test" :number 1})

  (pid-of-run-sqlvec {:pipeline "test" :number 1})

  (run-id-of-sqlvec {:pipeline "test" :number 1})

  (image-of-sqlvec {:name "aa"}))
