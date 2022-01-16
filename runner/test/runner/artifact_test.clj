; Copyright 2018-2022 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.artifact-test
  (:require [clojure.test :refer [deftest testing is]]
            [xtdb.api :as xt]
            [failjure.core :as f]
            [java-http-clj.core :as http]
            [runner.util :as u]
            [runner.engine :as eng]
            [runner.artifact :as a]))

(deftest upload-artifact-test
  (eng/pull-image "busybox:musl")
  (u/with-system (fn [db _]
                   (let [id (eng/create-container "busybox:musl")]
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/put
                                                  {:xt/id :bob.artifact-store/local
                                                   :type  :artifact-store
                                                   :url   "http://localhost:8001"
                                                   :name  "local"}]]))

                     (testing "successful artifact upload"
                       (is (= "Ok"
                              (a/upload-artifact db "dev" "test" "r-1" "file" id "/root" "local")))
                       (is (= 200
                              (:status (http/get "http://localhost:8001/bob_artifact/dev/test/r-1/file")))))
                     (eng/delete-container id)

                     (testing "unsuccessful artifact upload"
                       (is (f/failed? (a/upload-artifact db "dev" "test" "r-1" "file1" id "/invalid-path" "local")))
                       (is (= 404
                              (:status (http/get "http://localhost:8001/bob_artifact/dev/test/r-1/file1")))))
                     (xt/await-tx db
                                  (xt/submit-tx db
                                                [[::xt/delete :bob.artifact-store/local]])))))
  (eng/delete-image "busybox:musl"))
