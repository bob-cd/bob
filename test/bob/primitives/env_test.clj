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

(ns bob.primitives.env-test
  (:require [clojure.test :refer :all]
            [bob.primitives.env :refer :all])
  (:import (bob.primitives.env Env)))

(deftest env-test
  (testing "Adding var to Bob Env"
    (let [initial-env (Env. "1" {:k1 "v1"})
          final-env (Env. "1" {:k1 "v1"
                               :k2 "v2"})]
      (is (= final-env (add-var-in initial-env :k2 "v2")))))
  (testing "Removing var from Bob Env"
    (let [initial-env (Env. "1" {:k1 "v1"
                                 :k2 "v2"})
          final-env (Env. "1" {:k1 "v1"})]
      (is (= final-env (remove-var-from initial-env :k2))))))
