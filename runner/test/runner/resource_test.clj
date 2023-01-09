; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.resource-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as s]
   [clojure.test :refer [deftest is testing]]
   [contajners.core :as c]
   [failjure.core :as f]
   [runner.engine :as eng]
   [runner.resource :as r]
   [runner.util :as u]
   [xtdb.api :as xt])
  (:import
   [org.kamranzafar.jtar TarInputStream]))

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
                                              [[::xt/put
                                                {:xt/id :bob.resource-provider/git
                                                 :type  :resource-provider
                                                 :name  "git"
                                                 :url   "http://localhost:8000"}]]))
                   (testing "valid resource provider"
                     (is (r/valid-resource-provider? db {:provider "git"})))
                   (testing "invalid resource provider"
                     (is (not (r/valid-resource-provider? db {:provider "invalid"}))))
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[::xt/delete :bob.resource-provider/git]])))))

(deftest ^:integration url-generation-test
  (u/with-system (fn [db _]
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[::xt/put
                                                {:xt/id :bob.resource-provider/git
                                                 :type  :resource-provider
                                                 :name  "git"
                                                 :url   "http://localhost:8000"}]]))
                   (xt/await-tx db
                                (xt/submit-tx db
                                              [[::xt/put
                                                {:xt/id :bob.artifact-store/local
                                                 :type  :artifact-store
                                                 :name  "local"
                                                 :url   "http://localhost:8001"}]]))
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
                                              [[::xt/delete :bob.resource-provider/git]])))))

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
                                              [[::xt/put
                                                {:xt/id :bob.resource-provider/git
                                                 :type  :resource-provider
                                                 :name  "git"
                                                 :url   "http://localhost:8000"}]]))
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
                                                  [[::xt/delete :bob.resource-provider/git]]))))
                   (testing "unsuccessful mount"
                     (is (f/failed? (r/mounted-image-from db
                                                          {:name     "source"
                                                           :provider "invalid"}
                                                          "invalid"))))
                   (eng/delete-image "busybox:musl"))))
