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
            [korma.core :as k]
            [clj-docker-client.core :as docker]
            [bob.db.core :as db]
            [bob.execution.internals :as e]
            [bob.util :as u])
  (:import (java.util.zip ZipInputStream)
           (java.io File)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(defn- rm-r!
  "Recursively deletes a directory."
  [dir & [silently]]
  (u/unsafe!
    (letfn [(delete-f [^File file]
              (when (.isDirectory file)
                (doseq [child-file (.listFiles file)]
                  (delete-f child-file)))
              (clojure.java.io/delete-file file silently))]
      (delete-f (clojure.java.io/file dir)))))

(defn- extract-zip!
  "Takes a java.util.zip.ZipInputStream `zip-stream` and extracts the content to the `out-dir`."
  [^ZipInputStream zip-stream out-dir]
  (u/unsafe!
    (with-open [stream zip-stream]
      (loop [entry (.getNextEntry stream)]
        (when entry
          (let [savePath (str out-dir File/separatorChar (.getName entry))
                saveFile (File. savePath)]
            (if (.isDirectory entry)
              (if-not (.exists saveFile)
                (.mkdirs saveFile))
              (let [parentDir (.getParentFile saveFile)]
                (if-not (.exists parentDir)
                  (.mkdirs parentDir))
                (clojure.java.io/copy stream saveFile)))
            (recur (.getNextEntry stream))))))))

(defn- url-of
  "Generates a GET URL for the external resource of a pipeline."
  [resource pipeline]
  (let [url    (-> (k/select db/external-resources
                             (k/where {:name (:provider resource)})
                             (k/fields :url))
                   (first)
                   (:url))
        params (k/select db/resource-params
                         (k/where {:name     (:name resource)
                                   :pipeline pipeline})
                         (k/fields :key :value))]
    (format "%s/bob_request?%s"
            url
            (clojure.string/join
              "&"
              (map #(format "%s=%s" (:key %) (:value %))
                   params)))))

(defn fetch-resource
  "Downloads a resource(zip file) and expands it to a tmp dir.

  Returns the absolute path of the expansion dir."
  [resource pipeline]
  (f/attempt-all [creation-args (into-array FileAttribute [])
                  out-dir       (u/unsafe! (str (Files/createTempDirectory "out" creation-args)))
                  dir           (File. (str out-dir File/separatorChar (:name resource)))
                  _             (u/unsafe! (.mkdirs ^File dir))
                  url           (url-of resource pipeline)
                  stream        (u/unsafe! (-> @(http/get url)
                                           :body
                                           (ZipInputStream.)))
                  _             (extract-zip! stream dir)]
    out-dir
    (f/when-failed [err] err)))

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
  (f/attempt-all [id (u/unsafe! (docker/create e/conn image "" {} {}))
                  _  (u/unsafe! (docker/cp e/conn id path "/root"))
                  _  (u/unsafe! (rm-r! path true))]
    (docker/commit-container
      e/conn
      id
      (format "%s/%d" id (System/currentTimeMillis))
      "latest"
      cmd)
    (f/when-failed [err] err)))

(defn add-params
  "Saves the map of GET params to be sent to the resource."
  [resource-name params pipeline]
  (when (not (empty? params))
    (k/insert db/resource-params
              (k/values (map #(hash-map :key (clojure.core/name (first %))
                                        :value (last %)
                                        :name resource-name
                                        :pipeline pipeline)
                             params)))))

(defn valid-external-resource?
  "Checks if the resource has a valid URL."
  [resource]
  (not (empty? (k/select db/external-resources
                         (k/where {:name (:provider resource)
                                   :url  [not= nil]})))))

(comment
  (def resource {:name     "source"
                 :type     "external"
                 :provider "git"
                 :params   {}})
  (valid-external-resource? resource)
  (url-of resource "test:test")
  (-> (fetch-resource resource "test:test")
      (initial-image-of "busybox:musl")))
