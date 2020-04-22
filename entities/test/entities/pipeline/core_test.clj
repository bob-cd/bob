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

(ns entities.pipeline.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.stuartsierra.component :as component]
            [entities.system :as sys]
            [entities.pipeline.core :as p]))

(defn with-db
  [test-fn]
  (let [url "jdbc:postgresql://localhost:5433/bob-test?user=bob&password=bob"
        db  (sys/map->Database {:jdbc-url           url
                                :connection-timeout 5000})
        com (component/start db)]
    (test-fn (sys/db-connection com))
    (component/stop com)))

(deftest ^:integration pipleine
  (testing "creation and deletion"
    (with-db
      #(let [pipeline   {:group     "test"
                         :name      "test"
                         :steps     [{:cmd "echo hello"}
                                     {:needs_resource "source"
                                      :cmd            "ls"}]
                         :vars      {:k1 "v1"
                                     :k2 "v2"}
                         :resources [{:name     "source"
                                      :type     "external"
                                      :provider "git"
                                      :params   {:repo   "https://github.com/bob-cd/bob"
                                                 :branch "master"}}
                                     {:name     "source2"
                                      :type     "external"
                                      :provider "git"
                                      :params   {:repo   "https://github.com/lispyclouds/clj-docker-client"
                                                 :branch "master"}}]
                         :image     "busybox:musl"}
             create-res (p/create % pipeline)
             delete-res (p/delete % (select-keys pipeline [:name :group]))]
         (is (= "Ok" create-res))
         (is (= "Ok" delete-res))))))
