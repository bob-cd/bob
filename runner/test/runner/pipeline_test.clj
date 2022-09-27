; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.pipeline-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [xtdb.api :as xt]
            [failjure.core :as f]
            [java-http-clj.core :as http]
            [common.schemas]
            [runner.util :as u]
            [runner.engine :as eng]
            [runner.engine-test :as et]
            [runner.pipeline :as p]))

(def test-image "alpine:latest")

(deftest ^:integration logging-to-db
  (u/with-system (fn [db _]
                   (testing "log raw line"
                     (p/log->db db "r-a-run-id" "a log line")
                     (Thread/sleep 1000)
                     (u/spec-assert :bob.db/log-line
                                    (-> (xt/db db)
                                        (xt/q '{:find  [(pull log [:type :run-id :line])]
                                                :where [[log :run-id "r-a-run-id"]]})
                                        ffirst)))

                   (testing "log event"
                     (p/log-event db "r-another-run-id" "another log line")
                     (Thread/sleep 1000)
                     (u/spec-assert :bob.db/log-line-event
                                    (-> (xt/db db)
                                        (xt/q '{:find  [(pull log [:type :run-id :line])]
                                                :where [[log :run-id "r-another-run-id"]]})
                                        ffirst))))))

(deftest ^:integration garbage-collection
  (testing "mark image"
    (let [state (p/mark-image-for-gc "a-image" "a-run-id")]
      (is (= (list "a-image") (get-in state [:images-for-gc "a-run-id"])))))

  (testing "mark and sweep"
    (eng/pull-image test-image)
    (p/mark-image-for-gc test-image "another-run-id")
    (let [state (p/gc-images "another-run-id")]
      (is (not (contains? state "another-run-id")))
      (is (empty? (->> (et/image-ls)
                       (filter #(= % test-image))))))))

(deftest ^:integration resource-mounts
  (u/with-system (fn [db _]
                   (testing "successful resource provisioning of a step"
                     (eng/pull-image test-image)
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id :bob.resource-provider/git
                                                   :type  :resource-provider
                                                   :url   "http://localhost:8000"
                                                   :name  "git"}]]))
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id     :bob.pipeline.test/test
                                                   :type      :pipeline
                                                   :group     "test"
                                                   :name      "test"
                                                   :steps     []
                                                   :vars      {}
                                                   :resources [{:name     "source"
                                                                :type     "external"
                                                                :provider "git"
                                                                :params   {:repo   "https://github.com/bob-cd/bob"
                                                                           :branch "main"}}]
                                                   :image     test-image}]]))
                     (let [image (p/resourceful-step db
                                                     {:needs_resource "source"
                                                      :cmd            "ls"}
                                                     "test"
                                                     "test"
                                                     test-image
                                                     "a-run-id")]
                       (is (not (f/failed? image)))
                       (eng/delete-image image))
                     (eng/delete-image test-image))))

  (u/with-system (fn [db _]
                   (testing "unsuccessful resource provisioning of a step"
                     (is (f/failed? (p/resourceful-step db
                                                        {:needs_resource "source"
                                                         :cmd            "ls"}
                                                        "test"
                                                        "test"
                                                        test-image
                                                        "a-run-id"))))))

  (testing "mount needed for step"
    (is (p/mount-needed? {:mounted #{"another-resource"}} {:needs_resource "a-resource"}))
    (is (p/mount-needed? {:mounted #{}} {:needs_resource "a-resource"})))

  (testing "mount not needed for step"
    (is (not (p/mount-needed? {:mounted #{"a-resource"}} {:needs_resource "a-resource"})))
    (is (not (p/mount-needed? {:mounted #{"a-resource"}} {})))))

(deftest ^:integration successful-step-executions
  (testing "successful simple step execution"
    (eng/pull-image test-image)
    (u/with-system (fn [db _]
                     (let [initial-state {:image     test-image
                                          :mounted   #{}
                                          :run-id    "r-a-simple-run-id"
                                          :db-client db
                                          :env       {}
                                          :group     "test"
                                          :name      "test"}
                           step          {:cmd "ls"}
                           final-state   (p/exec-step initial-state step)]
                       (is (not (f/failed? final-state)))
                       (is (not= test-image (:image final-state)))
                       (is (empty? (:mounted final-state))))
                     (p/gc-images "a-simple-run-id"))))

  (testing "successful step with resource execution"
    (eng/pull-image test-image)
    (u/with-system (fn [db _]
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id :bob.resource-provider/git
                                                   :type  :resource-provider
                                                   :name  "git"
                                                   :url   "http://localhost:8000"}]]))
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id     :bob.pipeline.test/test
                                                   :type      :pipeline
                                                   :group     "test"
                                                   :name      "test"
                                                   :steps     []
                                                   :vars      {}
                                                   :resources [{:name     "source"
                                                                :type     "external"
                                                                :provider "git"
                                                                :params   {:repo   "https://github.com/bob-cd/bob"
                                                                           :branch "main"}}]
                                                   :image     test-image}]]))
                     (let [initial-state {:image     test-image
                                          :mounted   #{}
                                          :run-id    "r-a-resource-run-id"
                                          :db-client db
                                          :env       {}
                                          :group     "test"
                                          :name      "test"}
                           step          {:cmd            "ls"
                                          :needs_resource "source"}
                           final-state   (p/exec-step initial-state step)]
                       (is (not (f/failed? final-state)))
                       (is (contains? (:mounted final-state) "source")))
                     (p/gc-images "a-resource-run-id"))))

  (testing "successful step with artifact execution"
    (eng/pull-image test-image)
    (u/with-system
      (fn [db _]
        (xt/await-tx db
                     (xt/submit-tx db
                                   [[::xt/put
                                     {:xt/id :bob.artifact-store/local
                                      :type  :artifact-store
                                      :name  "local"
                                      :url   "http://localhost:8001"}]]))
        (let [initial-state {:image     test-image
                             :mounted   #{}
                             :run-id    "r-a-artifact-run-id"
                             :db-client db
                             :env       {}
                             :group     "test"
                             :name      "test"}
              step          {:cmd               "touch text.txt"
                             :produces_artifact {:path  "text.txt"
                                                 :name  "text"
                                                 :store "local"}}
              final-state   (p/exec-step initial-state step)]
          (is (not (f/failed? final-state)))
          (is (empty? (:mounted final-state)))
          (is (= 200
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/r-a-artifact-run-id/text")))))
        (p/gc-images "a-artifact-run-id"))))

  (testing "successful step with resource and artifact execution"
    (eng/pull-image test-image)
    (u/with-system
      (fn [db _]
        (xt/await-tx db
                     (xt/submit-tx db
                                   [[::xt/put
                                     {:xt/id :bob.resource-provider/git
                                      :type  :resource-provider
                                      :name  "git"
                                      :url   "http://localhost:8000"}]]))

        (xt/await-tx db
                     (xt/submit-tx db
                                   [[::xt/put
                                     {:xt/id :bob.artifact-store/local
                                      :type  :artifact-store
                                      :name  "local"
                                      :url   "http://localhost:8001"}]]))
        (xt/await-tx db
                     (xt/submit-tx db
                                   [[::xt/put
                                     {:xt/id     :bob.pipeline.test/test
                                      :type      :pipeline
                                      :group     "test"
                                      :name      "test"
                                      :steps     []
                                      :vars      {}
                                      :resources [{:name     "source"
                                                   :type     "external"
                                                   :provider "git"
                                                   :params   {:repo   "https://github.com/bob-cd/bob"
                                                              :branch "main"}}]
                                      :image     test-image}]]))
        (let [initial-state {:image     test-image
                             :mounted   #{}
                             :run-id    "r-a-full-run-id"
                             :db-client db
                             :env       {}
                             :group     "test"
                             :name      "test"}
              step          {:needs_resource    "source"
                             :cmd               "ls"
                             :produces_artifact {:path  "README.md"
                                                 :name  "text"
                                                 :store "local"}}
              final-state   (p/exec-step initial-state step)]
          (is (not (f/failed? final-state)))
          (is (contains? (:mounted final-state) "source"))
          (is (= 200
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/r-a-full-run-id/text")))))
        (p/gc-images "a-full-run-id")))))

(deftest ^:integration failed-step-executions
  (testing "reduces upon build failure"
    (is (reduced? (p/exec-step (f/fail "this is fine") {}))))

  (testing "wrong command step failure"
    (u/with-system (fn [db _]
                     (let [initial-state {:image     test-image
                                          :mounted   #{}
                                          :run-id    "r-a-simple-run-id"
                                          :db-client db
                                          :env       {}
                                          :group     "test"
                                          :name      "test"}
                           step          {:cmd "this-bombs"}
                           final-state   (p/exec-step initial-state step)]
                       (is (f/failed? final-state)))
                     (p/gc-images "a-simple-run-id")))))

(deftest ^:integration pipeline-starts
  (testing "successful pipeline run"
    (u/with-system (fn [db queue]
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id :bob.pipeline.test/test
                                                   :type  :pipeline
                                                   :group "test"
                                                   :name  "test"
                                                   :steps [{:cmd "echo hello"} {:cmd "sh -c \"echo ${k1}\""}]
                                                   :vars  {:k1 "v1"}
                                                   :image test-image}]]))
                     (let [result   @(p/start db
                                       queue
                                       {:group  "test"
                                        :name   "test"
                                        :run_id "r-a-run-id"})
                           history  (xt/entity-history (xt/db db)
                                                       (keyword (str "bob.pipeline.run/" result))
                                                       :desc
                                                       {:with-docs? true})
                           run-info (xt/entity (xt/db db) (keyword (str "bob.pipeline.run/" result)))
                           statuses (->> history
                                         (map ::xt/doc)
                                         (map :status))]
                       (is (= [:passed :running :initializing]
                              statuses))
                       (is (not (f/failed? result)))
                       (u/spec-assert :bob.db/run run-info)))))

  (testing "failed pipeline run"
    (u/with-system (fn [db queue]
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id :bob.pipeline.test/test
                                                   :type  :pipeline
                                                   :group "test"
                                                   :name  "test"
                                                   :steps [{:cmd "echo hello"} {:cmd "this-bombs"}]
                                                   :vars  {:k1 "v1"}
                                                   :image test-image}]]))
                     (let [result   @(p/start db
                                       queue
                                       {:group  "test"
                                        :name   "test"
                                        :run_id "r-a-run-id"})
                           id       (f/message result)
                           run-info (xt/entity (xt/db db) (keyword (str "bob.pipeline.run/" id)))
                           history  (xt/entity-history (xt/db db)
                                                       (keyword (str "bob.pipeline.run/" id))
                                                       :desc
                                                       {:with-docs? true})
                           statuses (->> history
                                         (map ::xt/doc)
                                         (map :status)
                                         (into #{}))]
                       (u/spec-assert :bob.db/run run-info)
                       (is (f/failed? result))
                       (is (contains? statuses :running))
                       (is (contains? statuses :failed)))))))

(deftest ^:integration pipeline-stop
  (testing "stopping a pipeline run"
    (u/with-system
      (fn [db queue]
        (xt/await-tx db
                     (xt/submit-tx db
                                   [[::xt/put
                                     {:xt/id :bob.pipeline.test/stop-test
                                      :type  :pipeline
                                      :group "test"
                                      :name  "stop-test"
                                      :steps [{:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"}]
                                      :vars  {}
                                      :image test-image}]]))
        (let [_ (p/start db
                  queue
                  {:group  "test"
                   :name   "stop-test"
                   :run_id "r-a-stop-id"})
              _ (Thread/sleep 5000) ;; Longer, possibly flaky wait
              _ (p/stop db
                  queue
                  {:group  "test"
                   :name   "stop-test"
                   :run_id "r-a-stop-id"})
              run-info (xt/entity (xt/db db) :bob.pipeline.run/r-a-stop-id)
              history  (xt/entity-history (xt/db db)
                                          :bob.pipeline.run/r-a-stop-id
                                          :desc
                                          {:with-docs? true})
              statuses (->> history
                            (map ::xt/doc)
                            (map :status)
                            (into #{}))]
          (u/spec-assert :bob.db/run run-info)
          (is (not (contains? statuses :failed)))
          (is (contains? statuses :running))
          (is (contains? statuses :stopped)))))))
