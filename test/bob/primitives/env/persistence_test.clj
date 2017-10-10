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

(ns bob.primitives.env.persistence-test
  (:require [clojure.test :refer :all]
            [ragtime.jdbc :as jdbc]
            [ragtime.repl :refer [migrate]]
            [korma.db :refer [defdb]]
            [bob.primitives.env.persistence :refer :all])
  (:import (bob.primitives.env.env Env)))

(defonce test-db-spec
         {:classname   "org.h2.Driver"
          :subprotocol "h2:mem"
          :subname     "test;DB_CLOSE_DELAY=-1"})

(def test-migration-config
  {:datastore  (jdbc/sql-database test-db-spec)
   :migrations (jdbc/load-resources "migrations")})

(migrate test-migration-config)

(defdb db-handle test-db-spec)

(deftest env-persistence-test
  (testing "Retrieve an Env from disk"
    (let [env (Env. "1" {:k1 "v1"})]
      (do (put-env env)
          (is (= env (get-env "1"))))))
  (testing "Delete an Env from disk"
    (is (= 1 (del-env "1"))))
  (testing "Retrieve non existent Env"
    (is (nil? (get-env "1")))))
