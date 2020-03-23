;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.pipeline.internals-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [taoensso.timbre :as log]
            [bob.test-utils :as tu]
            [bob.pipeline.internals :refer :all]
            [bob.util :as u]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]
            [bob.resource.core :as r]
            [bob.execution.internals :as e]
            [bob.artifact.core :as artifact]))

(deftest image-mark-and-sweep-test
  (log/merge-config! {:level :report})
  (testing "marking an image for the first time"
    (with-redefs [bob.pipeline.internals/images-produced (atom {})]
      (is (= {"build1" (list "image1")}
             (mark-image-for-gc "image1" "build1")))))

  (testing "marking the same image is idempotent"
    (with-redefs [bob.pipeline.internals/images-produced (atom {})]
      (is (= {"build1" (list "image1")}
             (mark-image-for-gc "image1" "build1")))))

  (testing "marking a new image for the same build"
    (with-redefs [bob.pipeline.internals/images-produced (atom {"build1" (list "image1")})]
      (is (= {"build1" (list "image2" "image1")}
             (mark-image-for-gc "image2" "build1")))))

  (testing "marking a new image for another same build"
    (with-redefs [bob.pipeline.internals/images-produced (atom {"build1" (list "image2" "image1")})]
      (is (= {"build1" (list "image2" "image1")
              "build2" (list "image1")}
             (mark-image-for-gc "image1" "build2"))))))

(deftest gc-images-test
  (testing "sweep images for build1"
    (with-redefs [bob.pipeline.internals/images-produced (atom {"build1" (list "image2" "image1")
                                                                "build2" (list "image1")})]
      (with-redefs-fn {#'e/delete-image (fn [image]
                                              (tu/check-and-fail
                                               #(some #{image} ["image1" "image2"])))}
        #(is (= {"build2" (list "image1")}
                (gc-images "build1")))))))

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
                                                  "image-id")
                     #'u/log-to-db              (constantly nil)}
      #(is (= "image-id"
              (resourceful-step {:needs_resource "git"} "test" "img" "build-id")))))

  (testing "ignore mount if not needed"
    (is (= "img" (resourceful-step {} "test" "img" "run-id")))))

(deftest next-step-execution
  (testing "next images generation with resource mount"
    (with-redefs-fn {#'e/commit-image     (fn [id cmd]
                                            (tu/check-and-fail
                                             #(and (= "id" id)
                                                   (= "hello" cmd))))
                     #'resourceful-step   (constantly "img")
                     #'e/create-container (constantly "id")
                     #'e/delete-container (constantly nil)
                     #'mark-image-for-gc  (constantly nil)}
      #(is (= {:id      "id"
               :mounted ["source"]}
              (next-step {:id "id" :mounted []}
                         {:needs_resource "source"
                          :cmd            "hello"}
                         {}
                         "test"
                         "run-id")))))

  (testing "next image generation without resource mount"
    (with-redefs-fn {#'e/commit-image     (fn [id cmd]
                                            (tu/check-and-fail
                                             #(and (= "id" id)
                                                   (= "hello" cmd))))
                     #'resourceful-step   (constantly "img")
                     #'e/create-container (constantly "id")
                     #'e/delete-container (constantly nil)
                     #'mark-image-for-gc  (constantly nil)}
      #(is (= {:id      "id"
               :mounted []}
              (next-step {:id "id" :mounted []}
                         {:cmd "hello"}
                         {}
                         "test"
                         "run-id")))))

  (testing "failed next image generation"
    (with-redefs-fn {#'e/commit-image     #(throw (Exception. "nope"))
                     #'resourceful-step   (constantly "img")
                     #'e/create-container (constantly "id")
                     #'mark-image-for-gc  (constantly nil)}
      #(is (f/failed? (next-step {:id "id" :mounted []}
                                 {:cmd "hello"}
                                 {}
                                 "test"
                                 "run-id"))))))

(deftest single-step-execution
  (testing "successful step execution with artifact upload"
    (let [test-step {:produces_artifact "jar"
                     :artifact_path     "path"
                     :artifact_store    "s3"}]
      (with-redefs-fn {#'next-step                (fn [id step evars pipeline run-id]
                                                    (tu/check-and-fail
                                                     #(and (= {:id "id"} id)
                                                           (= step test-step)
                                                           (= {} evars)
                                                           (= "dev:test" pipeline)
                                                           (= "id" run-id)))
                                                    {:id      "id"
                                                     :mounted []})
                       #'update-pid               (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'e/start-container        (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'docker/invoke            (fn [_ params]
                                                    (tu/check-and-fail
                                                     #(= "id" (:id (:params params))))
                                                    {:Config {:WorkingDir "/some"}})
                       #'artifact/upload-artifact (fn [group name number artifact id path store-name]
                                                    (tu/check-and-fail
                                                     #(and (= "dev" group)
                                                           (= "test" name)
                                                           (= 1 number)
                                                           (= "jar" artifact)
                                                           (= "id" id)
                                                           (= "/some/path" path)
                                                           (= "s3" store-name))))
                       #'u/log-to-db              (constantly nil)}
        #(is (= {:id      "id"
                 :mounted []}
                (exec-step "id" {} "dev:test" 1 {:id "id"} test-step))))))

  (testing "successful step execution without artifact upload"
    (let [test-step {}]
      (with-redefs-fn {#'next-step                (fn [id step evars pipeline run-id]
                                                    (tu/check-and-fail
                                                     #(and (= {:id "id"} id)
                                                           (= step test-step)
                                                           (= {} evars)
                                                           (= "dev:test" pipeline)
                                                           (= "id" run-id)))
                                                    {:id      "id"
                                                     :mounted []})
                       #'update-pid               (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'e/start-container        (fn [id run-id]
                                                    (tu/check-and-fail
                                                     #(and (= "id" id)
                                                           (= "id" run-id)))
                                                    "id")
                       #'docker/invoke            (fn [_ id]
                                                    (tu/check-and-fail
                                                     #(= "id" id))
                                                    {:Config {:WorkingDir "/some"}})
                       #'artifact/upload-artifact #(throw (Exception. "shouldn't be called"))}
        #(is (= {:id      "id"
                 :mounted []}
                (exec-step "id" {} "dev:test" 1 {:id "id"} test-step))))))

  (testing "non-zero step execution"
    (let [test-step {}
          nein      (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'next-step                nein
                       #'update-pid               nein
                       #'e/start-container        nein
                       #'docker/invoke            nein
                       #'artifact/upload-artifact nein}
        #(is (reduced? (exec-step "id" {} "dev:test" 1 (f/fail "shizz") test-step))))))

  (testing "failed step execution"
    (let [test-step {}
          nein      (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'next-step                (constantly (f/fail "shizz"))
                       #'update-pid               nein
                       #'e/start-container        nein
                       #'docker/invoke            nein
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
    (log/merge-config! {:level :report})
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
                       #'u/log-to-db          (constantly nil)
                       #'e/pull-image         (fn [img]
                                                (tu/check-and-fail
                                                 #(= "img" img))
                                                "img")
                       #'resourceful-step     (fn [step pipeline img run-id]
                                                (tu/check-and-fail
                                                 #(and (= first-step
                                                          step)
                                                       (= "test" pipeline)
                                                       (= "img" img)
                                                       (= "run-id" run-id)))
                                                "img")
                       #'e/create-container   (fn [img step evars]
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
                       #'e/start-container   (fn [id run-id]
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
                                                     args)))
                       #'e/delete-container   (constantly nil)
                       #'mark-image-for-gc    (constantly nil)}
        #(is (= "id" @(exec-steps "img"
                                  [first-step {:cmd "hello2"}]
                                  "test"
                                  {}))))))

  (testing "successful steps execution without first resource"
    (let [first-step {:cmd "hello"}]
      (log/merge-config! {:level :report})
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
                       #'u/log-to-db          (constantly nil)
                       #'e/pull-image          (fn [img]
                                                (tu/check-and-fail
                                                 #(= "img" img))
                                                "img")
                       #'resourceful-step     (fn [step pipeline img run-id]
                                                (tu/check-and-fail
                                                 #(and (= first-step
                                                          step)
                                                       (= "test" pipeline)
                                                       (= "img" img)
                                                       (= "run-id" run-id)))
                                                "img")
                       #'e/create-container   (fn [img step evars]
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
                       #'e/start-container   (fn [id run-id]
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
                                                     args)))
                       #'e/delete-container   (constantly nil)
                       #'mark-image-for-gc    (constantly nil)}
        #(is (= "id" @(exec-steps "img"
                                  [first-step {:cmd "hello2"}]
                                  "test"
                                  {}))))))

  (testing "unsuccessful steps execution"
    (log/merge-config! {:level :report})
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
                       #'u/log-to-db          (constantly nil)
                       #'e/pull-image         (constantly (f/fail "shizz"))
                       #'db/status-of         (constantly {:status "running"})
                       #'resourceful-step     nein
                       #'e/create-container   nein
                       #'update-pid           nein
                       #'e/start-container   nein
                       #'reduce               nein
                       #'db/update-run        nein
                       #'mark-image-for-gc    nein}
        #(is (f/failed? @(exec-steps "img"
                                     [first-step {:cmd "hello2"}]
                                     "test"
                                     {})))))))

(deftest stopping-pipeline
  (testing "successfully stop a running local pipeline from user request"
    (let [criteria {:pipeline "test"
                    :number   1}]
      (with-redefs-fn {#'db/status-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:status "running"})
                       #'db/stop-run       (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args)))
                       #'db/pid-of-run     (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:last_pid "pid"})
                       #'e/status-of       (fn [pid]
                                             (tu/check-and-fail
                                              #(= "pid" pid))
                                             {:running? true})
                       #'e/kill-container  (fn [pid]
                                             (tu/check-and-fail
                                              #(= "pid" pid)))
                       #'db/run-id-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:id "run-id"})
                       #'gc-images         (fn [run-id]
                                             (tu/check-and-fail
                                              #(= "run-id" run-id)))
                       #'container-in-node (constantly true)}
        #(is (= "Ok" (stop-pipeline "test" 1))))))

  (testing "successfully stop a running local pipeline from DB signal"
    (let [criteria {:pipeline "test"
                    :number   1}]
      (with-redefs-fn {#'db/status-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:status "running"})
                       #'db/stop-run       (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args)))
                       #'db/pid-of-run     (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:last_pid "pid"})
                       #'e/status-of       (fn [pid]
                                             (tu/check-and-fail
                                              #(= "pid" pid))
                                             {:running? true})
                       #'e/kill-container  (fn [pid]
                                             (tu/check-and-fail
                                              #(= "pid" pid)))
                       #'db/run-id-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:id "run-id"})
                       #'gc-images         (fn [run-id]
                                             (tu/check-and-fail
                                              #(= "run-id" run-id)))
                       #'container-in-node (constantly true)}
        #(is (= "Ok" (stop-pipeline "test" 1 true))))))

  (testing "successfully stop a running remote pipeline from user request"
    (let [criteria {:pipeline "test"
                    :number   1}
          nein     (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'container-in-node (constantly nil)
                       #'db/status-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:status "running"})
                       #'db/stop-run       (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args)))
                       #'db/pid-of-run     (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:last_pid "pid"})
                       #'e/status-of       nein
                       #'e/kill-container  nein
                       #'db/run-id-of      nein
                       #'gc-images         nein}
        #(is (= "Ok" (stop-pipeline "test" 1))))))

  (testing "ignoring a stop request for a remote pipeline from DB signal"
    (let [criteria {:pipeline "test"
                    :number   1}
          nein     (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'container-in-node (constantly nil)
                       #'db/pid-of-run     (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:last_pid "pid"})
                       #'db/stop-run       (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args)))
                       #'db/status-of      nein
                       #'e/status-of       nein
                       #'e/kill-container  nein
                       #'db/run-id-of      nein
                       #'gc-images         nein}
        #(is (nil? (stop-pipeline "test" 1 true))))))

  (testing "unsuccessfully stop a running pipeline"
    (let [criteria {:pipeline "test"
                    :number   1}
          nein     (fn [& _] (throw (Exception. "shouldn't be called")))]
      (with-redefs-fn {#'container-in-node (constantly true)
                       #'db/pid-of-run     (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:last_pid "pid"})
                       #'db/status-of      (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args))
                                             {:status "running"})
                       #'db/stop-run       (fn [_ args]
                                             (tu/check-and-fail
                                              #(= criteria args)))
                       #'e/status-of       (fn [& _] (throw (Exception. "nope")))
                       #'e/kill-container  nein}
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
      #(is (= "nope"
              (f/message (pipeline-logs "test" 1 0 2)))))))

(deftest pipeline-image-fetch
  (testing "successfully fetch the image of a pipeline"
    (with-redefs-fn {#'db/image-of (fn [_ args]
                                     (tu/check-and-fail
                                      #(= {:name "test"}
                                          args))
                                     {:image "img"})}
      #(is (= "img" (image-of "test")))))

  (testing "unsuccessfully fetch the image of a pipeline"
    (with-redefs-fn {#'db/image-of (constantly nil)}
      #(is (and (f/failed? (image-of "test"))
                (= "No such pipeline"
                   (f/message (image-of "test"))))))))

(deftest container-locality-test
  (testing "container is found locally"
    (with-redefs-fn {#'docker/invoke (constantly [{:Id "id1"}])}
      #(is (container-in-node "id"))))

  (testing "container is not found locally"
    (with-redefs-fn {#'docker/invoke (constantly [{:Id "crappy-id"}])}
      #(is (nil? (container-in-node "id"))))))

(deftest sync-action-test
  (letfn [(action-fn [] "acted")
          (signalling-fn [] "signalled")
          (nein [] (throw (Exception. "this shouldn't be called")))]
    (testing "not signalled and not local dispatch"
      (with-redefs-fn {#'container-in-node (constantly false)}
        #(is (= "signalled" (sync-action false "id" nein signalling-fn)))))

    (testing "not signalled and local dispatch"
      (with-redefs-fn {#'container-in-node (constantly true)}
        #(is (= "signalled" (sync-action false "id" action-fn signalling-fn)))))

    (testing "signalled and local dispatch"
      (with-redefs-fn {#'container-in-node (constantly true)}
        #(is (= "acted" (sync-action true "id" action-fn nein)))))

    (testing "signalled and not local dispatch"
      (with-redefs-fn {#'container-in-node (constantly false)}
        #(is (nil? (sync-action true "id" nein nein)))))))
