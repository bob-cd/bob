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

(ns runner.resource-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [xtdb.api :as xt]
            [failjure.core :as f]
            [contajners.core :as c]
            [runner.util :as u]
            [runner.engine :as eng]
            [runner.resource :as r])
  (:import [org.kamranzafar.jtar TarInputStream]))

(deftest ^:integration resource-fetch-test
  (testing "successful resource fetch"
    (is (instance?
          java.io.InputStream
          (r/fetch-resource
            "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=main"))))
  (testing "unsuccessful resource fetch"
    (is (f/failed? (r/fetch-resource "http://invalid-url")))))

(deftest ^:integration tar-prefix-test
  (let [url   "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=main"
        tar   (r/fetch-resource url)
        path  (r/prefix-dir-on-tar! (TarInputStream. tar) "source")
        entry (-> path
                  io/input-stream
                  TarInputStream.
                  .getNextEntry
                  .getName)]
    (is (s/starts-with? entry "source/"))))

(deftest ^:integration valid-resource-provider-test
  (u/with-system (fn [db _]
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/put
                                                {:xt.db/id :bob.resource-provider/git
                                                 :url      "http://localhost:8000"}]]))
                   (testing "valid resource provider"
                     (is (r/valid-resource-provider? db {:provider "git"})))
                   (testing "invalid resource provider"
                     (is (not (r/valid-resource-provider? db {:provider "invalid"}))))
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/delete :bob.resource-provider/git]])))))

(deftest ^:integration url-generation-test
  (u/with-system (fn [db _]
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/put
                                                {:xt.db/id :bob.resource-provider/git
                                                 :url      "http://localhost:8000"}]]))
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/put
                                                {:xt.db/id :bob.artifact-store/local
                                                 :url      "http://localhost:8001"}]]))
                   (testing "generate url for an external resource"
                     (is (= "http://localhost:8000/bob_resource?repo=a-repo&branch=a-branch"
                            (r/url-of db
                                      {:name     "source"
                                       :provider "git"
                                       :type     "external"
                                       :params   {:repo   "a-repo"
                                                  :branch "a-branch"}}))))
                   (testing "generate url for an internal resource"
                     (is (= "http://localhost:8001/bob_artifact/dev/test/a-run-id/jar"
                            (r/url-of db
                                      {:name     "jar"
                                       :provider "local"
                                       :type     "internal"
                                       :params   {:group  "dev"
                                                  :name   "test"
                                                  :run_id "a-run-id"}}))))
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/delete :bob.resource-provider/git]])))))

(deftest ^:integration initial-image-test
  (eng/pull-image "busybox:musl")
  (testing "successful image creation"
    (let [url    "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=main"
          stream (r/fetch-resource url)
          image  (r/initial-image-of stream "busybox:musl" nil "source")
          images (->> (c/invoke eng/images {:op :ImageListLibpod})
                      (map :Id))]
      (is (some #{image} images))
      (eng/delete-image image)))
  (testing "unsuccessful image creation"
    (is (f/failed? (r/initial-image-of (io/input-stream "test/test.tar") "invalid-image" nil "src"))))
  (eng/delete-image "busybox:musl"))

(deftest ^:integration mounted-image-test
  (u/with-system (fn [db _]
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[:xt.tx/put
                                                {:xt.db/id :bob.resource-provider/git
                                                 :url      "http://localhost:8000"}]]))
                   (eng/pull-image "busybox:musl")
                   (testing "successful mount"
                     (let [image  (r/mounted-image-from db
                                                        {:name     "source"
                                                         :type     "external"
                                                         :provider "git"
                                                         :params   {:repo   "https://github.com/lispyclouds/bob-example"
                                                                    :branch "main"}}
                                                        "busybox:musl")
                           images (->> (c/invoke eng/images {:op :ImageListLibpod})
                                       (map :Id))]
                       (is (some #{image} images))
                       (eng/delete-image "busybox:musl")
                       (xt/await-tx db
                                    (xt/submit-tx db
                                                  [[:xt.tx/delete :bob.resource-provider/git]]))))
                   (testing "unsuccessful mount"
                     (is (f/failed? (r/mounted-image-from db
                                                          {:name     "source"
                                                           :provider "invalid"}
                                                          "invalid"))))
                   (eng/delete-image "busybox:musl"))))
