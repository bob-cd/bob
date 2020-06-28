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

(ns runner.resource.core
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [failjure.core :as f]
            [taoensso.timbre :as log]
            [clj-http.lite.client :as http]
            [crux.api :as crux]
            [runner.docker :as docker])
  (:import [java.io BufferedOutputStream File FileOutputStream]
           [org.kamranzafar.jtar TarInputStream TarOutputStream]))

(defn fetch-resource
  "Downloads a resource(tar file) and returns the stream."
  [url]
  (f/try-all [_        (log/infof "Fetching resource from %s" url)
              ;; TODO: Potential out of memory issues here?
              resource (:body (http/get url {:as :stream}))]
    resource
    (f/when-failed [err]
      (log/errorf "Failed to fetch resource: %s" (f/message err))
      err)))

(defn prefix-dir-on-tar!
  "Adds a prefix to the tar entry paths to make a directory.

  Returns the path to the final archive."
  [in-stream prefix]
  (let [archive    (File/createTempFile "resource" ".tar")
        out-stream (-> archive
                       FileOutputStream.
                       BufferedOutputStream.
                       TarOutputStream.)]
    (loop [entry (.getNextEntry in-stream)]
      (when entry
        (.setName entry
                  (format "%s/%s"
                          prefix
                          (.getName entry)))
        (.putNextEntry out-stream entry)
        (when-not (.isDirectory entry)
          (.transferTo in-stream out-stream)
          (.flush out-stream))
        (recur (.getNextEntry in-stream))))
    (.close in-stream)
    (.close out-stream)
    (.getAbsolutePath archive)))

(defn valid-resource-provider?
  "Checks if the resource is registered."
  [db-client resource]
  (some? (crux/entity (crux/db db-client) (keyword (str "bob.resource-provider/" (:provider resource))))))

(defn url-of
  "Generates a URL for the external resource of a pipeline."
  [db-client resource]
  (let [provider-id (keyword (str "bob.resource-provider/" (:provider resource)))
        url         (:url (crux/entity (crux/db db-client) provider-id))]
    (format "%s/bob_resource?%s"
            url
            (s/join
              "&"
              (map #(format "%s=%s"
                            (name (key %))
                            (val %))
                   (:params resource))))))

(defn initial-image-of
  "Takes an InputStream of the resource, name and image and builds the initial image.
  This image is used by Bob as the starting image which holds the initial
  state for the rest of the steps.

  Works like this:
  - Copy the contents to a container.
  - Commit the container.
  - Return the id of the committed image.
  - Deletes the temp folder."
  [resource-stream image cmd resource-name]
  (f/try-all [_                 (log/debug "Patching tar stream for container mounting")
              archive-path      (-> resource-stream
                                    TarInputStream.
                                    (prefix-dir-on-tar! resource-name))
              _                 (log/debug "Creating temp container for resource mount")
              container-id      (docker/create-container image)
              _                 (log/debug "Copying resources to container")
              _                 (docker/put-container-archive container-id (io/input-stream archive-path) "/root")
              _                 (-> archive-path
                                    File.
                                    .delete)
              _                 (log/debug "Committing resourceful container")
              provisioned-image (docker/commit-image container-id cmd)
              _                 (log/debug "Removing temp container")
              _                 (docker/delete-container container-id)]
    provisioned-image
    (f/when-failed [err]
      (log/errorf "Failed to create initial image: %s" (f/message err))
      err)))

(defn mounted-image-from
  "Mounts a resource prior to a step execution.

  Returns the id of the resource mounted container.

  This works as follows:
  - Checks if the resource is valid.
  - Downloads zip file from the resource url.
  - Expands it to a temp directory.
  - Creates a container using the image.
  - Copies over the contents to the home dir inside the container.
  - Returns the id or the error."
  [db-client resource image]
  (f/try-all [resource-name   (:name resource)
              _               (when (not (valid-resource-provider? db-client resource))
                                (f/fail (str "Invalid external resources, possibly not registered"
                                             resource-name)))
              resource-stream (fetch-resource (url-of db-client resource))]
    (initial-image-of resource-stream image nil resource-name)
    (f/when-failed [err]
      (log/errorf "Failed to generate mounted image: %s" (f/message err))
      err)))

(comment
  (fetch-resource "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=master")

  (http/get "http://localhost:8000/ping")

  (-> (fetch-resource "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=master")
      TarInputStream.
      (prefix-dir-on-tar! "source"))

  (initial-image-of (fetch-resource "http://localhost:8000/test.tar") "busybox:musl" nil "source")

  (require '[runner.system :as sys])

  (def db-client
    (-> sys/system
        :database
        sys/db-client))

  (valid-resource-provider? db-client {:provider "git"})

  (url-of db-client
          {:provider "git"
           :params   {:repo   "a-repo"
                      :branch "a-branch"}})

  (mounted-image-from db-client
                      {:name     "source"
                       :provider "git"
                       :params   {:repo   "a-repo"
                                  :branch "a-branch"}}
                      "busybox:musl"))
