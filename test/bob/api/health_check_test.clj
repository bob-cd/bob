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

(ns bob.api.health-check-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.api.health-check :refer :all]))

(deftest health-check-various-conditions
  (testing "all systems operational"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly {:?column? true})}
      #(is (= {:status 200, :headers {}, :body {:message "Yes, we can! \uD83D\uDD28 \uD83D\uDD28"}} @(health-check)))))

  (testing "failing docker daemon"
    (with-redefs-fn {#'docker/ping (constantly (f/fail "Docker Failed"))
                     #'db-health-check (constantly {:?column? true})}
      #(is (= {:status 503, :headers {}, :body {:message "Health check failed: Docker not healthy"}} @(health-check)))))

  (testing "failing postgres db"
    (with-redefs-fn {#'docker/ping (constantly "OK")
                     #'db-health-check (constantly (f/fail "Postgres Failed"))}
      #(is (= {:status 503, :headers {}, :body {:message "Health check failed: Postgres not healthy"}} @(health-check)))))

  (testing "failing docker and postgres"
    (with-redefs-fn {#'docker/ping (constantly (f/fail "Docker Failed"))
                     #'db-health-check (constantly (f/fail "Postgres Failed"))}
      #(is (= {:status 503, :headers {}, :body {:message "Health check failed: Docker and Postgres not healthy"}} @(health-check))))))
