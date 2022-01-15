; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.artifact-store-test
  (:require [clojure.test :refer [deftest testing is]]
            [xtdb.api :as xt]
            [entities.util :as u]
            [entities.artifact-store :as artifact-store]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration artifact-store
  (testing "creation"
    (u/with-system (fn [db queue-chan]
                     (let [artifact-store {:name "s3"
                                           :url  "my.store.com"}
                           create-res     (artifact-store/register-artifact-store db queue-chan artifact-store)
                           _ (Thread/sleep 1000)
                           effect         (xt/entity (xt/db db) :bob.artifact-store/s3)]
                       (is (= "Ok" create-res))
                       (is (= {:xt/id :bob.artifact-store/s3
                               :type     :artifact-store
                               :url      "my.store.com"
                               :name     "s3"}
                              effect))))))
  (testing "deletion"
    (u/with-system (fn [db queue-chan]
                     (let [artifact-store {:name "s3"}
                           delete-res     (artifact-store/un-register-artifact-store db queue-chan artifact-store)
                           _ (Thread/sleep 1000)
                           effect         (xt/entity (xt/db db) :bob.artifact-store/s3)]
                       (is (= "Ok" delete-res))
                       (is (nil? effect)))))))
