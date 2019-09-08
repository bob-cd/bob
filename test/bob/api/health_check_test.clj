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
    (with-redefs-fn {#'docker/ping (fn [x] "OK")
                     #'db-health-check (fn [x] {:?column? true})}
      #(is (= {:status 200, :headers {}, :body {:message "Yes, we can! \uD83D\uDD28 \uD83D\uDD28"}} @(health-check)))))

  (testing "failing docker daemon"
    (with-redefs-fn {#'docker/ping (fn [x] (f/fail "Docker Failed"))
                     #'db-health-check (fn [x] {:?column? true})}
      #(is (= {:status 503, :headers {}, :body {:message "Docker or Postgres unavailable"}} @(health-check)))))

  (testing "failing postgres db"
    (with-redefs-fn {#'docker/ping (fn [x] "OK")
                     #'db-health-check (fn [x] (f/fail "Postgres Failed"))}
      #(is (= {:status 503, :headers {}, :body {:message "Docker or Postgres unavailable"}} @(health-check))))))
