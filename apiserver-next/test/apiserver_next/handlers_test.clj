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

(ns apiserver_next.handlers-test
  (:require [clojure.test :as t]
            [failjure.core :as f]
            [langohr.channel :as lch]
            [apiserver_next.handlers :as h]
            [apiserver_next.util :as u]))

(t/deftest helpers-test
  (t/testing "default response"
    (t/is (= {:status 200
              :body   {:message "response"}}
             (h/respond "response"))))
  (t/testing "response with status"
    (t/is (= {:status 404
              :body   {:message "not found"}}
             (h/respond "not found" 404))))
  (t/testing "successful exec with default message"
    (t/is (= {:status 200
              :body   {:message "Ok"}}
             (h/exec #(+ 1 2)))))
  (t/testing "successful exec with supplied message"
    (t/is (= {:status 200
              :body   {:message "Yes"}}
             (h/exec #(+ 1 2) "Yes"))))
  (t/testing "failed exec"
    (t/is (= {:status 500
              :body   {:message "Divide by zero"}}
             (h/exec #(/ 5 0))))))

(t/deftest health-check-test
  (u/with-system (fn [db queue]
                   (t/testing "passing health check"
                     (let [{:keys [status body]} (h/health-check {:db    db
                                                                  :queue queue})]
                       (t/is (= status 200))
                       (t/is (= "Yes we can! 🔨 🔨"
                                (-> body
                                    :message
                                    f/message)))))
                   (t/testing "failing health check"
                     (lch/close queue)
                     (let [{:keys [status body]} (h/health-check {:db    db
                                                                  :queue queue})]
                       (t/is (= status 500))
                       (t/is (= "Queue is unreachable"
                                (-> body
                                    :message
                                    first
                                    f/message))))))))
