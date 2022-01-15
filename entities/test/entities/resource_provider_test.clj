; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.resource-provider-test
  (:require [clojure.test :refer [deftest testing is]]
            [xtdb.api :as xt]
            [entities.util :as u]
            [entities.resource-provider :as resource-provider]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration resource-provider
  (testing "creation"
    (u/with-system
      (fn [db queue-chan]
        (let [resource-provider {:name "github"
                                 :url  "my.resource.com"}
              create-res        (resource-provider/register-resource-provider db queue-chan resource-provider)
              _ (Thread/sleep 1000)
              effect            (xt/entity (xt/db db) :bob.resource-provider/github)]
          (is (= "Ok" create-res))
          (is (= {:xt/id :bob.resource-provider/github
                  :type     :resource-provider
                  :url      "my.resource.com"
                  :name     "github"}
                 effect))))))
  (testing "deletion"
    (u/with-system
      (fn [db queue-chan]
        (let [resource-provider {:name "github"}
              delete-res        (resource-provider/un-register-resource-provider db queue-chan resource-provider)
              _ (Thread/sleep 1000)
              effect            (xt/entity (xt/db db) :bob.resource-provider/github)]
          (is (= "Ok" delete-res))
          (is (nil? effect)))))))
