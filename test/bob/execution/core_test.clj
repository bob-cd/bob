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
            [clojure.java.shell :as shell]
            [failjure.core :as f]
            [bob.test-utils :as tu]
            [bob.execution.core :refer :all]))

(deftest gc-test
  (testing "simple GC"
    (with-redefs-fn {#'shell/sh (constantly nil)}
      #(is (not (f/failed? (gc))))))

  (testing "full GC"
    (with-redefs-fn {#'shell/sh (fn [args]
                                  (tu/check-and-fail
                                   #(= "-a"
                                       (last args))))}
      #(is (not (f/failed? (gc true)))))))
