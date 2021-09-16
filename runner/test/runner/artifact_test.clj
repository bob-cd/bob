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

(ns runner.artifact-test
  (:require [clojure.test :refer [deftest testing is]]
            [xt.api :as xt]
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
                                                [[:xt.tx/put
                                                  {:xt.db/id :bob.artifact-store/local
                                                   :url      "http://localhost:8001"}]]))

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
                                                [[:xt.tx/delete :bob.artifact-store/local]])))))
  (eng/delete-image "busybox:musl"))
