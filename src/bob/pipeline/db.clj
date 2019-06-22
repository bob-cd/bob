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

(ns bob.pipeline.db
  (:require [hugsql.core :as sql]
            [clojure.java.io :as io]))

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

  (insert-step-sqlvec {:cmd               "test"
                       :needs_resource    "test"
                       :produces_artifact "crap"
                       :artifact_path     "/some"
                       :pipeline          "best"})

  (delete-pipeline-sqlvec {:name "test"})

  (ordered-steps-sqlvec {:pipeline "test"})

  (evars-by-pipeline-sqlvec {:pipeline "test"})

  (status-of-sqlvec {:pipeline "test"
                     :number   1})

  (delete-pipeline-sqlvec {:name "test"})

  (running-pipelines-sqlvec)

  (insert-log-entry-sqlvec {:pid "aaa" :run "abcd"})

  (update-runs-sqlvec {:pid "aaa" :id "aaaaa"})

  (run-stopped?-sqlvec {:id "aaa"})

  (pipeline-runs-sqlvec {:pipeline "test"})

  (insert-run-sqlvec {:id       "id"
                      :number   1
                      :pipeline "test"
                      :status   "test"})

  (update-run-sqlvec {:status "test" :id 1})

  (stop-run-sqlvec {:pipeline "test" :number 1})

  (pid-of-run-sqlvec {:pipeline "test" :number 1})

  (run-id-of-sqlvec {:pipeline "test" :number 1})

  (container-ids-sqlvec {:run-id "aa"})

  (image-of-sqlvec {:name "aa"}))
