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
            [failjure.core :as f]
            [ring.util.http-response :as resp]
            [korma.core :as k]
            [bob.db.core :as db]
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

(defn get-resource
  [name pipeline]
  (first (k/select db/resources
                   (k/where {:name     name
                             :pipeline pipeline}))))

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
  [resource pipeline image]
  (f/attempt-all [_       (when (not (r/valid-external-resource? resource))
                            (f/fail (str "Invalid external resources, possibly not registered"
                                         (:name resource))))
                  out-dir (r/fetch-resource resource pipeline)]
    (r/initial-image-of out-dir image (:cmd resource))
    (f/when-failed [err] err)))

(comment
  (get-resource "source" "test:test")
  (all-external-resources)
  (register-external-resource "git" "http://localhost:8000"))
