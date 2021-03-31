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
            [clojure.set :as s]
            [cheshire.core :as json]))

(defn sleep-off-side-effects
  [f]
  (f)
  (Thread/sleep 100))

(t/use-fixtures :each sleep-off-side-effects)

(defn get-resp-message
  [body]
  (:message (json/parse-string body true)))

(defn run-id?
  [s]
  (-> s
      (.substring 2)
      (java.util.UUID/fromString)
      (uuid?)))

(def bob-url "http://localhost:7777")

(defn running?
  [id]
  (let [{:keys [body]} @(http/get (format "%s/pipelines/status/runs/%s"
                                          bob-url
                                          id))
        message        (get-resp-message body)]
    (= "running" message)))

(defn wait-until-complete
  ([id]
   (wait-until-complete id 1000))
  ([id wait]
   (Thread/sleep wait)
   (when (running? id)
     (recur id wait))))





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
  (let [image-name     "busybox:latest"
        message        "123"
        pipeline       {:steps [{:cmd (str "echo " message)}]
                        :image image-name}
        pipeline-group "some-group"
        pipeline-name  "some-name"]
    (t/testing "creates a pipeline"
      (let [options               {:headers {"content-type" "application/json"}
                                   :body
                                   (json/generate-string pipeline)}

            {:keys [body status]} @(http/post (format "%s/pipelines/groups/%s/names/%s"
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
                  :image image-name
                  :steps [{:cmd "echo 123"}]}
                 (first (get-resp-message body))))))

    (t/testing "starts a pipeline"
      (let [{:keys [body status]} @(http/post (format "%s/pipelines/start/groups/%s/names/%s"
                                                      bob-url
                                                      pipeline-group
                                                      pipeline-name))]
        (t/is (= 200 status))
        (t/is (run-id? (get-resp-message body)))))

    (t/testing "gets the logs of a pipeline"
      (let [{:keys [body]}        @(http/post (format "%s/pipelines/start/groups/%s/names/%s"
                                                      bob-url
                                                      pipeline-group
                                                      pipeline-name))
            run-id                (get-resp-message body)
            _                     (wait-until-complete run-id)
            {:keys [body status]} @(http/get (format "%s/pipelines/logs/runs/%s/offset/%s/lines/%s"
                                                     bob-url
                                                     run-id
                                                     0
                                                     100))]
        (t/is (s/subset? (set [message]) (set (get-resp-message body))))
        (t/is (= 200 status))))))
