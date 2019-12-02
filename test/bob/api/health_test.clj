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

(ns bob.api.health-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [failjure.core :as f]
            [manifold.deferred :as d]
            [clj-docker-client.core :as docker]
            [chime :refer [chime-ch]]
            [bob.artifact.db :refer [get-artifact-stores]]
            [bob.resource.db :refer [get-external-resources]]
            [bob.test-utils :as tu]
            [bob.api.health :refer :all]))

(deftest health-check-various-conditions-test
  (testing "all systems operational"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly {:?column? true})
                     #'ping-external-systems (constantly '())}
      #(is (= []
              (health-check)))))

  (testing "failing docker daemon"
    (with-redefs-fn {#'docker/ping (constantly (f/fail "Docker Failed"))
                     #'db-health-check (constantly {:?column? true})
                     #'ping-external-systems (constantly '())}
      #(is (= ["Docker"]
              (health-check)))))

  (testing "failing postgres db"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly (f/fail "Postgres Failed"))}
      #(is (= ["Postgres"]
              (health-check)))))

  (testing "failing docker and postgres"
    (with-redefs-fn {#'docker/ping (constantly (f/fail "Docker Failed"))
                     #'db-health-check (constantly (f/fail "Postgres Failed"))}
      #(is (= ["Docker" "Postgres"]
              (health-check)))))

  (testing "failing external resource"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly {:?column? true})
                     #'ping-external-systems (constantly '("failed"))}
      #(is (= ["failed"]
              (health-check)))))

  (testing "failing external resources"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly {:?column? true})
                     #'ping-external-systems (constantly '("failed" "failed"))}
      #(is (= ["failed" "failed"]
              (health-check))))))
