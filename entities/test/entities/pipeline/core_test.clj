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
            [crux.api :as crux]
            [entities.util :as u]
            [entities.pipeline.core :as p]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration pipleine
  (testing "creation"
    (u/with-system
      (fn [db queue-chan]
        (let [pipeline   {:group     "test"
                          :name      "test"
                          :steps     [{:cmd "echo hello"}
                                      {:needs_resource "source"
                                       :cmd            "ls"}
                                      {:cmd               "touch test"
                                       :produces_artifact {:name  "file"
                                                           :path  "test"
                                                           :store "s3"}}]
                          :vars      {:k1 "v1"
                                      :k2 "v2"}
                          :resources [{:name     "source"
                                       :type     "external"
                                       :provider "git"
                                       :params   {:repo   "https://github.com/bob-cd/bob"
                                                  :branch "main"}}
                                      {:name     "source2"
                                       :type     "external"
                                       :provider "git"
                                       :params   {:repo   "https://github.com/lispyclouds/clj-docker-client"
                                                  :branch "main"}}]
                          :image     "busybox:musl"}
              create-res (p/create db queue-chan pipeline)
              _          (Thread/sleep 1000)
              effect     (crux/entity (crux/db db) :bob.pipeline.test/test)]
          (is (= "Ok" create-res))
          (is (= (-> pipeline
                     (assoc :type :pipeline)
                     (assoc :crux.db/id :bob.pipeline.test/test))
                 effect))))))
  (testing "deletion"
    (u/with-system (fn [db queue-chan]
                     (let [pipeline   {:name  "test"
                                       :group "test"}
                           delete-res (p/delete db queue-chan pipeline)
                           _          (Thread/sleep 1000)
                           effect     (crux/entity (crux/db db) :bob.pipeline.test/test)]
                       (is (= "Ok" delete-res))
                       (is (nil? effect)))))))
