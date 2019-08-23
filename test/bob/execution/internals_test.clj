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
;   You should have received a copy of the GNU General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.execution.internals-test
  (:require [clojure.test :refer :all]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.test-utils :as tu]
            [bob.execution.internals :refer :all]))

(deftest docker-image-presence
  (testing "image present"
    (with-redefs-fn {#'docker/image-ls (constantly [{:RepoTags ["img"]}])}
      #(is (= "img" (has-image "img")))))

  (testing "image absent"
    (with-redefs-fn {#'docker/image-ls (constantly [])}
      #(let [result (has-image "img")]
         (is (and (f/failed? result)
                  (= "Failed to find img"
                     (f/message result))))))))

(deftest container-kill
  (testing "successful kill"
    (with-redefs-fn {#'docker/kill (fn [_ name]
                                     (tu/check-and-fail
                                      #(= "c1" name)))}
      #(is (= "c1" (kill-container "c1")))))

  (testing "unsuccessful kill"
    (with-redefs-fn {#'docker/kill (constantly (f/fail "Failed"))}
      #(let [result (kill-container "c1")]
         (is (and (f/failed? result)
                  (= "Could not kill c1"
                     (f/message result))))))))

(deftest image-pull
  (testing "successful pull"
    (with-redefs-fn {#'has-image   (constantly true)
                     #'docker/pull (fn [_ name]
                                     (tu/check-and-fail
                                      #(= "img" name)))}
      #(is (= "img" (pull "img")))))

  (testing "unsuccessful pull"
    (with-redefs-fn {#'has-image   (constantly (f/fail "Failed"))
                     #'docker/pull (fn [_ _] (throw (Exception. "Failed")))}
      #(let [result (pull "img")]
         (println result)
         (is (and (f/failed? result)
                  (= "Cannot pull img"
                     (f/message result))))))))

(deftest container-status
  (testing "successful status fetch"
    (with-redefs-fn {#'docker/container-state (fn [_ id]
                                                (tu/check-and-fail
                                                 #(= "id" id))
                                                {:Running  false
                                                 :ExitCode 0})}
      #(is (= {:running? false :exit-code 0} (status-of "id")))))

  (testing "unsuccessful status fetch"
    (with-redefs-fn {#'docker/container-state (fn [_ _] (throw (Exception. "Failed")))}
      #(is (f/failed? (status-of "id"))))))

(deftest container-runs
  (testing "successful run"
    (let [id "11235813213455"]
      (with-redefs-fn {#'docker/start     (fn [_ cid]
                                            (tu/check-and-fail
                                             #(= id cid)))
                       #'docker/logs-live (constantly true)
                       #'docker/inspect   (fn [_ cid]
                                            (tu/check-and-fail
                                             #(= id cid))
                                            {:State {:ExitCode 0}})}
        #(is (= "112358132134" (run id "run-id"))))))

  (testing "successful run, non-zero exit"
    (let [id "11235813213455"]
      (with-redefs-fn {#'docker/start     (fn [_ cid]
                                            (tu/check-and-fail
                                             #(= id cid)))
                       #'docker/logs-live (constantly true)
                       #'docker/inspect   (fn [_ cid]
                                            (tu/check-and-fail
                                             #(= id cid))
                                            {:State {:ExitCode 1}})}
        #(let [result (run id "run-id")]
           (is (and (f/failed? result)
                    (= "Abnormal exit."
                       (f/message result))))))))

  (testing "unsuccessful run"
    (with-redefs-fn {#'docker/start     (constantly nil)
                     #'docker/logs-live (constantly true)
                     #'docker/inspect   (fn [_ _] (throw (Exception. "Failed")))}
      #(is (f/failed? (run "id" "run-id"))))))
