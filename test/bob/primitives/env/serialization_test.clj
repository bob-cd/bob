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

(ns bob.primitives.env.serialization-test
  (:require [clojure.test :refer :all]
            [bob.primitives.env.serialization :refer :all])
  (:import (bob.primitives.env.env Env)))

(deftest env-serialization-test
  (testing "Serialize Env to JSON"
    (let [json "{\"id\":\"1\",\"vars\":{\"k1\":\"v1\"}}"
          env (Env. "1" {:k1 "v1"})]
      (is (= json (env-to-json env)))))
  (testing "De-Serialize JSON to Env"
    (let [json "{\"id\":\"1\",\"vars\":{\"k1\":\"v1\"}}"
          env (Env. "1" {:k1 "v1"})]
      (is (= env (json-to-env json))))))
