; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.artifact-store-test
  (:require
   [clojure.spec.alpha]
   [clojure.test :refer [deftest is testing]]
   [common.schemas]
   [entities.artifact-store :as artifact-store]
   [entities.util :as u]
   [xtdb.api :as xt]))

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
                         (u/spec-assert :bob.db/artifact-store effect)))))
    (testing "deletion"
      (u/with-system (fn [db queue-chan]
                       (let [artifact-store {:name "s3"}
                             delete-res     (artifact-store/un-register-artifact-store db queue-chan artifact-store)
                             _ (Thread/sleep 1000)
                             effect         (xt/entity (xt/db db) id)]
                         (is (= "Ok" delete-res))
                         (is (nil? effect))))))))
