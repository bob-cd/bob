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

(ns runner.resource.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [crux.api :as crux]
            [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [runner.docker :as d]
            [runner.resource.core :as r])
  (:import [org.kamranzafar.jtar TarInputStream]))

(def db-client (crux/new-api-client "http://localhost:7779"))

(deftest resource-fetch-test
  (testing "successful resource fetch"
    (is (instance?
          java.io.InputStream
          (r/fetch-resource
            "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=master"))))
  (testing "unsuccessful resource fetch"
    (is (f/failed? (r/fetch-resource "http://invalid-url")))))

(deftest tar-prefix-test
  (let [url   "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=master"
        tar   (r/fetch-resource url)
        path  (r/prefix-dir-on-tar! (TarInputStream. tar) "source")
        entry (-> path
                  io/input-stream
                  TarInputStream.
                  .getNextEntry
                  .getName)]
    (is (s/starts-with? entry "source/"))))

(deftest valid-resource-provider-test
  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.resource-provider/git
                     :url        "http://localhost:8000"}]])
  (Thread/sleep 1000)
  (testing "valid resource provider"
    (is (r/valid-resource-provider? db-client {:provider "git"})))
  (testing "invalid resource provider"
    (is (not (r/valid-resource-provider? db-client {:provider "invalid"}))))
  (crux/submit-tx db-client
                  [[:crux.tx/delete :bob.resource-provider/git]])
  (Thread/sleep 1000))

(deftest url-generation-test
  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.resource-provider/git
                     :url        "http://localhost:8000"}]])
  (Thread/sleep 1000)
  (testing "generate url for a resource provider"
    (is (= "http://localhost:8000/bob_resource?repo=a-repo&branch=a-branch"
           (r/url-of db-client
                     {:provider "git"
                      :params   {:repo   "a-repo"
                                 :branch "a-branch"}}))))
  (crux/submit-tx db-client
                  [[:crux.tx/delete :bob.resource-provider/git]])
  (Thread/sleep 1000))

(deftest initial-image-test
  (d/pull-image "busybox:musl")
  (testing "successful image creation"
    (let [url    "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=master"
          stream (r/fetch-resource url)
          image  (r/initial-image-of stream "busybox:musl" nil "source")
          images (->> (docker/invoke d/images {:op :ImageList})
                      (map :Id))]
      (is (some #{image} images))
      (d/delete-image image)))
  (testing "unsuccessful image creation"
    (is (f/failed? (r/initial-image-of (io/input-stream "test/test.tar") "invalid-image" nil "src"))))
  (d/delete-image "busybox:musl"))

(deftest mounted-image-test
  (crux/submit-tx db-client
                  [[:crux.tx/put
                    {:crux.db/id :bob.resource-provider/git
                     :url        "http://localhost:8000"}]])
  (Thread/sleep 1000)
  (d/pull-image "busybox:musl")
  (testing "successful mount"
    (let [image  (r/mounted-image-from db-client
                                       {:name     "source"
                                        :provider "git"
                                        :params   {:repo   "https://github.com/lispyclouds/bob-example"
                                                   :branch "master"}}
                                       "busybox:musl")
          images (->> (docker/invoke d/images {:op :ImageList})
                      (map :Id))]
      (is (some #{image} images))
      (d/delete-image "busybox:musl")
      (crux/submit-tx db-client
                      [[:crux.tx/delete :bob.resource-provider/git]])
      (Thread/sleep 1000)))
  (testing "unsuccessful mount"
    (is (f/failed? (r/mounted-image-from db-client
                                         {:name     "source"
                                          :provider "invalid"}
                                         "invalid"))))
  (d/delete-image "busybox:musl"))
