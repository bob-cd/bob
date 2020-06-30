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

(ns runner.artifact.core
  (:require [clojure.string :as s]
            [taoensso.timbre :as log]
            [clj-http.client :as http]
            [crux.api :as crux]
            [failjure.core :as f]
            [runner.docker :as docker]))

(defn upload-artifact
  "Opens up a stream to the path in a container by id and POSTs it to the artifact store."
  [db-client group name run-id artifact-name container-id path store-name]
  (if-let [{url :url} (crux/entity (crux/db db-client) (keyword (str "bob.artifact-store/" store-name)))]
    (f/try-all [_          (log/debugf "Streaming from container %s on path %s"
                                       container-id
                                       path)
                stream     (docker/get-container-archive container-id path)
                upload-url (s/join "/" [url "bob_artifact" group name run-id artifact-name])
                _          (log/infof "Uploading artifact %s for pipeline %s run %s to %s"
                                      artifact-name
                                      name
                                      run-id
                                      upload-url)
                _          (http/post upload-url
                                      {:multipart [{:name    "data" ; Another API constraint
                                                    :content stream}]})]
      "Ok"
      (f/when-failed [err]
        (log/errorf "Error in uploading artifact: %s" (f/message err))
        (f/try*
          (docker/delete-container run-id))
        err))
    (do
      (log/error "Error locating Artifact Store")
      (f/try*
        (docker/delete-container run-id))
      (f/fail "No such artifact store registered"))))

(comment
  (require '[runner.system :as sys]
           '[clojure.java.io :as io])

  (def db-client
    (-> sys/system
        :database
        sys/db-client))

  (http/post "http://localhost:8001/bob_artifact/dev/test/r-1/tar"
             {:multipart [{:name    "data"
                           :content (io/input-stream "test/test.tar")}]})

  (upload-artifact db-client "dev" "test" "r-1" "jar" "conny" "/root" "local"))
