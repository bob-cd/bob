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
            [bob.resource.internals :as r]
            [bob.util :as u]
            [bob.resource.db :as db]
            [bob.states :as states]))

(defn register-external-resource
  "Registers an external resource with an unique name and an URL."
  [name url]
  (d/let-flow [result (f/try* (db/insert-external-resource states/db
                                                           {:name name
                                                            :url  url}))]
    (if (f/failed? result)
      (resp/conflict "Resource already registered.")
      (u/respond "Ok"))))

(defn un-register-external-resource
  "Unregisters an external resource by its name."
  [name]
  (d/let-flow [_ (f/try* (db/delete-external-resource states/db
                                                      {:name name}))]
    (u/respond "Ok")))

(defn all-external-resources
  "Lists all external resources by name."
  []
  (d/let-flow [result (f/try* (db/external-resources states/db))]
    (u/respond
      (if (f/failed? result)
        []
        (map #(:name %) result)))))

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
  (f/try-all [_       (when (not (r/valid-external-resource? resource))
                        (f/fail (str "Invalid external resources, possibly not registered"
                                     (:name resource))))
              out-dir (r/fetch-resource resource pipeline)]
    (r/initial-image-of out-dir image nil)
    (f/when-failed [err] err)))

(comment
  (all-external-resources)

  (register-external-resource "git" "http://localhost:8000")

  (db/insert-external-resource states/db
                               {:name "git"
                                :url  "http://localhost:8000"})

  (db/delete-external-resource states/db {:name "git"}))
