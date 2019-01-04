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

(ns bob.resource.core
  (:require [manifold.deferred :as d]
            [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [ring.util.http-response :as resp]
            [korma.core :as k]
            [bob.db.core :as db]
            [bob.pipeline.internals :as p]
            [bob.execution.internals :as e]
            [bob.resource.internals :as r]
            [bob.util :as u]))

(defn register-external-resource
  "Registers an external resource with an unique name and an URL."
  [name url]
  (d/let-flow [result (u/unsafe! (k/insert db/external-resources
                                           (k/values {:name name
                                                      :url  url})))]
    (if (f/failed? result)
      (resp/conflict "Resource already registered.")
      (u/respond "Ok"))))

(defn un-register-external-resource
  "Unregisters an external resource by its name."
  [name]
  (d/let-flow [_ (u/unsafe! (k/delete db/external-resources
                                      (k/where {:name name})))]
    (u/respond "Ok")))

(defn all-external-resources
  "Lists all external resources by name."
  []
  (d/let-flow [result (u/unsafe! (k/select db/external-resources
                                           (k/fields :name)))]
    (u/respond
     (if (f/failed? result)
       []
       (map #(:name %) result)))))

(defn mount-resources
  "Creates the initial state of the build with a resource.

  This works as follows:
  - Pulls the image of the ppeline as this is the entrypoint for start.
  - Checks if all the resources are valid.
  - Downloads zip file(s) from the resource urls of the pipeline.
  - Expands them to a temp directory.
  - Creates a container using the initial image.
  - Copies over the contents to the dir `/tmp` inside the container.
  - Creates an image from this container and returns its id or the error."
  [pipeline]
  (e/pull (p/image-of pipeline))
  (if (empty? (r/external-resources-of pipeline))
    (p/image-of pipeline)
    (f/attempt-all [invalid (r/invalid-external-resources pipeline)
                    _       (when (not (empty? invalid))
                              (f/fail (str "Invalid resources, possibly not registered: "
                                           (clojure.string/join ", " invalid))))
                    image   (p/image-of pipeline)
                    out-dir (r/fetch-resources (r/external-resources-of pipeline))
                    id      (r/initial-container-of out-dir image)
                    image   (u/unsafe!
                              (docker/commit-container
                                e/conn
                                id
                                (format "%s/%d" id (System/currentTimeMillis))
                                "latest"
                                "sh"))]
      image
      (f/when-failed [err] err))))
