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

(ns bob.execution.core-test
  (:require [clojure.test :refer :all]
            [bob.execution.core :refer :all]))

(def SHA-pattern #"\b[0-9a-f]{5,40}\b")

(def good-test-command ["echo" "hello"])

(def bad-test-command ["ech" "hello"])

(def good-test-image "busybox:musl")

(def bad-test-image "busybox:mus")

(deftest start-test
  (testing "successful start"
    (let [id ((@(start good-test-command good-test-image) :body) :message)]
      (is (not (nil? (re-matches SHA-pattern id))))
      (stop id)))
  (testing "unsuccessful start with wrong image"
    (let [id ((@(start good-test-command bad-test-image) :body) :message)]
      (is (= id (format "Cannot pull %s" bad-test-image)))))
  (testing "unsuccessful start with bad command"
    (let [id ((@(start bad-test-command good-test-image) :body) :message)]
      (is (.contains id "executable file not found in $PATH")))))

(deftest logs-test
  (testing "successful log fetch"
    (let [id   ((@(start good-test-command good-test-image) :body) :message)
          logs ((@(logs-of id 1 1) :body) :message)]
      (is (= (list "hello") logs))
      (stop id)))
  (testing "unsuccessful log fetch"
    (let [log ((@(logs-of "crap" 1 1) :body) :message)]
      (is (= log "Container not found: crap")))))

(deftest stop-test
  (testing "successful stop"
    (let [id  ((@(start good-test-command good-test-image) :body) :message)
          msg ((@(stop id) :body) :message)]
      (is (= "Ok" msg))))
  (testing "unsuccessful stop"
    (let [msg ((@(stop "crap") :body) :message)]
      (is (= "Could not kill crap" msg)))))

(deftest status-test
  (testing "successful status fetch"
    (let [id     ((@(start good-test-command good-test-image) :body) :message)
          status ((@(status-of id) :body) :message)]
      (is (status :running))
      (is (= 0 (status :exitCode)))
      (stop id)))
  (testing "unsuccessful status fetch"
    (let [status ((@(status-of "crap") :body) :message)]
      (is (= "Container not found: crap" status)))))
