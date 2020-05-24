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

(ns entities.pipeline.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [entities.util :as u]
            [entities.pipeline.core :as p]))

(deftest ^:integration pipleine
  (testing "creation"
    (u/with-db
      #(let [pipeline                {:group     "test"
                                      :name      "test"
                                      :steps     [{:cmd "echo hello"}
                                                  {:needs_resource "source"
                                                   :cmd            "ls"}
                                                  {:cmd               "touch test"
                                                   :produces_artifact {:name  "file"
                                                                       :path  "test"
                                                                       :store "s3"}}]
                                      :vars      {:k1 "v1"
                                                  :k2 "v2"}
                                      :resources [{:name     "source"
                                                   :type     "external"
                                                   :provider "git"
                                                   :params   {:repo   "https://github.com/bob-cd/bob"
                                                              :branch "master"}}
                                                  {:name     "source2"
                                                   :type     "external"
                                                   :provider "git"
                                                   :params   {:repo   "https://github.com/lispyclouds/clj-docker-client"
                                                              :branch "master"}}]
                                      :image     "busybox:musl"}
             create-res              (p/create % pipeline)
             pipeline-effect         (first (u/sql-exec! % "SELECT * FROM pipelines"))
             steps-effect            (u/sql-exec! % "SELECT * FROM steps")
             evars-effect            (u/sql-exec! % "SELECT * FROM evars")
             resource-params-effects (u/sql-exec! % "SELECT * FROM resource_params")
             resources-effects       (u/sql-exec! % "SELECT * FROM resources")]
         (is (= "Ok" create-res))
         (is (= {:name  "test:test"
                 :image "busybox:musl"}
                pipeline-effect))
         (is (= [{:id                1
                  :cmd               "echo hello"
                  :pipeline          "test:test"
                  :needs_resource    nil
                  :produces_artifact nil
                  :artifact_path     nil
                  :artifact_store    nil}
                 {:id                2
                  :cmd               "ls"
                  :pipeline          "test:test"
                  :needs_resource    "source"
                  :produces_artifact nil
                  :artifact_path     nil
                  :artifact_store    nil}
                 {:id                3
                  :cmd               "touch test"
                  :pipeline          "test:test"
                  :needs_resource    nil
                  :produces_artifact "file"
                  :artifact_path     "test"
                  :artifact_store    "s3"}]
                steps-effect))
         (is (= [{:id       1
                  :key      "k1"
                  :value    "v1"
                  :pipeline "test:test"}
                 {:id       2
                  :key      "k2"
                  :value    "v2"
                  :pipeline "test:test"}]
                evars-effect))
         (is (= [{:name     "source"
                  :key      "repo"
                  :value    "https://github.com/bob-cd/bob"
                  :pipeline "test:test"}
                 {:name     "source"
                  :key      "branch"
                  :value    "master"
                  :pipeline "test:test"}
                 {:name     "source2"
                  :key      "repo"
                  :value    "https://github.com/lispyclouds/clj-docker-client"
                  :pipeline "test:test"}
                 {:name     "source2"
                  :key      "branch"
                  :value    "master"
                  :pipeline "test:test"}]
                resource-params-effects))
         (is (= [{:name     "source"
                  :type     "external"
                  :provider "git"
                  :pipeline "test:test"}
                 {:name     "source2"
                  :type     "external"
                  :provider "git"
                  :pipeline "test:test"}]
                resources-effects)))))
  (testing "deletion"
    (u/with-db
      (fn [conn]
        (let [pipeline   {:name  "test"
                          :group "test"}
              delete-res (p/delete conn pipeline)
              effects    (->> ["pipelines" "steps" "evars" "resource_params" "resources"]
                              (map #(format "SELECT * FROM %s" %))
                              (map #(u/sql-exec! conn %)))]
          (is (= "Ok" delete-res))
          (is (every? empty? effects)))))))
