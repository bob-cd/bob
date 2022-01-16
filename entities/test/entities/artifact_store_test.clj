; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.artifact-store-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [xtdb.api :as xt]
            [common.schemas]
            [entities.util :as u]
            [entities.artifact-store :as artifact-store]))

;; TODO: Better way to wait for consistency than sleep
(deftest ^:integration artifact-store
  (let [id :bob.artifact-store/s3]
    (testing "creation"
      (u/with-system (fn [db queue-chan]
                       (let [artifact-store {:name "s3"
                                             :url  "my.store.com"}
                             create-res     (artifact-store/register-artifact-store db queue-chan artifact-store)
                             _ (Thread/sleep 1000)
                             effect         (xt/entity (xt/db db) id)]
                         (is (= "Ok" create-res))
                         (is (= id (:xt/id effect)))
                         (is (spec/valid? :bob.db/artifact-store effect))))))
    (testing "deletion"
      (u/with-system (fn [db queue-chan]
                       (let [artifact-store {:name "s3"}
                             delete-res     (artifact-store/un-register-artifact-store db queue-chan artifact-store)
                             _ (Thread/sleep 1000)
                             effect         (xt/entity (xt/db db) id)]
                         (is (= "Ok" delete-res))
                         (is (nil? effect))))))))
