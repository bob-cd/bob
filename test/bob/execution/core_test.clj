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
            [clojure.core.async :refer [go]]
            [bob.execution.core :refer :all]
            [bob.execution.internals :refer [pull build]]))

(def SHA-pattern #"\b[0-9a-f]{5,40}\b")

(def good-test-command ["echo" "hello"])

(def bad-test-command ["ech" "hello"])

(def good-test-image "busybox:musl")

(defn good-container []
  (-> (pull good-test-image)
      (build good-test-command)))

(defn bad-container []
  (-> (pull good-test-image)
      (build bad-test-command)))

(deftest start-test
  (testing "successful start"
    (let [id (good-container)
          id ((@(start id) :body) :message)]
      (is (not (nil? (re-matches SHA-pattern id))))))
  (testing "unsuccessful start with bad command"
    (let [id (bad-container)
          id ((@(start id) :body) :message)]
      (is (.contains id "executable file not found in $PATH"))))
  (gc true))

(deftest logs-test
  (testing "successful log fetch"
    (let [id   (good-container)
          id   ((@(start id) :body) :message)
          logs ((@(logs-of id 1 1) :body) :message)]
      (is (= (list "hello") logs))
      (cancel id)))
  (testing "unsuccessful log fetch"
    (let [log ((@(logs-of "crap" 1 1) :body) :message)]
      (is (= log "Container not found: crap"))))
  (gc true))

(deftest status-test
  (testing "successful status fetch"
    (let [id     (good-container)
          id     ((@(start id) :body) :message)
          status ((@(status-of id) :body) :message)]
      (is (= 0 (status :exitCode)))
      (cancel id)))
  (testing "unsuccessful status fetch"
    (let [status ((@(status-of "crap") :body) :message)]
      (is (= "Container not found: crap" status))))
  (gc true))
