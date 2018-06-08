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
            [clojure.java.io :as io]
            [korma.db :refer [defdb]]
            [korma.core :refer [select fields]]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :refer [migrate]]
            [bob.pipeline.core :refer [create pipelines steps]])
  (:import (java.io File)))

(def test-db ^File (File/createTempFile "test" ".db"))

(defonce test-db-spec
         {:classname   "org.h2.Driver"
          :subprotocol "h2:file"
          :subname     (.getAbsolutePath test-db)})

(def migration-config
  {:datastore  (jdbc/sql-database test-db-spec)
   :migrations (jdbc/load-resources "migrations")})

(def valid-steps ["echo 1 >> state.txt"
                  "echo 2 >> state.txt"
                  "echo 3 >> state.txt"
                  "cat state.txt"])

(deftest create-test
  (testing "Creating a valid pipeline"
    (defdb _ test-db-spec)
    (migrate migration-config)
    (create "dev" "test" valid-steps "test:image")
    (is (= (first (select pipelines)) {:IMAGE "test:image", :NAME "dev:test"}))
    (is (= (select steps) (list {:CMD "echo 1 >> state.txt" :ID 1 :PID nil :PIPELINE "dev:test"}
                                {:CMD "echo 2 >> state.txt" :ID 2 :PID nil :PIPELINE "dev:test"}
                                {:CMD "echo 3 >> state.txt" :ID 3 :PID nil :PIPELINE "dev:test"}
                                {:CMD "cat state.txt" :ID 4 :PID nil :PIPELINE "dev:test"})))))
