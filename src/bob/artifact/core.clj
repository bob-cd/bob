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

(ns bob.artifact.core
  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [korma.core :as k]
            [ring.util.http-response :as res]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.util :as u]
            [bob.db.core :as db]
            [bob.states :as states]))

(def artifact-prefix "artifact/")

(defn- get-artifact-store
  "Gets the artifact store and its URL."
  []
  (f/attempt-all [result (u/unsafe! (k/select db/config))
                  store  (->> result
                              (filter #(clojure.string/starts-with? (:name %) artifact-prefix))
                              (first))]
    (-> store
        (update-in [:name] #(last (clojure.string/split % #"/")))
        (clojure.set/rename-keys {:value :url}))
    (f/when-failed [err] err)))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an URL."
  [name url]
  (d/let-flow [result (u/unsafe! (k/insert db/config
                                           (k/values {:name  (str artifact-prefix name)
                                                      :value url})))]
    (if (f/failed? result)
      (res/conflict "Artifact store already registered")
      (u/respond "Ok"))))

(defn un-register-artifact-store
  "Unregisters an artifact-store resource by its name."
  [name]
  (d/let-flow [_ (u/unsafe! (k/delete db/config
                                      (k/where {:name (str artifact-prefix name)})))]
    (u/respond "Ok")))

(defn get-registered-artifact-store
  "Gets the registered artifact store."
  []
  (d/let-flow [result (u/unsafe! (get-artifact-store))]
    (if (f/failed? result)
      (res/bad-request (f/message result))
      (u/respond result))))

(defn stream-artifact
  "Connects to the registered atrifact store and streams the artifact back if exists.

  Contrstucts the following URL to connect:
  <URL of regsitered store>/bob_artifact/dev/test/1/jar"
  [group name number artifact]
  (if-let [{url :url} (get-artifact-store)]
    (d/let-flow [fetch-url (clojure.string/join "/"
                                                [url
                                                 "bob_artifact"
                                                 group
                                                 name
                                                 number
                                                 artifact])
                 data      (http/get fetch-url {:throw-exceptions false})]
      (if (= 200 (:status data))
        {:status  200
         :headers {"Content-Type"        "application/tar"
                   "Content-Disposition" (format "attachment; filename=%s.tar" artifact)}
         :body    (:body data)}
        (res/not-found "No such artifact")))
    (res/bad-request "No artifact store registered")))

(defn upload-artifact
  "Opens up a stream to the path in a container by id and POSTs it to the artifact store.

  Returns a Failure object if failed."
  [group name number artifact run-id path]
  (if-let [{url :url} (get-artifact-store)]
    (f/attempt-all [stream     (u/unsafe! (docker/stream-path states/docker-conn run-id path))
                    upload-url (clojure.string/join "/"
                                                    [url
                                                     "bob_artifact"
                                                     group
                                                     name
                                                     number
                                                     artifact])
                    _          (u/unsafe! @(http/post upload-url
                                                      {:multipart [{:name    "data" ;; Another API constraint
                                                                    :content stream}]}))]
      "Ok"
      (f/when-failed [err] err))
    (f/fail "No artifact store registered")))

(comment
  @(register-artifact-store "s3" "http://localhost:8001")

  @(get-registered-artifact-store)

  @(un-register-artifact-store "s3")

  (stream-artifact "dev" "test" "1" "jar")

  @(http/post "http://localhost:8001/bob_artifact/dev/test/1/jar"
              {:multipart [{:name    "data"
                            :content (clojure.java.io/input-stream "build.boot")}]})

  (upload-artifact "dev" "test" "1" "hosts" "92c6692a6471" "/etc/hosts"))
