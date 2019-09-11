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

(ns bob.api.schemas
  (:require [schema.core :as s])
  (:import (clojure.lang Keyword)))

(s/defschema SimpleResponse {:message String})

(s/defschema SupportedResourceTypes (s/enum "external"))

(s/defschema Resource {:name     String
                       :params   {Keyword String}
                       :type     SupportedResourceTypes
                       :provider String})

(s/defschema Artifact {:name  String
                       :path  String
                       :store String})

(s/defschema Step {(s/required-key :cmd)               String
                   (s/optional-key :needs_resource)    String
                   (s/optional-key :produces_artifact) Artifact})

(s/defschema Pipeline {:steps                      [Step]
                       :image                      String
                       (s/optional-key :vars)      {Keyword String}
                       (s/optional-key :resources) [Resource]})

(s/defschema LogsResponse (s/either {:message [String]}
                                    SimpleResponse))

(s/defschema StatusResponse {:message (s/enum :running :passed :failed)})

(s/defschema RunningResponse {:message [{:group String
                                         :name  String}]})

(s/defschema ResourceAttributes {:url String})

(s/defschema ResourceResponse {:message [String]})

(s/defschema ArtifactStoreAttributes {:url String})

(s/defschema ArtifactStoreResponse {:message [{:name String
                                               :url  String}]})

(comment
  (s/validate Step
              {:cmd               "echo hello"
               :needs_resource    "src"
               :produces_artifact {:name "jar"
                                   :path "target"}})

  (s/validate LogsResponse
              {:message "Failed"})

  (s/validate LogsResponse
              {:message ["log line 1" "log line 2"]}))
