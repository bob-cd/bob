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
            [ring.util.http-response :as res]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [bob.util :as u]
            [bob.states :as states]
            [bob.artifact.db :as db]))

(def artifact-prefix "artifact/")

(defn register-artifact-store
  "Registers an artifact store with an unique name and an URL."
  [name url]
  (d/let-flow [result (f/try* (db/register-artifact-store
                                states/db
                                {:name (str artifact-prefix name)
                                 :url  url}))]
    (if (f/failed? result)
      (res/conflict "Artifact store already registered")
      (u/respond "Ok"))))

(defn un-register-artifact-store
  "Unregisters an artifact-store resource by its name."
  [name]
  (d/let-flow [_ (db/un-register-artifact-store
                   states/db
                   {:name (str artifact-prefix name)})]
    (u/respond "Ok")))

(defn get-registered-artifact-store
  "Gets the registered artifact store."
  []
  (d/let-flow [result (f/try* (db/get-artifact-store states/db))]
    (if (f/failed? result)
      (res/bad-request (f/message result))
      (u/respond (-> result
                     (update-in [:name] #(last (clojure.string/split % #"/"))))))))

(defn stream-artifact
  "Connects to the registered atrifact store and streams the artifact back if exists.

  Contrstucts the following URL to connect:
  <URL of regsitered store>/bob_artifact/dev/test/1/jar"
  [group name number artifact]
  (if-let [{url :url} (db/get-artifact-store states/db)]
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
  (if-let [{url :url} (db/get-artifact-store states/db)]
    (f/try-all [stream     (docker/stream-path states/docker-conn run-id path)
                upload-url (clojure.string/join "/"
                                                [url
                                                 "bob_artifact"
                                                 group
                                                 name
                                                 number
                                                 artifact])
                _          @(http/post upload-url
                                       {:multipart [{:name    "data" ;; Another API constraint
                                                     :content stream}]})]
      "Ok"
      (f/when-failed [err] err))
    (f/fail "No artifact store registered")))

(comment
  (db/register-artifact-store states/db {:name "artifact/s3"
                                         :url  "http://localhost:8001"})

  (db/get-artifact-store states/db)

  (db/un-register-artifact-store states/db {:name "artifact/s3"})

  (stream-artifact "dev" "test" "1" "jar")

  @(http/post "http://localhost:8001/bob_artifact/dev/test/1/jar"
              {:multipart [{:name    "data"
                            :content (clojure.java.io/input-stream "build.boot")}]})

  (upload-artifact "dev" "test" "1" "hosts" "92c6692a6471" "/etc/hosts"))