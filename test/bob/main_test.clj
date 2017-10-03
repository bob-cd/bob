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

(ns bob.main-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [bob.main :refer :all]))

(deftest routes-test
  (testing "Status page"
    (let [response (app (mock/request :get "/status"))
          body (:body response)]
      (is (= (:status response) 200))
      (is (= (:status body) "Ok"))))

  (testing "Non-existent route"
    (let [response (app (mock/request :get "/bogus-route"))
          body (:body response)]
      (is (= (:status response) 404))
      (is (= body "You've hit a dead end."))))

  (testing "Wrapping bob"
    (is (not (nil? (wrap app))))))
