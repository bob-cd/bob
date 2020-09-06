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

(ns runner.pipeline.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [crux.api :as crux]
            [runner.util :as u]
            [runner.pipeline.core :as p]))

(deftest ^:integration logging-to-db
  (u/with-system (fn [db _]
                   (testing "log raw line"
                     (p/log->db db "a-run-id" "a log line")
                     (Thread/sleep 1000)
                     (is (= {:type   :log-line
                             :run-id "a-run-id"
                             :line   "a log line"}
                            (-> (crux/db db)
                                (crux/q
                                  '{:find  [(eql/project log [:type :run-id :line])]
                                    :where [[log :run-id "a-run-id"]]})
                                first
                                first)))))))
