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

(ns bob.config-test
  (:require [clojure.test :refer :all]
            [bob.test-utils :as tu]
            [bob.config :refer :all]))

(deftest load-config-test
  (testing "defaults only"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:server {:port 7777}
               :docker
               {:uri "unix:///var/run/docker.sock",
                :timeouts {:connect-timeout 1000,
                           :read-timeout 30000,
                           :write-timeout 30000,
                           :call-timeout 40000}},
               :postgres {:host "localhost",
                          :port 5432,
                          :user "bob",
                          :database "bob"}}
              (load-config)))))
  (testing "defaults and bob.conf"
    (with-redefs-fn {#'read-config-file (constantly {:server {:port 8888}
                                                     :docker {:uri "foobar"}
                                                     :postgres {:port 2345}})}
      #(is (= {:server {:port 8888}
               :docker
               {:uri "foobar",
                :timeouts {:connect-timeout 1000,
                           :read-timeout 30000,
                           :write-timeout 30000,
                           :call-timeout 40000}},
               :postgres {:host "localhost",
                          :port 2345,
                          :user "bob",
                          :database "bob",}}
              (load-config))))))

(deftest merge-configs-test
  (testing "correct merge of two overlapping configs"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:foo {:bar :baz :faz :quo}}
             (merge-configs {:foo {:bar :baz}}
                            {:foo {:faz :quo}})))))
  (testing "merging of nil from bob.conf"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:foo {:bar :baz}}
             (merge-configs {:foo {:bar :baz}}
                            nil)))))
  (testing "merging of deeply nested confs"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:foo {:bar {:baz {:quo "meh" :lah "ber"}}}}
             (merge-configs {:foo {:bar {:baz {:quo "foo" :lah "ber" :yfo "cre"}}}}
                            {:foo {:bar {:baz {:quo "meh" :lah "ber"}}}})))))
  (testing "catched exception when merging a string"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:foo "foo" :bar {:baz "baz"}}
             (merge-configs {:foo "foo" :bar {:baz "baz"}}
                            "not so good file content")))))
  (testing "catched exception when merging a vector"
    (with-redefs-fn {#'read-config-file (constantly nil)}
      #(is (= {:foo "foo" :bar {:baz "baz"}}
             (merge-configs {:foo "foo" :bar {:baz "baz"}}
                            [:meh]))))))
