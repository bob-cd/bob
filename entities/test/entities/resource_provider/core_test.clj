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
            [entities.util :as u]
            [entities.resource-provider.core :as resource-provider]))

(deftest ^:integration resource-provider
  (testing "creation"
    (u/with-db
      #(let [resource-provider {:name "github"
                                :url  "my.resource.com"}
             create-res        (resource-provider/register-resource-provider % resource-provider)
             effect            (first (u/sql-exec! % "SELECT * FROM resource_providers"))]
         (is (= "Ok" create-res))
         (is (= {:name "github"
                 :url  "my.resource.com"}
                effect)))))
  (testing "deletion"
    (u/with-db
      #(let [resource-provider {:name "github"}
             delete-res        (resource-provider/un-register-resource-provider % resource-provider)
             effect            (first (u/sql-exec! % "SELECT * FROM resource_providers"))]
         (is (= "Ok" delete-res))
         (is (empty? effect))))))
