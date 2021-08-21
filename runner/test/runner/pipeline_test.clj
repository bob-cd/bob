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
;   You should have received a copy of the GNU Affero Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns runner.pipeline-test
  (:require [clojure.test :refer [deftest testing is]]
            [crux.api :as crux]
            [failjure.core :as f]
            [java-http-clj.core :as http]
            [runner.util :as u]
            [runner.engine :as eng]
            [runner.engine-test :as et]
            [runner.pipeline :as p]))

(def test-image "alpine:latest")

(deftest ^:integration logging-to-db
  (u/with-system (fn [db _]
                   (testing "log raw line"
                     (p/log->db db "a-run-id" "a log line")
                     (Thread/sleep 1000)
                     (is (= {:type   :log-line
                             :run-id "a-run-id"
                             :line   "a log line"}
                            (-> (crux/db db)
                                (crux/q
                                  '{:find  [(pull log [:type :run-id :line])]
                                    :where [[log :run-id "a-run-id"]]})
                                first
                                first))))

                   (testing "log event"
                     (p/log-event db "another-run-id" "another log line")
                     (Thread/sleep 1000)
                     (is (= {:type   :log-line
                             :run-id "another-run-id"
                             :line   "[bob] another log line"}
                            (-> (crux/db db)
                                (crux/q
                                  '{:find  [(pull log [:type :run-id :line])]
                                    :where [[log :run-id "another-run-id"]]})
                                first
                                first)))))))

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
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.resource-provider/git
                                                       :url        "http://localhost:8000"}]]))
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.pipeline.test/test
                                                       :group      "test"
                                                       :name       "test"
                                                       :steps      []
                                                       :vars       {}
                                                       :resources  [{:name     "source"
                                                                     :type     "external"
                                                                     :provider "git"
                                                                     :params   {:repo   "https://github.com/bob-cd/bob"
                                                                                :branch "main"}}]
                                                       :image      test-image}]]))
                     (let [image (p/resourceful-step db
                                                     {:needs_resource "source"
                                                      :cmd            "ls"}
                                                     "test"         "test"
                                                     test-image "a-run-id")]
                       (is (not (f/failed? image)))
                       (eng/delete-image image))
                     (eng/delete-image test-image))))

  (u/with-system (fn [db _]
                   (testing "unsuccessful resource provisioning of a step"
                     (is (f/failed? (p/resourceful-step db
                                                        {:needs_resource "source"
                                                         :cmd            "ls"}
                                                        "test"         "test"
                                                        test-image "a-run-id"))))))

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
                                          :run-id    "a-simple-run-id"
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
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.resource-provider/git
                                                       :url        "http://localhost:8000"}]]))
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.pipeline.test/test
                                                       :group      "test"
                                                       :name       "test"
                                                       :steps      []
                                                       :vars       {}
                                                       :resources  [{:name     "source"
                                                                     :type     "external"
                                                                     :provider "git"
                                                                     :params   {:repo   "https://github.com/bob-cd/bob"
                                                                                :branch "main"}}]
                                                       :image      test-image}]]))
                     (let [initial-state {:image     test-image
                                          :mounted   #{}
                                          :run-id    "a-resource-run-id"
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
        (crux/await-tx db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.artifact-store/local
                                          :url        "http://localhost:8001"}]]))
        (let [initial-state {:image     test-image
                             :mounted   #{}
                             :run-id    "a-artifact-run-id"
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
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/a-artifact-run-id/text")))))
        (p/gc-images "a-artifact-run-id")
        (http/delete "http://localhost:8001/bob_artifact/test/test/a-artifact-run-id/text"))))

  (testing "successful step with resource and artifact execution"
    (eng/pull-image test-image)
    (u/with-system
      (fn [db _]
        (crux/await-tx db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.resource-provider/git
                                          :url        "http://localhost:8000"}]]))

        (crux/await-tx db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.artifact-store/local
                                          :url        "http://localhost:8001"}]]))
        (crux/await-tx db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.pipeline.test/test
                                          :group      "test"
                                          :name       "test"
                                          :steps      []
                                          :vars       {}
                                          :resources  [{:name     "source"
                                                        :type     "external"
                                                        :provider "git"
                                                        :params   {:repo   "https://github.com/bob-cd/bob"
                                                                   :branch "main"}}]
                                          :image      test-image}]]))
        (let [initial-state {:image     test-image
                             :mounted   #{}
                             :run-id    "a-full-run-id"
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
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/a-full-run-id/text")))))
        (p/gc-images "a-full-run-id")
        (http/delete "http://localhost:8001/bob_artifact/test/test/a-full-run-id/text")))))

(deftest ^:integration failed-step-executions
  (testing "reduces upon build failure"
    (is (reduced? (p/exec-step (f/fail "this is fine") {}))))

  (testing "wrong command step failure"
    (u/with-system (fn [db _]
                     (let [initial-state {:image     test-image
                                          :mounted   #{}
                                          :run-id    "a-simple-run-id"
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
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.pipeline.test/test
                                                       :group      "test"
                                                       :name       "test"
                                                       :steps      [{:cmd "echo hello"} {:cmd "sh -c \"echo ${k1}\""}]
                                                       :vars       {:k1 "v1"}
                                                       :image      test-image}]]))
                     (let [result   @(p/start db
                                       queue
                                       {:group  "test"
                                        :name   "test"
                                        :run_id "a-run-id"})
                           history  (crux/entity-history (crux/db db)
                                                         (keyword (str "bob.pipeline.run/" result))
                                                         :desc
                                                         {:with-docs? true})
                           run-info (crux/entity (crux/db db) (keyword (str "bob.pipeline.run/" result)))
                           statuses (->> history
                                         (map :crux.db/doc)
                                         (map :status))]
                       (is (= [:passed :running :initializing]
                              statuses))
                       (is (not (f/failed? result)))
                       (is (inst? (:started run-info)))
                       (is (inst? (:completed run-info)))))))

  (testing "failed pipeline run"
    (u/with-system (fn [db queue]
                     (crux/await-tx db
                                    (crux/submit-tx db
                                                    [[:crux.tx/put
                                                      {:crux.db/id :bob.pipeline.test/test
                                                       :group      "test"
                                                       :name       "test"
                                                       :steps      [{:cmd "echo hello"} {:cmd "this-bombs"}]
                                                       :vars       {:k1 "v1"}
                                                       :image      test-image}]]))
                     (let [result   @(p/start db
                                       queue
                                       {:group  "test"
                                        :name   "test"
                                        :run_id "a-run-id"})
                           id       (f/message result)
                           run-info (crux/entity (crux/db db) (keyword (str "bob.pipeline.run/" id)))
                           history  (crux/entity-history (crux/db db)
                                                         (keyword (str "bob.pipeline.run/" id))
                                                         :desc
                                                         {:with-docs? true})
                           statuses (->> history
                                         (map :crux.db/doc)
                                         (map :status)
                                         (into #{}))]
                       (is (f/failed? result))
                       (is (inst? (:started run-info)))
                       (is (inst? (:completed run-info)))
                       (is (contains? statuses :running))
                       (is (contains? statuses :failed)))))))

(deftest ^:integration pipeline-stop
  (testing "stopping a pipeline run"
    (u/with-system
      (fn [db queue]
        (crux/await-tx db
                       (crux/submit-tx db
                                       [[:crux.tx/put
                                         {:crux.db/id :bob.pipeline.test/stop-test
                                          :steps      [{:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"}]
                                          :image      test-image}]]))
        (let [_        (p/start db
                         queue
                         {:group  "test"
                          :name   "stop-test"
                          :run_id "a-stop-id"})
              _        (Thread/sleep 5000) ;; Longer, possibly flaky wait
              _        (p/stop db
                         queue
                         {:group  "test"
                          :name   "stop-test"
                          :run_id "a-stop-id"})
              run-info (crux/entity (crux/db db) :bob.pipeline.run/a-stop-id)
              history  (crux/entity-history (crux/db db)
                                            :bob.pipeline.run/a-stop-id
                                            :desc
                                            {:with-docs? true})
              statuses (->> history
                            (map :crux.db/doc)
                            (map :status)
                            (into #{}))]
          (is (not (contains? statuses :failed)))
          (is (inst? (:completed run-info)))
          (is (contains? statuses :running))
          (is (contains? statuses :stopped)))))))
