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

(ns bob.resource.internals
  (:require [aleph.http :as http]
            [failjure.core :as f]
            [clj-docker-client.core :as docker]
            [taoensso.timbre :as log]
            [bob.states :as states]
            [bob.resource.db :as db])
  (:import (java.util.zip ZipInputStream)
           (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- rm-r!
  "Recursively deletes a directory."
  [dir & [silently]]
  (log/debugf "Deleting directory %s" dir)
  (f/try*
    (letfn [(delete-f [^File file]
              (when (.isDirectory file)
                (doseq [child-file (.listFiles file)]
                  (delete-f child-file)))
              (clojure.java.io/delete-file file silently))]
      (delete-f (clojure.java.io/file dir)))))

(defn- extract-zip!
  "Takes a java.util.zip.ZipInputStream `zip-stream` and extracts the content to the `out-dir`."
  [^ZipInputStream zip-stream out-dir]
  (log/debugf "Extracting zip file to %s" out-dir)
  (f/try*
    (with-open [stream zip-stream]
      (loop [entry (.getNextEntry stream)]
        (when entry
          (let [save-path (str out-dir File/separatorChar (.getName entry))
                save-file (File. save-path)]
            (if (.isDirectory entry)
              (when-not (.exists save-file)
                (.mkdirs save-file))
              (let [parent-dir (.getParentFile save-file)]
                (when-not (.exists parent-dir)
                  (.mkdirs parent-dir))
                (clojure.java.io/copy stream save-file)))
            (recur (.getNextEntry stream))))))))

(defn url-of
  "Generates a GET URL for the external resource of a pipeline."
  [resource pipeline]
  (let [url    (-> (db/external-resource-url states/db
                                             {:name (:provider resource)})
                   (:url))
        params (db/resource-params-of states/db
                                      {:name     (:name resource)
                                       :pipeline pipeline})]
    (format "%s/bob_resource?%s"
            url
            (clojure.string/join
              "&"
              (map #(format "%s=%s" (:key %) (:value %))
                   params)))))

(defn fetch-resource
  "Downloads a resource(zip file) and expands it to a tmp dir.

  Returns the absolute path of the expansion dir."
  [resource pipeline]
  (f/try-all [creation-args (into-array FileAttribute [])
              out-dir       (str (Files/createTempDirectory "out" creation-args))
              dir           (File. (str out-dir File/separatorChar (:name resource)))
              _             (.mkdirs ^File dir)
              url           (url-of resource pipeline)
              _             (log/infof "Fetching resource %s from %s"
                                       (:name resource)
                                       url)
              stream        (-> @(http/get url)
                                :body
                                (ZipInputStream.))
              _             (extract-zip! stream dir)]
    (.getAbsolutePath ^File dir)
    (f/when-failed [err]
      (log/errorf "Failed to fetch resource: %s" (f/message err))
      err)))

(defn initial-image-of
  "Takes a path to a directory, name and image and builds the initial image.
  This image is used by Bob as the starting image which holds the initial
  state for the rest of the steps.

  Works like this:
  - Copy the contents to a container.
  - Commit the container.
  - Return the id of the committed image.
  - Deletes the temp folder."
  [path image cmd]
  (f/try-all [_                 (log/debug "Creating temp container for resource mount")
              id                (docker/create states/docker-conn image "" {} {})
              _                 (log/debug "Copying resources to container")
              _                 (docker/cp states/docker-conn id path "/root")
              _                 (rm-r! path true)
              _                 (log/debug "Committing resourceful container")
              provisioned-image (docker/commit-container
                                  states/docker-conn
                                  id
                                  (format "%s/%d" id (System/currentTimeMillis))
                                  "latest"
                                  cmd)
              _                 (log/debug "Removing temp container")
              _                 (docker/rm states/docker-conn id)]
    provisioned-image
    (f/when-failed [err]
      (log/errorf "Failed to create initial image: %s" (f/message err))
      err)))

(defn add-params
  "Saves the map of GET params to be sent to the resource."
  [resource-name params pipeline]
  (when (seq params)
    (log/debugf "Adding params %s for resource %s" params resource-name)
    (db/insert-resource-params states/db
                               {:params (map #(vector (clojure.core/name (first %))
                                                      (last %)
                                                      resource-name
                                                      pipeline)
                                             params)})))

(defn valid-external-resource?
  "Checks if the resource has a valid URL."
  [resource]
  (some? (db/invalid-external-resources states/db
                                        {:name (:provider resource)})))

(defn get-resource-params
  "Fetches list of parameters associated with the resource"
  [pipeline name]
  (reduce
    (fn [r {:keys [key value]}]
      (assoc r (keyword key) value))
    {}
    (db/resource-params-of states/db
                           {:name     name
                            :pipeline pipeline})))

(comment
  (def resource {:name     "my-source"
                 :type     "external"
                 :provider "git"
                 :params   {}})

  (valid-external-resource? resource)

  (url-of resource "dev:test")

  (->> (db/resource-params-of states/db
                              {:name     "my-source"
                               :pipeline "dev:test"})))
