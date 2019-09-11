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

(ns bob.resource.core-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [bob.test-utils :as tu]
            [bob.resource.core :refer :all]
            [bob.resource.internals :as r]
            [bob.resource.db :as db]))

(deftest external-resource-lifecycle
  (testing "successful external resource registration"
    (with-redefs-fn {#'db/insert-external-resource (fn [_ args]
                                                     (tu/check-and-fail
                                                      #(= {:name "git"
                                                           :url  "test"}
                                                          args)))}
      #(is (= "Ok" (-> @(register-external-resource "git" "test")
                       :body
                       :message)))))

  (testing "unsuccessful external resource registration"
    (with-redefs-fn {#'db/insert-external-resource #(f/fail "Nope")}
      #(is (= {:message "Resource Provider may already be registered"}
              (-> @(register-external-resource "git" "test")
                  :body)))))

  (testing "successful external resource un-registration"
    (with-redefs-fn {#'db/delete-external-resource (fn [_ args]
                                                     (tu/check-and-fail
                                                      #(= {:name "git"}
                                                          args)))}
      #(is (= "Ok"
              (-> @(un-register-external-resource "git")
                  :body
                  :message)))))

  (testing "listing all external resources"
    (with-redefs-fn {#'db/external-resources (constantly [{:name "r1"}
                                                          {:name "r2"}])}
      #(is (= ["r1" "r2"]
              (-> @(all-external-resources)
                  :body
                  :message))))))

(deftest image-mount
  (testing "successful image mount"
    (with-redefs-fn {#'r/valid-external-resource? (fn [resource]
                                                    (tu/check-and-fail
                                                     #(= "test" resource))
                                                    true)
                     #'r/fetch-resource           (fn [resource pipeline]
                                                    (tu/check-and-fail
                                                     #(and (= "test" resource)
                                                           (= "p1" pipeline)))
                                                    "/some/path")
                     #'r/initial-image-of         (fn [dir img cmd]
                                                    (tu/check-and-fail
                                                     #(and (= "/some/path" dir)
                                                           (= "test-img" img)
                                                           (nil? cmd)))
                                                    "mounted-image-id")}
      #(is (= "mounted-image-id"
              (mounted-image-from "test" "p1" "test-img"))))))
