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

(ns bob.artifact.core-test
  (:require [clojure.test :refer :all]
            [aleph.http :as http]
            [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [bob.test-utils :as tu]
            [bob.artifact.core :refer :all]
            [bob.artifact.db :as db]))

(deftest test-artifact-store
  (testing "successful artifact store registration"
    (with-redefs-fn {#'db/register-artifact-store (fn [_ params]
                                                    (tu/check-and-fail
                                                     #(and (tu/subseq? [:name :url]
                                                                       (keys params))
                                                           (= "s3" (:name params)))))}
      #(is (= "Ok"
              (-> @(register-artifact-store "s3" "test")
                  (:body)
                  (:message))))))

  (testing "unsuccessful artifact store registration"
    (with-redefs-fn {#'db/register-artifact-store (fn [_ _]
                                                    (tu/check-and-fail
                                                     false
                                                     "FAILED!"))}
      #(is (= 409
              (:status @(register-artifact-store "s3" "test"))))))

  (testing "artifact store un-registration"
    (with-redefs-fn {#'db/un-register-artifact-store (fn [_ params]
                                                       (tu/check-and-fail
                                                        #(and (tu/subseq? [:name]
                                                                          (keys params))
                                                              (= "s3" (:name params)))))}
      #(is (= "Ok"
              (-> @(un-register-artifact-store "s3")
                  (:body)
                  (:message))))))

  (testing "successfully getting registered artifact store"
    (with-redefs-fn {#'db/get-artifact-stores (constantly [{:name "s3"
                                                            :url  "test"}])}
      #(is (= [{:name "s3" :url "test"}]
              (-> @(get-registered-artifact-stores)
                  (:body)
                  (:message))))))

  (testing "unsuccessfully getting registered artifact store"
    (with-redefs-fn {#'db/get-artifact-stores (fn [_]
                                                (tu/check-and-fail
                                                 false
                                                 "FAILED!"))}
      #(is (= 400
              (:status @(get-registered-artifact-stores)))))))

(deftest streaming-artifacts
  (testing "successful artifact stream"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'http/get              (fn [url _]
                                               (tu/check-and-fail
                                                #(= "bob-url/bob_artifact/dev/test/1/afile"
                                                    url))
                                               (future {:status 200
                                                        :body   :stuff}))}
      #(is (= {:status  200
               :headers {"Content-Type"        "application/tar"
                         "Content-Disposition" "attachment; filename=afile.tar"}
               :body    :stuff}
              @(stream-artifact "dev" "test" 1 "afile" "s3")))))

  (testing "artifact store not registered"
    (with-redefs-fn {#'db/get-artifact-store (constantly nil)}
      #(is (= {:message "No such artifact store registered"}
              (-> (stream-artifact "dev" "test" 1 "afile" "s3")
                  (:body))))))

  (testing "unsuccessful artifact stream"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'http/get              (constantly (future {:status 404}))}
      #(let [result @(stream-artifact "dev" "test" 1 "afile" "s3")]
         (is (and (= 404 (:status result))
                  (= {:message "No such artifact"} (:body result)))))))

  (testing "artifact store connection failure"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'http/get              (constantly (future (f/fail "Shizz")))}
      #(let [result @(stream-artifact "dev" "test" 1 "afile" "s3")]
         (is (and (= 503 (:status result))
                  (= {:message "Cannot reach artifact store: Shizz"}
                     (:body result))))))))

; TODO reimplement the stream tests
(deftest artifact-upload
  (testing "successful artifact upload"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'docker/invoke         (constantly "foo")
                     #'http/post             (fn [url options]
                                               (future
                                                 (tu/check-and-fail
                                                  #(= "bob-url/bob_artifact/dev/test/1/afile"
                                                           url))))}
      #(is (= "Ok"
              (upload-artifact "dev" "test" 1 "afile" "1" "/path" "s3")))))

  (testing "artifact store not registered"
    (with-redefs-fn {#'db/get-artifact-store (constantly nil)}
      #(let [result (upload-artifact "dev" "test" 1 "afile" "1" "/path" "s3")]
         (is (and (f/failed? result)
                  (= "No such artifact store registered"
                     (f/message result)))))))

  (testing "unsuccessful artifact upload"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'docker/invoke         (constantly :stream)
                     #'http/post             (constantly (future (throw (Exception. "bad call"))))}
      #(let [result (upload-artifact "dev" "test" 1 "afile" "1" "/path" "s3")]
         (is (f/failed? result))))))
