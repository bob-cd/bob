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

(ns bob.resource.internals-test
  (:require [clojure.test :refer :all]
            [bob.test-utils :as tu]
            [bob.resource.db :as db]
            [bob.resource.internals :refer :all]))

;; TODO: Test the rest of the fns too?

(deftest utils-test
  (testing "generation of GET url from resource"
    (with-redefs-fn {#'db/external-resource-url (fn [_ params]
                                                  (tu/check-and-fail
                                                   #(= "git"
                                                       (:name params)))
                                                  {:url "http://url.com"})
                     #'db/resource-params-of    (fn [_ params]
                                                  (tu/check-and-fail
                                                   #(and (= "r1"
                                                            (:name params))
                                                         (= "test"
                                                            (:pipeline params))))
                                                  [{:key   "k1"
                                                    :value "v1"}
                                                   {:key   "k2"
                                                    :value "v2"}])}
      #(is (= "http://url.com/bob_request?k1=v1&k2=v2"
              (url-of {:provider "git"
                       :name     "r1"}
                      "test"))))))
