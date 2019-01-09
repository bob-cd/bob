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

(defn fetch-resources
  "Downloads a resource(zip file) and expands it to a tmp dir.
  Returns the absolute path of the expansion dir."
  [resources]
  (f/attempt-all [creation-args (into-array FileAttribute [])
                  out-dir       (u/unsafe! (Files/createTempDirectory "out" creation-args))
                  _             (u/unsafe! (->> resources
                                                (map :url)
                                                (pmap #(-> @(http/get %)
                                                           :body
                                                           (ZipInputStream.)))
                                                (run! #(extract-zip! % out-dir))))]
    (str out-dir)
    (f/when-failed [err] err)))

(defn initial-container-of
  "Takes a path to a directory, name and image and builds the initial image.
   This image is used by Bob as the starting image which holds the initial
   state for the rest of the steps. Works by copying the contents to the
   container and returns the id of the container. Deletes the temp folder
   after it."
  [^String path ^String image]
  (f/attempt-all [id (u/unsafe! (docker/create e/conn image "sh" {} {}))
                  _  (u/unsafe! (docker/cp e/conn id path "/tmp"))
                  _  (u/unsafe! (rm-r! path true))]
    id
    (f/when-failed [err] err)))

(defn add-params
  "Saves the map of GET params to be sent to the resource."
  [name params pipeline]
  (when (not (empty? params))
    (k/insert db/resource-params
              (k/values (map #(hash-map :key (clojure.core/name (first %))
                                        :value (last %)
                                        :name name
                                        :pipeline pipeline)
                             params)))))

(defn url-of
  "Generates a GET URL for the external resource of a pipeline."
  [name pipeline]
  (let [url    (-> (k/select db/external-resources
                             (k/where {:name name})
                             (k/fields :url))
                   (first)
                   (:url))
        params (k/select db/resource-params
                         (k/where {:name     name
                                   :pipeline pipeline})
                         (k/fields :key :value))]
    (format "%s/bob_request?%s"
            url
            (clojure.string/join
              "&"
              (map #(format "%s=%s" (:key %) (:value %))
                   params)))))

(defn external-resources-of
  "Fetches all the external resources of a pipeline, updating the URLs for bob."
  [pipeline]
  (->> (k/select db/resources
                 (k/fields :name :type :external_resources.url)
                 (k/join db/external-resources
                         (= :external_resources.name :name))
                 (k/where {:pipeline pipeline}))
       (map #(if (and (not (nil? (% :url)))
                      (= (% :type) "external"))
               (update-in % [:url] (fn [_]
                                     (url-of (% :name) pipeline)))
               %))))

(defn invalid-external-resources
  "Returns all the invalid resources of a pipeline.

  Checks if all the resources of a given pipeline
  have a valid URL."
  [pipeline]
  (->> (external-resources-of pipeline)
       (filter #(nil? (:url %)))
       (map #(:name %))))
