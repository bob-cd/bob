; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns runner.resource
  (:require
   [babashka.http-client :as http]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as spec]
   [clojure.string :as s]
   [clojure.tools.logging :as log]
   [common.schemas]
   [failjure.core :as f]
   [runner.artifact :as a]
   [runner.engine :as eng]
   [xtdb.api :as xt])
  (:import
   [java.io BufferedOutputStream File FileOutputStream]
   [org.kamranzafar.jtar TarInputStream TarOutputStream]))

(defn fetch-resource
  "Downloads a resource(tar file) and returns the stream."
  [url]
  (f/try-all [_ (log/infof "Fetching resource from %s" url)
              {:keys [body]} (try
                               (http/get url {:as :stream})
                               (catch Exception _
                                 (f/fail (str "Error connecting to " url))))]
    body
    (f/when-failed [err]
      (log/errorf "Failed to fetch resource: %s" (f/message err))
      err)))

(defn prefix-dir-on-tar!
  "Adds a prefix to the tar entry paths to make a directory.

  Returns the path to the final archive."
  [^TarInputStream in-stream prefix]
  (let [archive (File/createTempFile "resource" ".tar")
        out-stream (-> archive
                       FileOutputStream/new
                       BufferedOutputStream/new
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
  (some? (xt/entity (xt/db db-client) (keyword "bob.resource-provider" (:provider resource)))))

(defn url-of
  "Generates a URL for the resource fetch of a pipeline."
  [db-client {:keys [name type provider params]}]
  (case type
    "external" (let [{:keys [url] :as rp} (->> provider
                                               (str "bob.resource-provider/")
                                               keyword
                                               (xt/entity (xt/db db-client)))
                     _ (when-not (spec/valid? :bob.db/resource-provider rp)
                         (throw (Exception. (str "Invalid resource provider: " rp))))
                     query (s/join "&"
                                   (map #(format "%s=%s"
                                                 (clojure.core/name (key %))
                                                 (val %))
                                        params))]
                 (format "%s/bob_resource?%s"
                         url
                         query))
    "internal" (let [base-url (a/store-url db-client provider)
                     resource-name name
                     {:keys [group name run-id]} params]
                 (format "%s/bob_artifact/%s/%s/%s/%s"
                         base-url
                         group
                         name
                         run-id
                         resource-name))))

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
  (f/try-all [_ (log/debug "Patching tar stream for container mounting")
              archive-path (-> resource-stream
                               TarInputStream.
                               (prefix-dir-on-tar! resource-name))
              _ (log/debug "Creating temp container for resource mount")
              container-id (eng/create-container image)
              _ (log/debug "Copying resources to container")
              _ (eng/put-container-archive container-id (io/input-stream archive-path) "/root")
              _ (-> ^String archive-path
                    File/new
                    .delete)
              _ (log/debug "Committing resourceful container")
              provisioned-image (eng/commit-container container-id cmd)
              _ (log/debug "Removing temp container")
              _ (eng/delete-container container-id)]
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
  (f/try-all [resource-name (:name resource)
              _ (when (not (valid-resource-provider? db-client resource))
                  (f/fail (str "Invalid external resources, possibly not registered "
                               resource-name)))
              resource-stream (fetch-resource (url-of db-client resource))]
    (initial-image-of resource-stream image nil resource-name)
    (f/when-failed [err]
      (log/errorf "Failed to generate mounted image: %s" (f/message err))
      err)))

(comment
  (set! *warn-on-reflection* true)

  (fetch-resource "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=main")

  (http/get "http://localhost:8000/ping")

  (-> (fetch-resource "http://localhost:8000/bob_resource?repo=https://github.com/lispyclouds/bob-example&branch=main")
      TarInputStream.
      (prefix-dir-on-tar! "source"))

  (initial-image-of (fetch-resource "http://localhost:8000/test.tar") "busybox:musl" nil "source"))
