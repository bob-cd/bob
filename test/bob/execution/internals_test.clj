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

(ns bob.execution.internals-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.test-utils :as tu]
            [bob.execution.internals :refer :all]))

(deftest docker-image-presence
  (testing "image present"
    (with-redefs-fn {#'docker/invoke (constantly [{:RepoTags ["clojure:latest"]}])}
      #(is (= "clojure:latest" (has-image "clojure:latest")))))

  (testing "image present no tag provided"
    (with-redefs-fn {#'docker/invoke (constantly [{:RepoTags ["clojure:latest"]}])}
      #(let [result (has-image "clojure")]
         (is (and (f/failed? result)
                  (= "Failed to find clojure locally"
                     (f/message result)))))))

  (testing "image absent"
    (with-redefs-fn {#'docker/invoke (constantly [{:RepoTags ["clojure:foo"]}])}
      #(let [result (has-image "clojure:latest")]
         (is (and (f/failed? result)
                  (= "Failed to find clojure:latest locally"
                     (f/message result))))))))

(deftest container-kill
  (testing "successful kill"
    (with-redefs-fn {#'docker/invoke (constantly "")}
      #(is (= "c1" (kill-container "c1")))))

  (testing "unsuccessful kill"
    (with-redefs-fn {#'docker/invoke (constantly "{:message \"Cannot kill container\"}")}
      #(let [result (kill-container "c1")]
         (is (and (f/failed? result)
                  (= "Could not kill c1"
                     (f/message result))))))))

(deftest image-pull
  (testing "successful pull"
    (with-redefs-fn {#'has-image   (constantly true)
                     #'docker/invoke (fn [_ name]
                                       (tu/check-and-fail
                                        #(= "img" name)))}
      #(is (= "img" (pull-image "img")))))

  (testing "unsuccessful pull"
    (with-redefs-fn {#'has-image   (constantly (f/fail "Failed"))
                     #'docker/invoke (fn [_ _] {:message "failed"})}
      #(let [result (pull-image "clojure:foo")]
         (is (and (f/failed? result)
                  (= "Could not pull image: clojure:foo"
                     (f/message result)))))))

  (testing "missing tag in image name"
    (with-redefs-fn {#'docker/invoke (constantly {:message "failed"})}
      #(let [result (pull-image "clojure")]
         (is (f/failed? result)))))

  (testing "image already locally available"
    (with-redefs-fn {#'has-image   (constantly true)}
      #(is (= "img" (pull-image "img"))))))

(deftest container-build
  (testing "successful build"
    (with-redefs-fn {#'docker/invoke (constantly {:Id "83996a1d", :Warnings []})}
      #(is (= "83996a1d" (create-container "foo:bar"
                                           {:needs_resource "source"
                                            :cmd "ls"}
                                           {:FOO "bar"})))))

  (testing "successful build single param"
    (with-redefs-fn {#'docker/invoke (constantly {:Id "83996a1d", :Warnings []})}
      #(is (= "83996a1d" (create-container "foo:bar")))))

  (testing "successful build double param"
    (with-redefs-fn {#'docker/invoke (constantly {:Id "83996a1d", :Warnings []})}
      #(is (= "83996a1d" (create-container "foo:bar" {:cmd "ls"})))))

  (testing "failing build"
    (with-redefs-fn {#'docker/invoke (constantly {:message "failed"})}
      #(is (f/failed? (create-container "foo:bar"
                                        {:needs_resource "source"
                                         :cmd "ls"}
                                        {:FOO "bar"}))))))

(deftest container-status
  (testing "successful status fetch"
    (with-redefs-fn {#'docker/invoke (constantly {:State {:Running  false
                                                          :ExitCode 0}})}
      #(is (= {:running? false :exit-code 0} (status-of "id")))))

  (testing "unsuccessful status fetch"
    (with-redefs-fn {#'docker/invoke (fn [_ id] {:message (format "No such container: %s" id)})}
      #(is (f/failed? (status-of "id"))))))

(deftest container-starts
  (testing "successful start"
    (let [id "11235813213455"]
      (with-redefs-fn {#'docker/invoke (fn [_ cid]
                                         (tu/check-and-fail
                                          #(= id (-> cid
                                                     :params
                                                     :id)))
                                         {:StatusCode 0})
                       #'logs-live (constantly true)}
        #(is (= "112358132134" (start-container id "run-id"))))))

  (testing "successful start, non-zero exit"
    (let [id "11235813213455"]
      (with-redefs-fn {#'docker/invoke (fn [_ cid]
                                         (tu/check-and-fail
                                          #(= id (-> cid
                                                     :params
                                                     :id)))
                                         {:StatusCode 1})
                       #'logs-live (constantly true)}
        #(let [result (start-container id "run-id")]
           (is (and (f/failed? result)))))))

  (testing "unsuccessful start"
    (with-redefs-fn {#'logs-live (constantly true)
                     #'docker/invoke (fn [_ _] (throw (Exception. "Failed")))}
      #(is (f/failed? (start-container "id" "run-id"))))))

(deftest container-deletion
  (testing "successful deletion of container"
    (with-redefs-fn {#'docker/invoke (constantly "")}
      #(is (= "" (delete-container 1)))))

  (testing "successful force deletion of running container"
    (with-redefs-fn {#'docker/invoke (constantly "")}
      #(is (= "" (delete-container 1 :force)))))

  (testing "failed deletion of running container"
    (with-redefs-fn {#'docker/invoke (constantly {:message "Failed"})}
      #(is (f/failed? (delete-container 1))))))

(deftest test-commit-image
  (testing "Successful creation of new image from container"
    (with-redefs-fn {#'docker/invoke (constantly {:Id "sha256:f2763f84"})}
      #(is (= "sha256:f2763f84" (commit-image "12121212" "foo")))))

  (testing "Failed creation of new image from container"
    (with-redefs-fn {#'docker/invoke (constantly {:message "foobar"})}
      #(is (f/failed? (commit-image "12121212" "foo"))))))

(deftest test-delete-image
  (testing "Successful deletion of image"
    (let [image "foobar:1.1.0"]
      (with-redefs-fn {#'docker/invoke (fn [_ opmap]
                                         (tu/check-and-fail
                                          #(= image (-> opmap
                                                        :params
                                                        :name)))
                                         [{:Untagged image} {:Deleted image}])}
        #(is (= (delete-image image) [{:Untagged image} {:Deleted image}])))))

  (testing "Failed deletion of image"
    (let [image "foobar:1.1.0"]
      (with-redefs-fn {#'docker/invoke (constantly (f/fail "No such image: foobar:1.1.0"))}
        #(is (= (delete-image image) (f/fail "Could not delete image: No such image: foobar:1.1.0")))))))
