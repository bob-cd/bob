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
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.pipeline.internals-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [clojure.core.async :as a]
            [bob.test-utils :as tu]
            [bob.pipeline.internals :refer :all]
            [bob.util :as u]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]
            [bob.resource.core :as r]
            [bob.execution.internals :as e]
            [bob.artifact.core :as artifact]))

(deftest pid-updates
  (testing "successful container id update"
    (with-redefs-fn {#'db/update-runs (fn [_ args]
                                        (tu/check-and-fail
                                         #(= {:pid "p1"
                                              :id  "r1"}
                                             args)))}
      #(is (= "p1" (update-pid "p1" "r1")))))

  (testing "unsuccessful container id update"
    (with-redefs-fn {#'db/update-runs (fn [& _] (throw (Exception. "nope")))}
      #(is (f/failed? (update-pid "p1" "r1"))))))

(deftest mounted-images
  (testing "mount resource image if needed"
    (with-redefs-fn {#'rdb/resource-by-pipeline (fn [_ args]
                                                  (tu/check-and-fail
                                                   #(= {:name     "git"
                                                        :pipeline "test"}
                                                       args))
                                                  {:name "git"
                                                   :url  "some-url"})
                     #'r/mounted-image-from     (fn [resource pipeline img]
                                                  (tu/check-and-fail
                                                   #(and (= {:name "git"
                                                             :url  "some-url"}
                                                            resource)
                                                         (= "test" pipeline)
                                                         (= "img" img)))
                                                  "image-id")}
      #(is (= "image-id"
              (resourceful-step {:needs_resource "git"} "test" "img")))))

  (testing "ignore mount if not needed"
    (is (= "img" (resourceful-step {} "test" "img")))))

(deftest next-step-execution
  (testing "next images generation with resource mount"
    (with-redefs-fn {#'docker/commit-container (fn [_ id repo tag cmd]
                                                 (tu/check-and-fail
                                                  #(and (= "id" id)
                                                        (clojure.string/starts-with? repo id)
                                                        (= "latest" tag)
                                                        (= "hello" cmd)))
                                                 "img")
                     #'resourceful-step        (constantly "img")
                     #'e/build                 (constantly "id")}
      #(is (= {:id      "id"
               :mounted ["source"]}
              (next-step {:id "id" :mounted []}
                         {:needs_resource "source"
                          :cmd            "hello"}
                         {}
                         "test")))))

  (testing "next image generation without resource mount"
    (with-redefs-fn {#'docker/commit-container (fn [_ id repo tag cmd]
                                                 (tu/check-and-fail
                                                  #(and (= "id" id)
                                                        (clojure.string/starts-with? repo id)
                                                        (= "latest" tag)
                                                        (= "hello" cmd)))
                                                 "img")
                     #'resourceful-step        (constantly "img")
                     #'e/build                 (constantly "id")}
      #(is (= {:id      "id"
               :mounted []}
              (next-step {:id "id" :mounted []}
                         {:cmd "hello"}
                         {}
                         "test")))))

  (testing "failed next image generation"
    (with-redefs-fn {#'docker/commit-container #(throw (Exception. "nope"))
                     #'resourceful-step        (constantly "img")
                     #'e/build                 (constantly "id")}
      #(is (f/failed? (next-step {:id "id" :mounted []}
                                 {:cmd "hello"}
                                 {}
                                 "test"))))))

(deftest single-step-execution
  (testing "successful step execution with artifact upload"
    (let [test-step {:produces_artifact "jar"
                     :artifact_path     "path"}]
      (with-redefs-fn {#'db/run-stopped?          (fn [_ args]
                                                    (tu/check-and-fail
                                                     #(= {:id "id"} args))
                                                    {:stopped false})
                       #'next-step                (fn [id step evars pipeline]
                                                    (tu/check-and-fail
                                                     #(and (= {:id "id"} id)
                                                           (= step test-step)
                                                           (= {} evars)
                                                           (= "dev:test" pipeline)))
                                                    {:id      "id"
                                                     :mounted []})
                       #'update-pid               (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'e/run                    (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'docker/inspect           (fn [_ id]
                                                    (tu/check-and-fail
                                                     #(= "id" id))
                                                    {:Config {:WorkingDir "/some"}})
                       #'artifact/upload-artifact (fn [group name number artifact id path]
                                                    (tu/check-and-fail
                                                     #(and (= "dev" group)
                                                           (= "test" name)
                                                           (= 1 number)
                                                           (= "jar" artifact)
                                                           (= "id" id)
                                                           (= "/some/path" path))))}
        #(is (= {:id      "id"
                 :mounted []}
                (exec-step "id" {} "dev:test" 1 {:id "id"} test-step))))))

  (testing "successful step execution without artifact upload"
    (let [test-step {}]
      (with-redefs-fn {#'db/run-stopped?          (fn [_ args]
                                                    (tu/check-and-fail
                                                     #(= {:id "id"} args))
                                                    {:stopped false})
                       #'next-step                (fn [id step evars pipeline]
                                                    (tu/check-and-fail
                                                     #(and (= {:id "id"} id)
                                                           (= step test-step)
                                                           (= {} evars)
                                                           (= "dev:test" pipeline)))
                                                    {:id      "id"
                                                     :mounted []})
                       #'update-pid               (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'e/run                    (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'docker/inspect           (fn [_ id]
                                                    (tu/check-and-fail
                                                     #(= "id" id))
                                                    {:Config {:WorkingDir "/some"}})
                       #'artifact/upload-artifact #(throw (Exception. "shouldn't be called"))}
        #(is (= {:id      "id"
                 :mounted []}
                (exec-step "id" {} "dev:test" 1 {:id "id"} test-step))))))

  (testing "stopped step execution"
    (let [test-step {}
          nein      (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'db/run-stopped?          (constantly {:stopped true})
                       #'next-step                nein
                       #'update-pid               nein
                       #'e/run                    nein
                       #'docker/inspect           nein
                       #'artifact/upload-artifact nein}
        #(is (reduced? (exec-step "id" {} "dev:test" 1 {:id "id"} test-step))))))

  (testing "non-zero step execution"
    (let [test-step {}
          nein      (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'db/run-stopped?          (constantly {:stopped false})
                       #'next-step                nein
                       #'update-pid               nein
                       #'e/run                    nein
                       #'docker/inspect           nein
                       #'artifact/upload-artifact nein}
        #(is (reduced? (exec-step "id" {} "dev:test" 1 {:id (f/fail "shizz")} test-step))))))

  (testing "failed step execution"
    (let [test-step {}
          nein      (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'db/run-stopped?          (constantly {:stopped false})
                       #'next-step                (constantly (f/fail "shizz"))
                       #'update-pid               nein
                       #'e/run                    nein
                       #'docker/inspect           nein
                       #'artifact/upload-artifact nein}
        #(is (f/failed? (exec-step "id" {} "dev:test" 1 {:id "id"} test-step)))))))

(deftest next-build-number-generation
  (testing "successful initial build number generation"
    (with-redefs-fn {#'db/pipeline-runs (fn [_ name]
                                          (tu/check-and-fail
                                           #(= {:pipeline "test"} name)))}
      #(is (= 1 (next-build-number-of "test")))))

  (testing "successful next build number generation"
    (with-redefs-fn {#'db/pipeline-runs (fn [_ name]
                                          (tu/check-and-fail
                                           #(= {:pipeline "test"} name))
                                          [{:number 1}])}
      #(is (= 2 (next-build-number-of "test")))))

  (testing "unsuccessful next build number generation"
    (with-redefs-fn {#'db/pipeline-runs #(throw (Exception. "nope"))}
      #(is (f/failed? (next-build-number-of "test"))))))

(deftest steps-execution
  (testing "successful steps execution with first resource"
    (let [first-step {:cmd            "hello"
                      :needs_resource "src"}]
      (with-redefs-fn {#'u/get-id             (constantly "run-id")
                       #'next-build-number-of (fn [name]
                                                (tu/check-and-fail
                                                 #(= "test" name))
                                                1)
                       #'db/insert-run        (fn [_ args]
                                                (tu/check-and-fail
                                                 #(= {:id       "run-id"
                                                      :number   1
                                                      :pipeline "test"
                                                      :status   "running"}
                                                     args)))
                       #'e/pull               (fn [img]
                                                (tu/check-and-fail
                                                 #(= "img" img))
                                                "img")
                       #'resourceful-step     (fn [step pipeline img]
                                                (tu/check-and-fail
                                                 #(and (= first-step
                                                          step)
                                                       (= "test" pipeline)
                                                       (= "img" img)))
                                                "img")
                       #'e/build              (fn [img step evars]
                                                (tu/check-and-fail
                                                 #(and (= "img" img)
                                                       (= first-step
                                                          step)
                                                       (= {} evars)))
                                                "id")
                       #'update-pid           (fn [id run-id]
                                                (tu/check-and-fail
                                                 #(and (= "id" id)
                                                       (= "run-id" run-id)))
                                                "id")
                       #'e/run                (fn [id run-id]
                                                (tu/check-and-fail
                                                 #(and (= "id" id)
                                                       (= "run-id" run-id)))
                                                "id")
                       #'reduce               (fn [pred accum steps]
                                                (tu/check-and-fail
                                                 #(and (fn? pred)
                                                       (= {:id      "id"
                                                           :mounted ["src"]}
                                                          accum)
                                                       (= [{:cmd "hello2"}]
                                                          steps)))
                                                "id")
                       #'db/update-run        (fn [_ args]
                                                (tu/check-and-fail
                                                 #(= {:status "passed"
                                                      :id     "run-id"}
                                                     args)))}
        #(is (= "id" (a/<!! (exec-steps "img"
                                        [first-step {:cmd "hello2"}]
                                        "test"
                                        {})))))))

  (testing "successful steps execution without first resource"
    (let [first-step {:cmd "hello"}]
      (with-redefs-fn {#'u/get-id             (constantly "run-id")
                       #'next-build-number-of (fn [name]
                                                (tu/check-and-fail
                                                 #(= "test" name))
                                                1)
                       #'db/insert-run        (fn [_ args]
                                                (tu/check-and-fail
                                                 #(= {:id       "run-id"
                                                      :number   1
                                                      :pipeline "test"
                                                      :status   "running"}
                                                     args)))
                       #'e/pull               (fn [img]
                                                (tu/check-and-fail
                                                 #(= "img" img))
                                                "img")
                       #'resourceful-step     (fn [step pipeline img]
                                                (tu/check-and-fail
                                                 #(and (= first-step
                                                          step)
                                                       (= "test" pipeline)
                                                       (= "img" img)))
                                                "img")
                       #'e/build              (fn [img step evars]
                                                (tu/check-and-fail
                                                 #(and (= "img" img)
                                                       (= first-step
                                                          step)
                                                       (= {} evars)))
                                                "id")
                       #'update-pid           (fn [id run-id]
                                                (tu/check-and-fail
                                                 #(and (= "id" id)
                                                       (= "run-id" run-id)))
                                                "id")
                       #'e/run                (fn [id run-id]
                                                (tu/check-and-fail
                                                 #(and (= "id" id)
                                                       (= "run-id" run-id)))
                                                "id")
                       #'reduce               (fn [pred accum steps]
                                                (tu/check-and-fail
                                                 #(and (fn? pred)
                                                       (= {:id      "id"
                                                           :mounted []}
                                                          accum)
                                                       (= [{:cmd "hello2"}]
                                                          steps)))
                                                "id")
                       #'db/update-run        (fn [_ args]
                                                (tu/check-and-fail
                                                 #(= {:status "passed"
                                                      :id     "run-id"}
                                                     args)))}
        #(is (= "id" (a/<!! (exec-steps "img"
                                        [first-step {:cmd "hello2"}]
                                        "test"
                                        {})))))))

  (testing "unsuccessful steps execution"
    (let [first-step {:cmd "hello"}
          nein       (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'u/get-id             (constantly "run-id")
                       #'next-build-number-of (fn [name]
                                                (tu/check-and-fail
                                                 #(= "test" name))
                                                1)
                       #'db/insert-run        (fn [_ args]
                                                (tu/check-and-fail
                                                 #(= {:id       "run-id"
                                                      :number   1
                                                      :pipeline "test"
                                                      :status   "running"}
                                                     args)))
                       #'e/pull               (constantly (f/fail "shizz"))
                       #'resourceful-step     nein
                       #'e/build              nein
                       #'update-pid           nein
                       #'e/run                nein
                       #'reduce               nein
                       #'db/update-run        nein}
        #(is (f/failed? (a/<!! (exec-steps "img"
                                           [first-step {:cmd "hello2"}]
                                           "test"
                                           {}))))))))

(deftest stopping-pipeline
  (testing "successfully stop a running pipeline"
    (let [criteria {:pipeline "test"
                    :number   1}]
      (with-redefs-fn {#'db/status-of     (fn [_ args]
                                            (tu/check-and-fail
                                             #(= criteria args))
                                            {:status "running"})
                       #'db/stop-run      (fn [_ args]
                                            (tu/check-and-fail
                                             #(= criteria args)))
                       #'db/pid-of-run    (fn [_ args]
                                            (tu/check-and-fail
                                             #(= criteria args))
                                            {:last_pid "pid"})
                       #'e/status-of      (fn [pid]
                                            (tu/check-and-fail
                                             #(= "pid" pid))
                                            {:running? true})
                       #'e/kill-container (fn [pid]
                                            (tu/check-and-fail
                                             #(= "pid" pid)))}
        #(is (= "Ok" (stop-pipeline "test" 1))))))

  (testing "unsuccessfully stop a running pipeline"
    (let [criteria {:pipeline "test"
                    :number   1}
          nein     (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'db/status-of     (fn [_ args]
                                            (tu/check-and-fail
                                             #(= criteria args))
                                            {:status "running"})
                       #'db/stop-run      (fn [& _]
                                            (throw (Exception. "nope")))
                       #'db/pid-of-run    nein
                       #'e/status-of      nein
                       #'e/kill-container nein}
        #(is (= "nope" (stop-pipeline "test" 1))))))

  (testing "stop a stopped pipeline"
    (let [criteria {:pipeline "test"
                    :number   1}
          nein     (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'db/status-of     (fn [_ args]
                                            (tu/check-and-fail
                                             #(= criteria args))
                                            {:status "passed"})
                       #'db/stop-run      nein
                       #'db/pid-of-run    nein
                       #'e/status-of      nein
                       #'e/kill-container nein}
        #(is (nil? (stop-pipeline "test" 1)))))))

(deftest logging-pipeline
  (testing "successfully fetch logs"
    (with-redefs-fn {#'db/run-id-of (fn [_ args]
                                      (tu/check-and-fail
                                       #(= {:pipeline "test"
                                            :number   1}
                                           args))
                                      {:id "run-id"})
                     #'db/logs-of   (fn [_ args]
                                      (tu/check-and-fail
                                       #(= {:run-id "run-id"}
                                           args))
                                      {:content "log1\nlog2"})}
      #(is (= ["log1" "log2"] (pipeline-logs "test" 1 0 2)))))

  (testing "unsuccessfully fetch logs"
    (with-redefs-fn {#'db/run-id-of    (fn [& _] (throw (Exception. "nope")))}
      #(is (= "nope" (pipeline-logs "test" 1 0 2))))))

(deftest pipeline-image-fetch
  (testing "successfully fetch the image of a pipeline"
    (with-redefs-fn {#'db/image-of (fn [_ args]
                                     (tu/check-and-fail
                                      #(= {:name "test"}
                                          args))
                                     {:image "img"})}
      #(is (= "img" (image-of "test")))))

  (testing "unsuccessfully fetch the image of a pipeline"
    (with-redefs-fn {#'db/image-of (fn [& _] (throw (Exception. "nope")))}
      #(is (f/failed? (image-of "test"))))))
