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
            [clojure.string :as str]
            [korma.db :refer [defdb]]
            [korma.core :refer [select fields]]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :refer [migrate]]
            [hikari-cp.core :refer [make-datasource]]
            [bob.db.core :refer [pipelines steps artifacts resources resource-params]]
            [bob.pipeline.core :refer [create]]
            [bob.util :refer [clob->str]]))

(def db-uri "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")

(def data-source
  (make-datasource {:adapter "h2"
                    :url     db-uri}))

(def migration-config
  {:datastore  (jdbc/sql-database {:connection-uri db-uri})
   :migrations (jdbc/load-resources "migrations")})

(def valid-steps ["echo 1 >> state.txt"
                  "echo 2 >> state.txt"
                  "echo 3 >> state.txt"
                  "cat state.txt"])

(def valid-artifacts {:test-jar "/path/to/jar"})

(def valid-resources [{:name     "source"
                       :provider "git"
                       :params   {:url    "https://test.com"
                                  :branch "master"}
                       :type     "external"}])

(deftest create-test
  (testing "Creating a valid pipeline"
    (migrate migration-config)
    (defdb _
      {:datasource data-source
       :naming     {:keys   str/lower-case
                    :fields str/upper-case}})
    @(create "dev" "test" valid-steps [] valid-artifacts valid-resources "test:image")
    (is (= (first (select pipelines)) {:image "test:image", :name "dev:test"}))
    (is (= (first (select artifacts
                          (fields :name :path :pipeline)))
           {:name "test-jar" :path "/path/to/jar" :pipeline "dev:test"}))
    (is (= (first (select resources))
           {:name     "source"
            :provider "git"
            :type     "external"
            :pipeline "dev:test"}))
    (is (= (select resource-params)
           [{:name     "source"
             :key      "url"
             :value    "https://test.com"
             :pipeline "dev:test"}
            {:name     "source"
             :key      "branch"
             :value    "master"
             :pipeline "dev:test"}]))
    (is (= (->> (select steps)
                (map #(update-in % [:cmd] clob->str)))
           (list {:cmd "echo 1 >> state.txt" :id 1 :pipeline "dev:test"}
                 {:cmd "echo 2 >> state.txt" :id 2 :pipeline "dev:test"}
                 {:cmd "echo 3 >> state.txt" :id 3 :pipeline "dev:test"}
                 {:cmd "cat state.txt" :id 4 :pipeline "dev:test"})))))
