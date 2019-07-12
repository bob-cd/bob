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
                                                           (= (:name params)
                                                              "artifact/s3"))))}
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
                                                              (= (:name params)
                                                                 "artifact/s3"))))}
      #(is (= "Ok"
              (-> @(un-register-artifact-store "s3")
                  (:body)
                  (:message))))))

  (testing "successfully getting registered artifact store"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:name "artifact/s3"
                                                          :url  "test"})}
      #(is (= {:name "s3" :url "test"}
              (-> @(get-registered-artifact-store)
                  (:body)
                  (:message))))))

  (testing "unsuccessfully getting registered artifact store"
    (with-redefs-fn {#'db/get-artifact-store (fn [_]
                                               (tu/check-and-fail
                                                false
                                                "FAILED!"))}
      #(is (= 400
              (:status @(get-registered-artifact-store)))))))

(deftest streaming-artifacts
  (testing "successful artifact stream"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'http/get              (fn [url _]
                                               (tu/check-and-fail
                                                #(= "bob-url/bob_artifact/dev/test/1/afile"
                                                    url))
                                               {:status 200
                                                :body   :stuff})}
      #(is (= {:status  200
               :headers {"Content-Type"        "application/tar"
                         "Content-Disposition" "attachment; filename=afile.tar"}
               :body    :stuff}
              @(stream-artifact "dev" "test" 1 "afile")))))

  (testing "artifact store not registered"
    (with-redefs-fn {#'db/get-artifact-store (constantly nil)}
      #(is (= "No artifact store registered"
              (-> (stream-artifact "dev" "test" 1 "afile")
                  (:body))))))

  (testing "unsuccessful artifact stream"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'http/get              (constantly {:status 404})}
      #(let [result @(stream-artifact "dev" "test" 1 "afile")]
         (println result)
         (is (and (= 404 (:status result))
                  (= "No such artifact" (:body result))))))))

(deftest artifact-upload
  (testing "successful artifact upload"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'docker/stream-path    (fn [_ id path]
                                               (tu/check-and-fail
                                                #(and (= "1" id)
                                                      (= "/path" path)))
                                               :stream)
                     #'http/post             (fn [url options]
                                               (future
                                                 (tu/check-and-fail
                                                  #(and (= "bob-url/bob_artifact/dev/test/1/afile"
                                                           url)
                                                        (= "data"
                                                           (get-in options [:multipart 0 :name]))
                                                        (= :stream
                                                           (get-in options [:multipart 0 :content]))))))}
      #(is (= "Ok"
              (upload-artifact "dev" "test" 1 "afile" "1" "/path")))))

  (testing "artifact store not registered"
    (with-redefs-fn {#'db/get-artifact-store (constantly nil)}
      #(let [result (upload-artifact "dev" "test" 1 "afile" "1" "/path")]
         (is (and (f/failed? result)
                  (= "No artifact store registered"
                     (f/message result)))))))

  (testing "unsuccessful artifact upload"
    (with-redefs-fn {#'db/get-artifact-store (constantly {:url "bob-url"})
                     #'docker/stream-path    (constantly :stream)
                     #'http/post             (constantly (future (throw (Exception. "bad call"))))}
      #(let [result (upload-artifact "dev" "test" 1 "afile" "1" "/path")]
         (is (f/failed? result))))))
