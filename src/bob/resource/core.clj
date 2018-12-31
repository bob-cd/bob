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
  (:require [clj-docker-client.core :as docker]
            [failjure.core :as f]
            [bob.pipeline.internals :as p]
            [bob.execution.internals :as e]
            [bob.resource.internals :as r]))

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
  (if (empty? (r/resources-of pipeline))
    (p/image-of pipeline)
    (f/attempt-all [invalid (r/invalid-resources pipeline)
                    _       (when (not (empty? invalid))
                              (f/fail (str "Invalid resources, possibly not registered: "
                                           (clojure.string/join ", " invalid))))
                    image   (p/image-of pipeline)
                    out-dir (r/fetch-resources (r/resources-of pipeline))
                    id      (r/initial-container-of out-dir image)
                    image   (docker/commit-container
                              e/conn
                              id
                              (format "%s/%d" id (System/currentTimeMillis))
                              "latest"
                              "sh")]
      image
      (f/when-failed [err] err))))
