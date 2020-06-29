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

(ns runner.docker-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [runner.docker :as d]))

;; helpers

(defn ps-a
  []
  (->> (docker/invoke d/containers
                      {:op     :ContainerList
                       :params {:all true}})
       (map :Id)))

(defn image-ls
  []
  (->> (docker/invoke d/images {:op :ImageList})
       (map :RepoTags)
       (map first)))

(defn start
  [id]
  (docker/invoke d/containers
                 {:op     :ContainerStart
                  :params {:id id}}))

(defn wait
  [id]
  (docker/invoke d/containers
                 {:op     :ContainerWait
                  :params {:id id}}))

;; tests

(deftest shell-arg-tokenize-test
  (testing "tokenizing a shell command"
    (is (= ["sh" "-c" "while sleep 1; do echo ${RANDOM}; done"]
           (d/sh-tokenize! "sh -c \"while sleep 1; do echo ${RANDOM}; done\""))))
  (testing "tokenizing a shell command with escaped double quotes"
    (is (= ["sort" "-t" "\t" "-k2" "test" ">" "test-sorted"]
           (d/sh-tokenize! "sort -t \"\t\" -k2 test > test-sorted")))))

(def image "busybox:musl")

(deftest ^:integration image-pull
  (testing "success"
    (d/pull-image image)
    (is (some #{image} (image-ls))))
  (testing "failure"
    (is (f/failed? (d/pull-image "this-doesnt-exist"))))
  (d/delete-image image))

(deftest ^:integration commit-image
  (let [_  (d/pull-image image)
        id (d/create-container image {:cmd "ls"})]
    (testing "success"
      (let [_         (d/commit-image id "pwd")
            new-image (->> (image-ls)
                           (filter #(s/starts-with? % id))
                           (first))]
        (is (some? new-image))
        (d/delete-image new-image)))
    (testing "failure"
      (is (f/failed? (d/commit-image "this-doesnt-exist" "pwd"))))
    (d/delete-container id)
    (d/delete-image image)))

(deftest ^:integration delete-image
  (d/pull-image image)
  (testing "success"
    (d/delete-image image)
    (let [id (->> (image-ls)
                  (filter #(= % image)))]
      (is (empty? id)))))

(deftest ^:integration create-container
  (d/pull-image image)
  (testing "success: image"
    (let [id          (d/create-container image)
          expected-id (first (ps-a))]
      (is (= expected-id id))
      (d/delete-container id)))
  (testing "success: image and evars"
    (let [id                           (d/create-container image
                                                           {:cmd            "ls"
                                                            :needs_resource "src"}
                                                           {:k1 "v1"})
          {:keys [Env Cmd WorkingDir]} (-> (docker/invoke d/containers
                                                          {:op     :ContainerInspect
                                                           :params {:id id}})
                                           :Config
                                           (select-keys [:Env :Cmd :WorkingDir]))]
      (is (some #{"k1=v1"} Env))
      (is (= "ls" (first Cmd)))
      (is (= "/root/src" WorkingDir))
      (d/delete-container id)))
  (testing "failure"
    (is (f/failed? (d/create-container "this-doesnt-exist")))
    (is (f/failed? (d/create-container "this-doesnt-exist" {} {}))))
  (d/delete-image image))

(deftest ^:integration status-of
  (d/pull-image image)
  (testing "success"
    (let [id     (d/create-container image {:cmd "ls"})
          _      (start id)
          status (d/status-of id)]
      (is (= {:running?  false
              :exit-code 0}
             status))
      (d/delete-container id)))
  (testing "failure"
    (is (f/failed? (d/status-of "this-doesnt-exist"))))
  (d/delete-image image))

(deftest ^:integration react-to-log-line
  (d/pull-image image)
  (testing "success"
    (let [id    (d/create-container image {:cmd "sh -c 'for i in `seq 1 5`; do echo $i; done'"})
          _     (start id)
          lines (atom [])
          _     (d/react-to-log-line id #(swap! lines conj %))]
      (wait id)
      (is (= ["1" "2" "3" "4" "5"] @lines))
      (d/delete-container id)))
  (testing "failure"
    (is (f/failed? (d/react-to-log-line "this-doesnt-exist" #()))))
  (d/delete-image image))

(deftest ^:integration start-container
  (d/pull-image image)
  (testing "success: normal exit"
    (let [id        (d/create-container image {:cmd "ls"})
          _         (wait id)
          result-id (d/start-container id #(println %))]
      (is (= id result-id))
      (d/delete-container id)))
  (testing "success: abnormal exit"
    (let [id     (d/create-container image {:cmd "sh -c 'exit 1'"})
          _      (wait id)
          result (d/start-container id #(println %))]
      (is (f/failed? result))
      (is (= (format "Container %s exited with non-zero status 1" id) (f/message result)))
      (d/delete-container id)))
  (testing "failure"
    (is (f/failed? (d/start-container "this-doesnt-exist" #(println %)))))
  (d/delete-image image))

(deftest ^:integration kill-container
  (d/pull-image image)
  (testing "success"
    (let [id     (d/create-container image {:cmd "sh -c 'while :; do echo ${RANDOM}; sleep 1; done'"})
          _      (future (d/start-container id #(println %)))
          _      (d/kill-container id)
          status (d/status-of id)]
      (is (not (:running? status)))
      (d/delete-container id)))
  (d/delete-image image))

(deftest ^:integration delete-container
  (d/pull-image image)
  (testing "success"
    (let [id     (d/create-container image)
          _      (d/delete-container id)
          result (some #{id} (ps-a))]
      (is (nil? result))))
  (d/delete-image image))

(deftest ^:integration put-container-archive
  (d/pull-image image)
  (testing "success"
    (let [id (d/create-container image)]
      (is (nil? (d/put-container-archive id (io/input-stream "test/test.tar") "/root")))
      (d/delete-container id)))
  (testing "failure"
    (let [id (d/create-container image)]
      (is (f/failed? (d/put-container-archive "this-doesnt-exist" (io/input-stream "test/test.tar") "/root")))
      (is (f/failed? (d/put-container-archive id (io/input-stream "test/test.tar") "no-a-valid-path")))
      (d/delete-container id)))
  (d/delete-image image))

(deftest ^:integration get-container-archive
  (d/pull-image image)
  (testing "success"
    (let [id (d/create-container image)]
      (is (instance? java.io.InputStream (d/get-container-archive id "/root")))
      (d/delete-container id)))
  (testing "failure"
    (let [id (d/create-container image)]
      (is (f/failed? (d/get-container-archive id "/invalid-path")))
      (d/delete-container id)))
  (d/delete-image image))
