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

(ns bob.artifact.core
  (:require [manifold.deferred :as d]
            [ring.util.http-response :as res]
            [failjure.core :as f]
            [bob.util :as u]
            [bob.artifact.internals :as a]))

(defn stream-artifact
  [name group number artifact]
  (d/let-flow [pipeline (u/name-of name group)
               id       (a/artifact-container-of pipeline number)
               path     (a/get-artifact-path-of pipeline artifact)
               stream   (a/get-stream-of path id)]
    (if (f/failed? stream)
      (res/bad-request {:message "No such artifact."})
      {:status  200
       :headers {"Content-Type"        "archive/tar"
                 "Content-Disposition" (format "attachement; filename=%s.tar"
                                               artifact)}
       :body    stream})))

(comment
  @(stream-artifact "test" "test" 1 "source"))
