; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.artifact-test
  (:require
   [babashka.http-client :as http]
   [clojure.test :refer [deftest is testing]]
   [failjure.core :as f]
   [runner.artifact :as a]
   [runner.engine :as eng]
   [runner.util :as u]
   [xtdb.api :as xt]))

(deftest upload-artifact-test
  (eng/pull-image "busybox:musl")
  (u/with-system
    (fn [database _ _]
      (let [id (eng/create-container "busybox:musl")]
        (xt/await-tx database
                     (xt/submit-tx database
                                   [[::xt/put
                                     {:xt/id :bob.artifact-store/local
                                      :type :artifact-store
                                      :url "http://localhost:8001"
                                      :name "local"}]]))

        (testing "successful artifact upload"
          (is (= "Ok"
                 (a/upload-artifact database "dev" "test" "r-1" "file" id "/root" "local")))
          (is (= 200
                 (:status (http/get "http://localhost:8001/bob_artifact/dev/test/r-1/file")))))
        (eng/delete-container id)

        (testing "unsuccessful artifact upload"
          (is (f/failed? (a/upload-artifact database "dev" "test" "r-1" "file1" id "/invalid-path" "local")))
          (is (= 404
                 (:status (http/get "http://localhost:8001/bob_artifact/dev/test/r-1/file1" {:throw false})))))
        (xt/await-tx database
                     (xt/submit-tx database
                                   [[::xt/delete :bob.artifact-store/local]])))))
  (eng/delete-image "busybox:musl"))
