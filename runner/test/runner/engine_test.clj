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

(ns runner.engine-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [contajners.core :as c]
            [failjure.core :as f]
            [runner.engine :as e]))

;; helpers

(defn ps-a
  []
  (map :Id
       (c/invoke e/containers
                 {:op     :ContainerListLibpod
                  :params {:all true}})))

(defn image-ls
  []
  (c/invoke e/images {:op :ImageListLibpod}))

(defn start
  [id]
  (c/invoke e/containers
            {:op     :ContainerStartLibpod
             :params {:name id}}))

;; tests

(deftest shell-arg-tokenize-test
  (testing "tokenizing a shell command"
    (is (= ["sh" "-c" "while sleep 1; do echo ${RANDOM}; done"]
           (e/sh-tokenize "sh -c \"while sleep 1; do echo ${RANDOM}; done\""))))
  (testing "tokenizing a shell command with escaped double quotes"
    (is (= ["sort" "-t" "\t" "-k2" "test" ">" "test-sorted"]
           (e/sh-tokenize "sort -t \"\t\" -k2 test > test-sorted")))))

(def image "docker.io/library/busybox:musl")

(deftest ^:integration image-pull
  (testing "success"
    (e/pull-image image)
    (is (->> (image-ls)
             (map :RepoTags)
             (map first)
             (some #{image}))))
  (e/delete-image image))

(deftest ^:integration commit-container
  (let [_ (e/pull-image image)
        id (e/create-container image {:cmd "ls"})]
    (testing "success"
      (let [image-id  (e/commit-container id "pwd")
            new-image (->> (image-ls)
                           (map :Id)
                           (filter #{image-id})
                           (first))]
        (is (some? new-image))
        (e/delete-image new-image)))
    (testing "failure"
      (is (f/failed? (e/commit-container "this-doesnt-exist" "pwd"))))
    (e/delete-container id)
    (e/delete-image image)))

(deftest ^:integration delete-image
  (e/pull-image image)
  (testing "success"
    (e/delete-image image)
    (let [id (->> (image-ls)
                  (map :Id)
                  (filter #{image}))]
      (is (empty? id)))))

(deftest ^:integration create-container
  (e/pull-image image)
  (testing "success: image"
    (let [id          (e/create-container image)
          expected-id (first (ps-a))]
      (is (= expected-id id))
      (e/delete-container id)))
  (testing "success: image and evars"
    (let [id                           (e/create-container image
                                                           {:cmd            "ls"
                                                            :needs_resource "src"}
                                                           {:k1 "v1"})
          {:keys [Env Cmd WorkingDir]} (-> (e/inspect-container id)
                                           :Config
                                           (select-keys [:Env :Cmd :WorkingDir]))]
      (is (some #{"k1=v1"} Env))
      (is (= "ls" (first Cmd)))
      (is (= "/root/src" WorkingDir))
      (e/delete-container id)))
  (testing "failure"
    (is (f/failed? (e/create-container "this-doesnt-exist")))
    (is (f/failed? (e/create-container "this-doesnt-exist" {} {}))))
  (e/delete-image image))

(deftest ^:integration status-of
  (e/pull-image image)
  (testing "success"
    (let [id     (e/create-container image {:cmd "ls"})
          _ (start id)
          status (e/status-of id)]
      (is (= {:running?  false
              :exit-code 0}
             status))
      (e/delete-container id)))
  (testing "failure"
    (is (f/failed? (e/status-of "this-doesnt-exist"))))
  (e/delete-image image))

(deftest ^:integration react-to-log-line
  (e/pull-image image)
  (testing "success"
    (let [id    (e/create-container image {:cmd "sh -c 'for i in `seq 1 5`; do echo $i; done'"})
          _ (start id)
          lines (atom [])
          _ (e/react-to-log-line id #(swap! lines conj %))]
      (is (= ["1" "2" "3" "4" "5"] @lines))
      (e/delete-container id)))
  (testing "failure"
    (is (f/failed? (e/react-to-log-line "this-doesnt-exist" identity))))
  (e/delete-image image))

(deftest ^:integration start-container
  (e/pull-image image)
  (testing "success: normal exit"
    (let [id        (e/create-container image {:cmd "sh -c 'sleep 2; ls'"})
          result-id (e/start-container id println)]
      (is (= id result-id))
      (e/delete-container id)))
  (testing "success: abnormal exit"
    (let [id     (e/create-container image {:cmd "sh -c 'sleep 2; exit 1'"})
          result (e/start-container id println)]
      (is (f/failed? result))
      (is (= (format "Container %s exited with non-zero status 1" id) (f/message result)))
      (e/delete-container id)))
  (testing "failure"
    (is (f/failed? (e/start-container "this-doesnt-exist" #(println %)))))
  (e/delete-image image))

(deftest ^:integration kill-container
  (e/pull-image image)
  (testing "success"
    (let [id     (e/create-container image {:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"})
          _ (future (e/start-container id #(println %)))
          _ (Thread/sleep 1000)
          _ (e/kill-container id)
          status (e/status-of id)]
      (is (not (:running? status)))
      (e/delete-container id)))
  (e/delete-image image))

(deftest ^:integration delete-container
  (e/pull-image image)
  (testing "success"
    (let [id     (e/create-container image)
          _ (e/delete-container id)
          result (some #{id} (ps-a))]
      (is (nil? result))))
  (e/delete-image image))

(deftest ^:integration put-container-archive
  (e/pull-image image)
  (testing "success"
    (let [id (e/create-container image)]
      (is (nil? (e/put-container-archive id (io/input-stream "test/test.tar") "/root")))
      (e/delete-container id)))
  (testing "failure"
    (let [id (e/create-container image)]
      (is (f/failed? (e/put-container-archive "this-doesnt-exist" (io/input-stream "test/test.tar") "/root")))
      (e/delete-container id)))
  (e/delete-image image))

(deftest ^:integration get-container-archive
  (e/pull-image image)
  (testing "success"
    (let [id (e/create-container image)]
      (is (instance? java.io.InputStream (e/get-container-archive id "/root")))
      (e/delete-container id)))
  (testing "failure"
    (let [id (e/create-container image)]
      (is (f/failed? (e/get-container-archive id "/invalid-path")))
      (e/delete-container id)))
  (e/delete-image image))

(deftest ^:integration inspect-container
  (e/pull-image image)
  (testing "success"
    (let [id (e/create-container image)]
      (is (map? (e/inspect-container id)))
      (e/delete-container id)))
  (testing "failure"
    (is (f/failed? (e/inspect-container "invalid-id"))))
  (e/delete-image image))

(deftest ^:integration list-running-containers
  (e/pull-image image)
  (testing "success"
    (let [containers-before (e/container-ls)
          id                (e/create-container image {:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"})
          _ (future (e/start-container id #(println %)))
          _ (Thread/sleep 1000)
          containers-after  (e/container-ls)]
      (is (= 1 (- (count containers-after) (count containers-before))))
      (e/kill-container id)
      (e/delete-container id)))
  (e/delete-image image))
