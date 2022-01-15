; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.pipeline-test
  (:require [clojure.test :refer [deftest testing is]]
            [xtdb.api :as xt]
            [entities.util :as u]
            [entities.pipeline :as p]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration pipleine
  (testing "creation"
    (u/with-system
      (fn [db queue-chan]
        (let [pipeline   {:group     "test"
                          :name      "test"
                          :steps     [{:cmd "echo hello"}
                                      {:needs_resource "source"
                                       :cmd            "ls"}
                                      {:cmd               "touch test"
                                       :produces_artifact {:name  "file"
                                                           :path  "test"
                                                           :store "s3"}}]
                          :vars      {:k1 "v1"
                                      :k2 "v2"}
                          :resources [{:name     "source"
                                       :type     "external"
                                       :provider "git"
                                       :params   {:repo   "https://github.com/bob-cd/bob"
                                                  :branch "main"}}
                                      {:name     "source2"
                                       :type     "external"
                                       :provider "git"
                                       :params   {:repo   "https://github.com/lispyclouds/clj-docker-client"
                                                  :branch "main"}}]
                          :image     "busybox:musl"}
              create-res (p/create db queue-chan pipeline)
              _ (Thread/sleep 1000)
              effect     (xt/entity (xt/db db) :bob.pipeline.test/test)]
          (is (= "Ok" create-res))
          (is (= (-> pipeline
                     (assoc :type :pipeline)
                     (assoc :xt/id :bob.pipeline.test/test))
                 effect))))))
  (testing "deletion"
    (u/with-system (fn [db queue-chan]
                     (let [pipeline   {:name  "test"
                                       :group "test"}
                           delete-res (p/delete db queue-chan pipeline)
                           _ (Thread/sleep 1000)
                           effect     (xt/entity (xt/db db) :bob.pipeline.test/test)]
                       (is (= "Ok" delete-res))
                       (is (nil? effect)))))))
