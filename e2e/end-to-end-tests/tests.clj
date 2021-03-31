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

(defn sleep-off-side-effects
  [f]
  (f)
  (Thread/sleep 100))

(t/use-fixtures :each sleep-off-side-effects)

(defn get-resp-message
  [body]
  (:message (json/parse-string body true)))

(def bob-url "http://localhost:7777")

(t/deftest health-check-test
  (t/testing "testing the health check endpoint"
    (let [{:keys [body status]} @(http/get "http://localhost:7777/can-we-build-it")]
      (t/is (= 200 status))
      (t/is (= "Yes we can! 🔨 🔨" (get-resp-message body))))))

(t/deftest api-test
  (t/testing "gets api spec as yaml"
    (let [{:keys [headers status]} @(http/get (format "%s/api.yaml" bob-url))]
      (t/is (= 200 status))
      (t/is (= "application/yaml" (:content-type headers))))))

(t/deftest resource-providers-test
  (let [provider-name "resource-git"
        provider-url  "http://localhost:8000"]
    (t/testing "can add resources"
      (let [options               {:headers {"content-type" "application/json"}
                                   :body
                                   (json/generate-string {:url provider-url})}
            {:keys [body status]}
            @(http/post (format "%s/resource-providers/%s"
                                bob-url
                                provider-name)
                        options)]
        (Thread/sleep 500)
        (t/is (= 200 status))
        (t/is (= "Ok" (get-resp-message body)))))

    (t/testing "can list resource providers"
      (let [{:keys [body status]} @(http/get (format "%s/resource-providers" bob-url))]
        (t/is (= 200 status))
        (t/is (= [{:name provider-name :url provider-url}] (get-resp-message body)))))

    (t/testing "can remove resource providers"
      (let [options               {:headers {"content-type" "application/json"}}
            {:keys [body status]} @(http/delete (format "%s/resource-providers/%s"
                                                        bob-url
                                                        provider-name)
                                                options)]
        (t/is (= 200 status))
        (t/is (= "Ok" (get-resp-message body)))))))


(t/deftest pipeline-test
  (let [pipeline       {:steps [{:cmd "echo 123"}]
                        :image "some docker image"}
        pipeline-group "some-group"
        pipeline-name  "some-name"]
    (t/testing "creates a pipeline"
      (let [options          {:headers {"content-type" "application/json"}
                              :body
                              (json/generate-string pipeline)}

            {:keys [status]} @(http/post (format "%s/pipelines/groups/%s/names/%s"
                                                 bob-url
                                                 pipeline-group
                                                 pipeline-name)
                                         options)]
        (t/is (= 200 status))))
    (t/testing "lists pipelines"
      (Thread/sleep 500)
      (let [{:keys [body status]} @(http/get (format "%s/pipelines" bob-url))]

        (t/is (= 200 status))
        (t/is (= {:group pipeline-group
                  :name  pipeline-name
                  :image "some docker image"
                  :steps [{:cmd "echo 123"}]}
                 (first (get-resp-message body))))))))

