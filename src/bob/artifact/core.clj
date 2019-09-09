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
            [taoensso.timbre :as log]
            [bob.util :as u]
            [bob.states :as states]
            [bob.artifact.db :as db]))

(defn register-artifact-store
  "Registers an artifact store with an unique name and an URL."
  [name url]
  (d/let-flow [result (f/try* (db/register-artifact-store
                                states/db
                                {:name name
                                 :url  url}))]
    (if (f/failed? result)
      (do (log/errorf "Could not register Artifact Store: %s" (f/message result))
          (res/conflict "Artifact Store may already be registered"))
      (do (log/infof "Registered Artifact Store %s at %s" name url)
          (u/respond "Ok")))))

(defn un-register-artifact-store
  "Unregisters an artifact-store resource by its name."
  [name]
  (d/let-flow [_ (db/un-register-artifact-store
                   states/db
                   {:name name})]
    (do (log/infof "Un-registered Artifact Store %s" name)
        (u/respond "Ok"))))

(defn get-registered-artifact-stores
  "Gets the registered artifact store."
  []
  (d/let-flow [result (f/try* (db/get-artifact-stores states/db))]
    (if (f/failed? result)
      (do (log/errorf "Could not get Artifact Store details: %s" (f/message result))
          (res/bad-request (f/message result)))
      (u/respond result))))

(defn stream-artifact
  "Connects to the registered atrifact store and streams the artifact back if exists.

  Contrstucts the following URL to connect:
  <URL of regsitered store>/bob_artifact/dev/test/1/jar"
  [group name number artifact store-name]
  (if-let [{url :url} (db/get-artifact-store states/db {:name store-name})]
    (d/let-flow [fetch-url (clojure.string/join "/"
                                                [url
                                                 "bob_artifact"
                                                 group
                                                 name
                                                 number
                                                 artifact])
                 _         (log/infof "Fetching artifact %s for pipeline %s run %d from %s"
                                      artifact
                                      name
                                      number
                                      fetch-url)
                 data      (http/get fetch-url {:throw-exceptions false})]
      (if (= 200 (:status data))
        {:status  200
         :headers {"Content-Type"        "application/tar"
                   "Content-Disposition" (format "attachment; filename=%s.tar" artifact)}
         :body    (:body data)}
        (do (log/errorf "Error fetching artifact: %s" data)
            (res/not-found "No such artifact"))))
    (do (log/error "Error locating Artifact Store")
        (res/bad-request "No such artifact store registered"))))

(defn upload-artifact
  "Opens up a stream to the path in a container by id and POSTs it to the artifact store.

  Returns a Failure object if failed."
  [group name number artifact run-id path store-name]
  (if-let [{url :url} (db/get-artifact-store states/db {:name store-name})]
    (f/try-all [stream     (docker/stream-path states/docker-conn run-id path)
                upload-url (clojure.string/join "/"
                                                [url
                                                 "bob_artifact"
                                                 group
                                                 name
                                                 number
                                                 artifact])
                _          (log/infof "Uploading artifact %s for pipeline %s run %d to %s"
                                      artifact
                                      name
                                      number
                                      upload-url)
                _          @(http/post upload-url
                                       {:multipart [{:name    "data" ;; Another API constraint
                                                     :content stream}]})]
      "Ok"
      (f/when-failed [err]
        (log/errorf "Error in uploading artifact: %s" (f/message err))
        err))
    (do (log/error "Error locating Artifact Store")
        (f/fail "No such artifact store registered"))))

(comment
  (db/register-artifact-store states/db {:name "s3"
                                         :url  "http://localhost:8001"})

  (db/get-artifact-store states/db)

  (db/un-register-artifact-store states/db {:name "s3"})

  (stream-artifact "dev" "test" "1" "jar" "s3")

  @(http/post "http://localhost:8001/bob_artifact/dev/test/1/jar"
              {:multipart [{:name    "data"
                            :content (clojure.java.io/input-stream "build.boot")}]})

  (upload-artifact "dev" "test" "1" "hosts" "92c6692a6471" "/etc/hosts" "s3")

  (log/errorf "Shizzz %s" "Oh noes!"))
