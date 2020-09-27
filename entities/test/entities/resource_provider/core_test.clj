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

(ns entities.resource-provider.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [crux.api :as crux]
            [entities.util :as u]
            [entities.resource-provider.core :as resource-provider]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration resource-provider
  (testing "creation"
    (u/with-system
      (fn [db queue-chan]
        (let [resource-provider {:name "github"
                                 :url  "my.resource.com"}
              create-res        (resource-provider/register-resource-provider db queue-chan resource-provider)
              _                 (Thread/sleep 1000)
              effect            (crux/entity (crux/db db) :bob.resource-provider/github)]
          (is (= "Ok" create-res))
          (is (= {:crux.db/id :bob.resource-provider/github
                  :type       :resource-provider
                  :url        "my.resource.com"
                  :name       "github"}
                 effect))))))
  (testing "deletion"
    (u/with-system
      (fn [db queue-chan]
        (let [resource-provider {:name "github"}
              delete-res        (resource-provider/un-register-resource-provider db queue-chan resource-provider)
              _                 (Thread/sleep 1000)
              effect            (crux/entity (crux/db db) :bob.resource-provider/github)]
          (is (= "Ok" delete-res))
          (is (nil? effect)))))))
