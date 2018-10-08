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
            [bob.util :as u]
            [bob.plugin.core :as plug])
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
                  out-dir       (Files/createTempDirectory "out" creation-args)
                  _             (->> resources
                                     (map :url)
                                     (pmap #(-> @(http/get %)
                                                :body
                                                (ZipInputStream.)))
                                     (run! #(extract-zip! % out-dir)))]
    (str out-dir)
    (f/when-failed [err] err)))

(defn initial-container-of
  "Takes a path to a directory, name and image and builds the initial image.
   This image is used by Bob as the starting image which holds the initial
   state for the rest of the steps. Works by copying the contents to the
   container and returns the id of the container. Deletes the temp folder
   after it."
  [^String path ^String image]
  (f/attempt-all [img (docker/pull e/conn image)
                  id  (docker/create e/conn img "sh" {} {})
                  _   (docker/cp e/conn id path "/tmp")
                  _   (rm-r! path true)]
    id
    (f/when-failed [err] err)))

(defn resources-of
  "Fetches all the resources of a pipeline, updating the URLs for bob."
  [pipeline]
  (->> (k/select db/resources
                 (k/fields :name :type :plugins.url)
                 (k/join db/plugins
                         (= :plugins.name :name))
                 (k/where {:pipeline pipeline}))
       (map #(if (and (not (nil? (% :url)))
                      (= (% :type) "plugin"))
               (update-in % [:url] (fn [_]
                                     (plug/url-of (% :name) pipeline)))
               %))))

(defn invalid-resources
  "Returns all the invalid resources of a pipeline.

  Checks if all the resources of a given pipeline:

  - Have a valid URL.
  - TODO: Check heathiness of the resources"
  [pipeline]
  (->> (resources-of pipeline)
       (filter #(nil? (:url %)))
       (map #(:name %))))
