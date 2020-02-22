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

(ns bob.pipeline.core-test
  (:require [clojure.test :refer :all]
            [bob.test-utils :as tu]
            [bob.pipeline.core :refer :all]
            [bob.pipeline.internals :as p]
            [bob.pipeline.db :as db]
            [bob.resource.db :as rdb]
            [bob.resource.internals :as ri]))

(deftest pipeline-creation
  (testing "successfully create a pipeline with used resources and evars"
    (with-redefs-fn {#'db/insert-pipeline  (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:name  "dev:test"
                                                    :image "img"}
                                                   args)))
                     #'rdb/insert-resource (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:name     "src"
                                                    :type     "external"
                                                    :pipeline "dev:test"
                                                    :provider "git"}
                                                   args)))
                     #'ri/add-params       (fn [& args]
                                             (tu/check-and-fail
                                               #(= ["src"
                                                    {:url    "https://test.com"
                                                     :branch "master"}
                                                    "dev:test"]
                                                   args)))
                     #'db/insert-evars     (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:evars [["k1" "v1" "dev:test"]]}
                                                   args)))
                     #'db/insert-step      (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:cmd               "hello"
                                                    :needs_resource    "src"
                                                    :produces_artifact "jar"
                                                    :artifact_path     "path"
                                                    :artifact_store    "s3"
                                                    :pipeline          "dev:test"}
                                                   args)))
                     #'db/delete-pipeline  (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))}
      #(is (= "Ok"
              (-> @(create "dev"
                           "test"
                           [{:cmd               "hello"
                             :needs_resource    "src"
                             :produces_artifact {:name  "jar"
                                                 :path  "path"
                                                 :store "s3"}}]
                           {:k1 "v1"}
                           [{:name     "src"
                             :provider "git"
                             :params   {:url    "https://test.com"
                                        :branch "master"}
                             :type     "external"}]
                           "img")
                  :body
                  :message)))))

  (testing "successfully create a pipeline without resources and with evars"
    (with-redefs-fn {#'db/insert-pipeline  (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:name  "dev:test"
                                                    :image "img"}
                                                   args)))
                     #'rdb/insert-resource (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'ri/add-params       (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'db/insert-evars     (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:evars [["k1" "v1" "dev:test"]]}
                                                   args)))
                     #'db/insert-step      (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:cmd               "hello"
                                                    :needs_resource    nil
                                                    :produces_artifact "jar"
                                                    :artifact_path     "path"
                                                    :artifact_store    "s3"
                                                    :pipeline          "dev:test"}
                                                   args)))
                     #'db/delete-pipeline  (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))}
      #(is (= "Ok"
              (-> @(create "dev"
                           "test"
                           [{:cmd               "hello"
                             :produces_artifact {:name  "jar"
                                                 :path  "path"
                                                 :store "s3"}}]
                           {:k1 "v1"}
                           []
                           "img")
                  :body
                  :message)))))

  (testing "successfully create a pipeline without resources or evars"
    (with-redefs-fn {#'db/insert-pipeline  (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:name  "dev:test"
                                                    :image "img"}
                                                   args)))
                     #'rdb/insert-resource (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'ri/add-params       (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'db/insert-evars     (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'db/insert-step      (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:cmd               "hello"
                                                    :needs_resource    nil
                                                    :produces_artifact "jar"
                                                    :artifact_path     "path"
                                                    :artifact_store    "s3"
                                                    :pipeline          "dev:test"}
                                                   args)))
                     #'db/delete-pipeline  (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))}
      #(is (= "Ok"
              (-> @(create "dev"
                           "test"
                           [{:cmd               "hello"
                             :produces_artifact {:name  "jar"
                                                 :path  "path"
                                                 :store "s3"}}]
                           {}
                           []
                           "img")
                  :body
                  :message)))))

  (testing "unsuccessfully create a pipeline"
    (with-redefs-fn {#'db/insert-pipeline  (fn [& _]
                                             (throw (Exception. "shizzz")))
                     #'rdb/insert-resource (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'ri/add-params       (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'db/insert-evars     (fn [& _]
                                             (throw (Exception. "this shouldn't be called")))
                     #'db/insert-step      (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:cmd               "hello"
                                                    :needs_resource    nil
                                                    :produces_artifact "jar"
                                                    :artifact_path     "path"
                                                    :pipeline          "dev:test"}
                                                   args)))
                     #'db/delete-pipeline  (fn [_ args]
                                             (tu/check-and-fail
                                               #(= {:pipeline "dev:test"}
                                                   args)))}
      #(is (= "Pipeline creation error: Check params or if its already created"
              (-> @(create "dev"
                           "test"
                           [{:cmd               "hello"
                             :produces_artifact {:name "jar"
                                                 :path "path"}}]
                           {}
                           []
                           "img")
                  :body
                  :message))))))

(deftest pipeline-start
  (testing "successfully start a pipeline"
    (with-redefs-fn {#'p/image-of           (fn [pipeline]
                                              (tu/check-and-fail
                                                #(= "dev:test" pipeline))
                                              "img")
                     #'db/ordered-steps     (fn [_ args]
                                              (tu/check-and-fail
                                                #(= {:pipeline "dev:test"}
                                                    args))
                                              [{:cmd "test"}])
                     #'db/evars-by-pipeline (fn [_ args]
                                              (tu/check-and-fail
                                                #(= {:pipeline "dev:test"}
                                                    args))
                                              [{:key "k1" :value "v1"}])
                     #'p/exec-steps         (fn [& args]
                                              (tu/check-and-fail
                                                #(= ["img" [{:cmd "test"}] "dev:test" {:k1 "v1"}]
                                                    args)))}
      #(is (= "Ok" (-> @(start "dev" "test")
                       :body
                       :message)))))

  (testing "unsuccessfully start a pipeline"
    (with-redefs-fn {#'p/image-of           (fn [pipeline]
                                              (tu/check-and-fail
                                                #(= "dev:test" pipeline))
                                              "img")
                     #'db/ordered-steps     (fn [& _]
                                              (throw (Exception. "shizzz")))
                     #'db/evars-by-pipeline (fn [_ args]
                                              (tu/check-and-fail
                                                #(= {:pipeline "dev:test"}
                                                    args))
                                              [{:key "k1" :value "v1"}])
                     #'p/exec-steps         (fn [& args]
                                              (tu/check-and-fail
                                                #(= ["img" [{:cmd "test"}] "dev:test" {:k1 "v1"}]
                                                    args)))}
      #(is (= "shizzz" (-> @(start "dev" "test")
                           :body
                           :message))))))

(deftest pipeline-stop
  (testing "successfully stopping a pipeline"
    (with-redefs-fn {#'p/stop-pipeline (fn [pipeline number]
                                         (tu/check-and-fail
                                           #(and (= "dev:test" pipeline)
                                                 (= 1 number)))
                                         "Ok")}
      #(is (= "Ok" (-> @(stop "dev" "test" 1)
                       :body
                       :message)))))

  (testing "unsuccessfully stopping a pipeline"
    (with-redefs-fn {#'p/stop-pipeline (constantly nil)}
      #(is (= "Pipeline not running" (-> @(stop "dev" "test" 1)
                                         :body
                                         :message))))))

(deftest pipeline-status
  (testing "successful status fetch"
    (with-redefs-fn {#'db/status-of (fn [_ args]
                                      (tu/check-and-fail
                                        #(= {:pipeline "dev:test"
                                             :number   1}
                                            args))
                                      {:status "running"})}
      #(is (= :running
              (-> @(status "dev" "test" 1)
                  :body
                  :message)))))

  (testing "unsuccessful status fetch"
    (with-redefs-fn {#'db/status-of (constantly nil)}
      #(is (= "No such pipeline"
              (-> @(status "dev" "test" 1)
                  :body
                  :message))))))

(deftest pipeline-remove
  (testing "successful pipeline removal"
    (with-redefs-fn {#'db/delete-pipeline (fn [_ args]
                                            (tu/check-and-fail
                                              #(= {:pipeline "dev:test"}
                                                  args)))}
      #(is (= "Ok"
              (-> @(remove-pipeline "dev" "test")
                  :body
                  :message)))))

  (testing "suppressed unsuccessful pipeline removal"
    (with-redefs-fn {#'db/delete-pipeline (fn [& _]
                                            (throw (Exception. "shizzz")))}
      #(is (= "Ok"
              (-> @(remove-pipeline "dev" "test")
                  :body
                  :message))))))

(deftest pipeline-logs
  (testing "successful logs fetch"
    (with-redefs-fn {#'p/pipeline-logs (fn [pipeline number offset lines]
                                         (tu/check-and-fail
                                           #(and (= "dev:test" pipeline)
                                                 (= 1 number)
                                                 (= 0 offset)
                                                 (= 100 lines)))
                                         ["line1" "line2"])}
      #(is (= ["line1" "line2"]
              (-> @(logs-of "dev" "test" 1 0 100)
                  :body
                  :message)))))

  (testing "unsuccessful logs fetch"
    (with-redefs-fn {#'p/pipeline-logs (constantly "shizzz")}
      #(is (= "shizzz"
              (-> @(logs-of "dev" "test" 1 0 100)
                  :body
                  :message))))))

(deftest get-pipeleines
  (testing "Filter pipelines"
    (with-redefs-fn {#'db/get-pipelines          (fn [_ filter]
                                                   (tu/check-and-fail
                                                     #(= {:pipeline nil
                                                          :status   nil}
                                                         filter))
                                                   [{:name "test:Test" :image "test 1.7"}])
                     #'db/ordered-steps          (fn [_ filter]
                                                   (tu/check-and-fail
                                                     #(= {:pipeline "test:Test"}
                                                         filter))
                                                   [{
                                                     :id                1
                                                     :cmd               "echo hello"
                                                     :pipeline          "dev:test"
                                                     :needs_resource    nil
                                                     :produces_artifact "afile"
                                                     :artifact_path     "test.txt"
                                                     :artifact_store    "local"}
                                                    {:cmd "mkdir"}])
                     #'rdb/resources-by-pipeline (fn [_ filter]
                                                   (tu/check-and-fail
                                                     #(= {:pipeline "test:Test"}
                                                         filter))
                                                   [{:id       1
                                                     :provider "git"
                                                     :name     "src"
                                                     :pipeline "test:Test"}])
                     #'rdb/resource-params-of    (fn [_ filter]
                                                   (tu/check-and-fail
                                                     #(= {:name     "src"
                                                          :pipeline "test:Test"}
                                                         filter))
                                                   [{
                                                     :name     "git"
                                                     :key      "env"
                                                     :value    "dev"
                                                     :pipeline "test:Test"}])}
      #(is (= [{:name "test:Test",
                :data
                      {:image     "test 1.7",
                       :steps
                                  [{:cmd "echo hello",
                                    :produces_artifact
                                         {:name "afile", :path "test.txt", :store "local"}}
                                   {:cmd "mkdir"}],
                       :resources [{:name     "src"
                                    :type     nil
                                    :provider "git"
                                    :params   {:env "dev"}}]}}]
              (-> @(get-pipelines nil nil nil)
                  :body)))))

  (testing "Empty result returns empty "
    (with-redefs-fn {#'db/get-pipelines (fn [_ filter]
                                          nil)}
      #(is (= []
              (-> @(get-pipelines "dev" "test" nil)
                  :body)))))

  (testing "Test filters created correctly"
    (with-redefs-fn {#'db/get-pipelines (fn [_ filter-map]
                                          (tu/check-and-fail
                                            #(= {:pipeline "dev:test"
                                                 :status   nil}
                                                filter-map))
                                          nil)}
      #(get-pipelines "dev" "test" nil))
    (with-redefs-fn {#'db/get-pipelines (fn [_ filter]
                                          (tu/check-and-fail
                                            #(= {:name   "dev:test"
                                                 :status "pass"}
                                                filter))
                                          nil)}
      #(get-pipelines "dev" "test" "pass"))
    (with-redefs-fn {#'db/get-pipelines (fn [_ filter]
                                          (tu/check-and-fail
                                            #(= {:name   "test"
                                                 :status "pass"}
                                                filter))
                                          nil)}
      #(get-pipelines nil "test" "pass"))))
