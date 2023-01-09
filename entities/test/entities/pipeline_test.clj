; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.pipeline-test
  (:require
    [clojure.spec.alpha]
    [clojure.test :refer [deftest is testing]]
    [common.schemas]
    [entities.pipeline :as p]
    [entities.util :as u]
    [xtdb.api :as xt]))

;; TODO: Better way to wait for consistency than sleep
(deftest ^:integration pipleine
  (let [pipeline-id :bob.pipeline.test/test
        run-id      :bob.pipeline.run/r-a-run
        log-id      :bob.pipeline.log/l-a-log]
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
                                         :params   {:repo   "https://github.com/lispyclouds/contajners"
                                                    :branch "main"}}]
                            :image     "busybox:musl"}
                create-res (p/create db queue-chan pipeline)
                _ (Thread/sleep 1000)
                effect     (xt/entity (xt/db db) pipeline-id)]
            (is (= "Ok" create-res))
            (is (= pipeline-id (:xt/id effect)))
            (u/spec-assert :bob.db/pipeline effect)))))
    (testing "deletion"
      (u/with-system (fn [db queue-chan]
                       (xt/await-tx
                         db
                         (xt/submit-tx db
                                       [[::xt/put
                                         {:xt/id run-id
                                          :type  :pipeline-run
                                          :group "test"
                                          :name  "test"}]
                                        [::xt/put
                                         {:xt/id  log-id
                                          :type   :log-line
                                          :run-id run-id}]]))
                       (let [pipeline        {:name  "test"
                                              :group "test"}
                             delete-res      (p/delete db queue-chan pipeline)
                             _ (Thread/sleep 1000)
                             pipeline-effect (xt/entity (xt/db db) pipeline-id)
                             run-effect      (xt/entity (xt/db db) run-id)
                             log-effect      (xt/entity (xt/db db) log-id)]
                         (is (= "Ok" delete-res))
                         (is (nil? pipeline-effect))
                         (is (nil? run-effect))
                         (is (nil? log-effect))))))))
