; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.pipeline-test
  (:require
   [babashka.http-client :as http]
   [clojure.spec.alpha]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [common.schemas]
   [common.store :as store]
   [common.test-utils :as u]
   [failjure.core :as f]
   [runner.engine :as eng]
   [runner.engine-test :as et]
   [runner.pipeline :as p])
  (:import
   [java.time Instant]))

(def test-image "alpine:latest")

(def logger-url "http://localhost:8002")

(defn history
  [db key]
  (let [{:keys [value create-rev mod-rev]} (first (store/get db key))]
    (if-not value
      []
      (reduce #(conj %1 (store/get-one db key {:rev %2}))
              [value]
              (range (dec mod-rev) (dec create-rev) -1)))))

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
  (u/with-runner-system
    (fn [database _ stream]
      (testing "successful resource provisioning of a step"
        (eng/pull-image test-image)
        (store/put database
                   "bob.resource-provider/git"
                   {:url "http://localhost:8000"
                    :name "git"}
                   "bob.pipeline/test:test"
                   {:group "test"
                    :name "test"
                    :steps []
                    :vars {}
                    :resources [{:name "source"
                                 :type "external"
                                 :provider "git"
                                 :params {:repo "https://github.com/bob-cd/bob"
                                          :branch "main"}}]
                    :image test-image})
        (let [image (p/resourceful-step {:database database :stream stream :logger-url logger-url}
                                        {:group "test"
                                         :name "test"
                                         :image test-image
                                         :run-id "a-run-id"}
                                        {:needs_resource "source"
                                         :cmd "ls"})]
          (is (not (f/failed? image)))
          (eng/delete-image image))
        (eng/delete-image test-image))))

  (u/with-runner-system (fn [database _ stream]
                   (testing "unsuccessful resource provisioning of a step"
                     (is (f/failed? (p/resourceful-step {:database database :stream stream}
                                                        {:group "test"
                                                         :name "test"
                                                         :image test-image
                                                         :run-id "a-run-id"}
                                                        {:needs_resource "source"
                                                         :cmd "ls"}))))))

  (testing "mount needed for step"
    (is (p/mount-needed? {:mounted #{"another-resource"}} {:needs_resource "a-resource"}))
    (is (p/mount-needed? {:mounted #{}} {:needs_resource "a-resource"})))

  (testing "mount not needed for step"
    (is (not (p/mount-needed? {:mounted #{"a-resource"}} {:needs_resource "a-resource"})))
    (is (not (p/mount-needed? {:mounted #{"a-resource"}} {})))))

(deftest ^:integration successful-step-executions
  (testing "successful simple step execution"
    (eng/pull-image test-image)
    (u/with-runner-system (fn [database _ stream]
                     (let [initial-state {:image test-image
                                          :mounted #{}
                                          :run-id "r-a-simple-run-id"
                                          :env {}
                                          :group "test"
                                          :name "test"}
                           step {:cmd "ls"}
                           final-state (p/exec-step {:database database :stream stream} initial-state step)]
                       (is (not (f/failed? final-state)))
                       (is (not= test-image (:image final-state)))
                       (is (empty? (:mounted final-state))))
                     (p/gc-images "a-simple-run-id"))))

  (testing "successful step with resource execution"
    (eng/pull-image test-image)
    (u/with-runner-system
      (fn [database _ stream]
        (store/put database
                   "bob.resource-provider/git"
                   {:name "git"
                    :url "http://localhost:8000"}
                   "bob.pipeline/test:test"
                   {:group "test"
                    :name "test"
                    :steps []
                    :vars {}
                    :resources [{:name "source"
                                 :type "external"
                                 :provider "git"
                                 :params {:repo "https://github.com/bob-cd/bob"
                                          :branch "main"}}]
                    :image test-image})
        (let [initial-state {:image test-image
                             :mounted #{}
                             :run-id "r-a-resource-run-id"
                             :env {}
                             :group "test"
                             :name "test"}
              step {:cmd "ls"
                    :needs_resource "source"}
              final-state (p/exec-step {:database database :stream stream :logger-url logger-url} initial-state step)]
          (is (not (f/failed? final-state)))
          (is (contains? (:mounted final-state) "source")))
        (p/gc-images "a-resource-run-id"))))

  (testing "successful step with artifact execution"
    (eng/pull-image test-image)
    (u/with-runner-system
      (fn [database _ stream]
        (store/put database
                   "bob.artifact-store/local"
                   {:name "local"
                    :url "http://localhost:8001"})
        (let [initial-state {:image test-image
                             :mounted #{}
                             :run-id "r-a-artifact-run-id"
                             :env {}
                             :group "test"
                             :name "test"}
              step {:cmd "touch text.txt"
                    :produces_artifact {:path "text.txt"
                                        :name "text"
                                        :store "local"}}
              final-state (p/exec-step {:database database :stream stream :logger-url logger-url} initial-state step)]
          (is (not (f/failed? final-state)))
          (is (empty? (:mounted final-state)))
          (is (= 200
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/r-a-artifact-run-id/text")))))
        (p/gc-images "a-artifact-run-id"))))

  (testing "successful step with resource and artifact execution"
    (eng/pull-image test-image)
    (u/with-runner-system
      (fn [database _ stream]
        (store/put database
                   "bob.resource-provider/git"
                   {:name "git"
                    :url "http://localhost:8000"}
                   "bob.artifact-store/local"
                   {:name "local"
                    :url "http://localhost:8001"}
                   "bob.pipeline/test:test"
                   {:group "test"
                    :name "test"
                    :steps []
                    :vars {}
                    :resources [{:name "source"
                                 :type "external"
                                 :provider "git"
                                 :params {:repo "https://github.com/bob-cd/bob"
                                          :branch "main"}}]
                    :image test-image})
        (let [initial-state {:image test-image
                             :mounted #{}
                             :run-id "r-a-full-run-id"
                             :env {}
                             :group "test"
                             :name "test"}
              step {:needs_resource "source"
                    :cmd "ls"
                    :produces_artifact {:path "README.md"
                                        :name "text"
                                        :store "local"}}
              final-state (p/exec-step {:database database :stream stream :logger-url logger-url} initial-state step)]
          (is (not (f/failed? final-state)))
          (is (contains? (:mounted final-state) "source"))
          (is (= 200
                 (:status (http/get "http://localhost:8001/bob_artifact/test/test/r-a-full-run-id/text")))))
        (p/gc-images "a-full-run-id")))))

(deftest ^:integration failed-step-executions
  (testing "reduces upon build failure"
    (is (reduced? (p/exec-step {:database :database :stream :stream} (f/fail "this is fine") {}))))

  (testing "wrong command step failure"
    (u/with-runner-system (fn [database _ stream]
                     (let [initial-state {:image test-image
                                          :mounted #{}
                                          :run-id "r-a-simple-run-id"
                                          :env {}
                                          :group "test"
                                          :name "test"}
                           step {:cmd "this-bombs"}
                           final-state (p/exec-step {:database database :stream stream} initial-state step)]
                       (is (f/failed? final-state)))
                     (p/gc-images "a-simple-run-id")))))

(deftest ^:integration pipeline-starts
  (testing "successful pipeline run"
    (u/with-runner-system
      (fn [database queue stream]
        (store/put database
                   "bob.logger/logger-local"
                   {:url logger-url
                    :name "logger-local"}
                   "bob.pipeline/test:test"
                   {:group "test"
                    :name "test"
                    :steps [{:cmd "echo hello"}
                            {:cmd "sh -c 'echo \"ENV: ${k1}\"'"}
                            {:cmd "sh -c 'echo \"ENV: ${k1} ${k2}\"'"
                             :vars {:k1 "v2" :k2 "v3"}}]
                    :vars {:k1 "v1"}
                    :image test-image})
        (let [run-id "r-a-run-id"
              _ (store/put database
                           (str "bob.pipeline.run/" run-id)
                           {:status :pending
                            :logger "logger-local"
                            :scheduled-at (Instant/now)
                            :group "test"
                            :name "test"})
              result @(p/start {:database database
                                :stream stream}
                               queue
                               {:group "test"
                                :name "test"
                                :logger "logger-local"
                                :run-id run-id}
                               {})
              lines (:body (http/get (str "http://localhost:8002/bob_logs/" run-id)))
              run-info (store/get-one database (str "bob.pipeline.run/" result))
              statuses (->> (history database (str "bob.pipeline.run/" result))
                            (map :status)
                            (into #{}))]
          (is (inst? (:scheduled-at run-info)))
          (is (inst? (:initiated-at run-info)))
          (is (inst? (:initialized-at run-info)))
          (is (inst? (:started-at run-info)))
          (is (inst? (:completed-at run-info)))
          (is (contains? statuses :pending))
          (is (contains? statuses :initializing))
          (is (contains? statuses :initialized))
          (is (contains? statuses :running))
          (is (contains? statuses :passed))
          (is (not (f/failed? result)))
          (is (str/includes? lines "ENV: v1"))
          (is (str/includes? lines "ENV: v2 v3"))
          (u/spec-assert :bob.pipeline/run run-info)))))

  (testing "failed pipeline run"
    (u/with-runner-system
      (fn [database queue stream]
        (store/put database
                   "bob.logger/logger-local"
                   {:url logger-url
                    :name "logger-local"}
                   "bob.pipeline/test:test"
                   {:group "test"
                    :name "test"
                    :steps [{:cmd "echo hello"} {:cmd "this-bombs"}]
                    :vars {:k1 "v1"}
                    :image test-image})
        (let [run-id "r-a-run-id"
              _ (store/put database
                           (str "bob.pipeline.run/" run-id)
                           {:status :pending
                            :scheduled-at (Instant/now)
                            :logger "logger-local"
                            :group "test"
                            :name "test"})
              result @(p/start {:database database
                                :stream stream}
                               queue
                               {:group "test"
                                :name "test"
                                :logger "logger-local"
                                :run-id run-id}
                               {})
              id (f/message result)
              run-info (store/get-one database (str "bob.pipeline.run/" id))
              statuses (->> (history database (str "bob.pipeline.run/" id))
                            (map :status)
                            (into #{}))]
          (u/spec-assert :bob.pipeline/run run-info)
          (is (inst? (:scheduled-at run-info)))
          (is (inst? (:initiated-at run-info)))
          (is (inst? (:initialized-at run-info)))
          (is (inst? (:started-at run-info)))
          (is (inst? (:completed-at run-info)))
          (is (f/failed? result))
          (is (contains? statuses :pending))
          (is (contains? statuses :initializing))
          (is (contains? statuses :initialized))
          (is (contains? statuses :running))
          (is (contains? statuses :failed)))))))

(deftest ^:integration pipeline-stop
  (testing "stopping a pipeline run"
    (u/with-runner-system
      (fn [database queue stream]
        (store/put database
                   "bob.logger/logger-local"
                   {:url logger-url
                    :name "logger-local"}
                   "bob.pipeline/test:stop-test"
                   {:group "test"
                    :name "stop-test"
                    :steps [{:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"}]
                    :vars {}
                    :image test-image})
        (let [run-id "r-a-stop-id"
              _ (store/put database
                           (str "bob.pipeline.run/" run-id)
                           {:status :pending
                            :scheduled-at (Instant/now)
                            :logger "logger-local"
                            :group "test"
                            :name "test"})
              _ (p/start {:database database
                          :stream stream}
                         queue
                         {:group "test"
                          :name "stop-test"
                          :logger "logger-local"
                          :run-id run-id}
                         {})
              _ (Thread/sleep 5000) ;; Longer, possibly flaky wait
              _ (p/stop {:database database
                         :stream stream}
                        queue
                        {:group "test"
                         :name "stop-test"
                         :run-id run-id}
                        {})
              run-info (store/get-one database "bob.pipeline.run/r-a-stop-id")
              statuses (->> (history database "bob.pipeline.run/r-a-stop-id")
                            (map :status)
                            (into #{}))]
          (u/spec-assert :bob.pipeline/run run-info)
          (is (inst? (:scheduled-at run-info)))
          (is (inst? (:initiated-at run-info)))
          (is (inst? (:initialized-at run-info)))
          (is (inst? (:started-at run-info)))
          (is (inst? (:completed-at run-info)))
          (is (not (contains? statuses :failed)))
          (is (contains? statuses :pending))
          (is (contains? statuses :initializing))
          (is (contains? statuses :initialized))
          (is (contains? statuses :running))
          (is (contains? statuses :stopped)))))))
