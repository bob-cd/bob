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

(ns entities.artifact-store.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [crux.api :as crux]
            [entities.util :as u]
            [entities.artifact-store.core :as artifact-store]))

;; TODO: Better way to wait for consistency than sleep

(deftest ^:integration artifact-store
  (testing "creation"
    (u/with-system (fn [db queue-chan]
                     (let [artifact-store {:name "s3"
                                           :url  "my.store.com"}
                           create-res     (artifact-store/register-artifact-store db queue-chan artifact-store)
                           _              (Thread/sleep 1000)
                           effect         (crux/entity (crux/db db) :bob.artifact-store/s3)]
                       (is (= "Ok" create-res))
                       (is (= {:crux.db/id :bob.artifact-store/s3
                               :type       :artifact-store
                               :url        "my.store.com"}
                              effect))))))
  (testing "deletion"
    (u/with-system (fn [db queue-chan]
                     (let [artifact-store {:name "s3"}
                           delete-res     (artifact-store/un-register-artifact-store db queue-chan artifact-store)
                           _              (Thread/sleep 1000)
                           effect         (crux/entity (crux/db db) :bob.artifact-store/s3)]
                       (is (= "Ok" delete-res))
                       (is (nil? effect)))))))
