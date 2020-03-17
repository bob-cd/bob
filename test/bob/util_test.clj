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

(ns bob.util-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is]]
            [failjure.core :as f]
            [bob.util :refer :all]))

(defspec respond-returns-a-ring-response
  100
  (prop/for-all [msg gen/string]
                (= (respond msg) {:body    {:message msg}
                                  :headers {}
                                  :status  200})))

(defspec respond-returns-service-unavailable
  100
  (prop/for-all [msg gen/string]
                (= (service-unavailable msg) {:body    {:message msg}
                                              :headers {}
                                              :status  503})))

(s/def ::container-id
  (s/and string?
         #(> (count %) id-length)))

(defspec format-id-formats-given-id
  100
  (prop/for-all [msg (s/gen ::container-id)]
    (<= (count (format-id msg)) id-length)))

(def UUID-pattern #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

(deftest get-id-test
  (testing "Unique id generation"
    (is (re-matches UUID-pattern (get-id)))
    (let [id1 (get-id)
          id2 (get-id)]
      (is (not= id1 id2)))))

(deftest log-and-fail-test
  (testing "variadic arguments"
    (is (f/failed? (log-and-fail (gen/generate gen/string-alphanumeric)
                                 (gen/generate gen/string-alphanumeric)
                                 (gen/generate gen/string-alphanumeric)
                                 (gen/generate gen/string-alphanumeric))))))

(deftest shell-arg-tokenize-test
  (testing "tokenizing a Shell command"
    (is (= (sh-tokenize! "sh -c \"while sleep 1; do echo ${RANDOM}; done\"")
           ["sh" "-c" "while sleep 1; do echo ${RANDOM}; done"])))
  (testing "tokenizing a Shell command with escaped double quotes"
    (is (= (sh-tokenize! "sort -t \"\t\" -k2 test > test-sorted")
           ["sort" "-t" "\t" "-k2" "test" ">" "test-sorted"]))))

(deftest format-env-vars-test
  (testing "format a map of env vars for Docker"
    (is (= (format-env-vars {:var1 "val1"
                             :var2 "val2"})
           ["var1=val1" "var2=val2"]))))
