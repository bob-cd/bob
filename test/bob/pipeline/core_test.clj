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

(ns bob.pipeline.core-test
  (:require [clojure.test :refer :all]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :refer [migrate]]
            [hikari-cp.core :refer [make-datasource]]
            [hugsql.core :as hsql]
            [bob.states :as states]
            [bob.pipeline.core :refer [create]]
            [bob.util :refer [clob->str]]))

(def db-uri "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")

(def data-source
  (make-datasource {:adapter "h2"
                    :url     db-uri}))

(def migration-config
  {:datastore  (jdbc/sql-database {:connection-uri db-uri})
   :migrations (jdbc/load-resources "migrations")})

(def valid-steps [{:cmd "echo 1 >> state.txt"}
                  {:cmd "echo 2 >> state.txt"}
                  {:cmd "echo 3 >> state.txt"}
                  {:cmd               "cat state.txt"
                   :produces_artifact {:name "afile"
                                       :path "target/file"}}
                  {:needs_resource "source"
                   :cmd            "ls"}])

(def valid-resources [{:name     "source"
                       :provider "git"
                       :params   {:url    "https://test.com"
                                  :branch "master"}
                       :type     "external"}])

(def db {:datasource data-source})

(hsql/def-db-fns "bob/pipeline/test.sql")

(deftest create-test
  (testing "Creating a valid pipeline"
    (migrate migration-config)

    (with-redefs [states/db db]
      @(create "dev" "test" valid-steps {} valid-resources "test:image"))

    (is (= {:image "test:image", :name "dev:test"}
           (first (all-pipelines db))))

    (is (= {:name     "source"
            :provider "git"
            :type     "external"
            :pipeline "dev:test"}
           (first (all-resources db))))

    (is (= [{:name     "source"
             :key      "url"
             :value    "https://test.com"
             :pipeline "dev:test"}
            {:name     "source"
             :key      "branch"
             :value    "master"
             :pipeline "dev:test"}]
           (all-resource-params db)))

    (is (= [{:cmd               "echo 1 >> state.txt"
             :id                1
             :pipeline          "dev:test"
             :needs_resource    nil
             :produces_artifact nil
             :artifact_path     nil}
            {:cmd               "echo 2 >> state.txt"
             :id                2
             :pipeline          "dev:test"
             :needs_resource    nil
             :produces_artifact nil
             :artifact_path     nil}
            {:cmd               "echo 3 >> state.txt"
             :id                3
             :pipeline          "dev:test"
             :needs_resource    nil
             :produces_artifact nil
             :artifact_path     nil}
            {:cmd               "cat state.txt"
             :id                4
             :pipeline          "dev:test"
             :needs_resource    nil
             :produces_artifact "afile"
             :artifact_path     "target/file"}
            {:cmd               "ls"
             :id                5
             :pipeline          "dev:test"
             :needs_resource    "source"
             :produces_artifact nil
             :artifact_path     nil}]
           (->> (all-steps db)
                (map #(update-in % [:cmd] clob->str)))))))
