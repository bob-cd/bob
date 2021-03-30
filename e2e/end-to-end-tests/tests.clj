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

(ns tests
  (:require [clojure.test :as t]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn get-resp-message
  [body]
  (:message (json/parse-string body true)))


(t/deftest health-check
  (t/testing "testing the health check endpoing"
    (let [{:keys [body status]} @(http/get "http://localhost:7777/can-we-build-it")]
      (t/is (= 200 status))
      (t/is (= "Yes we can! 🔨 🔨" (get-resp-message body))))))

