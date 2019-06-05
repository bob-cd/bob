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

(ns bob.api.routes
  (:require [compojure.route :as route]
            [compojure.api.sweet :as rest]
            [schema.core :as s]
            [bob.util :as u]
            [bob.api.schemas :as schema]
            [bob.api.middleware :as m]
            [bob.execution.core :as e]
            [bob.pipeline.core :as p]
            [bob.resource.core :as r]
            [bob.artifact.core :as a]))

(def bob-api
  (m/ignore-trailing-slash
    (rest/api
     {:swagger
      {:ui   "/"
       :spec "/swagger.json"
       :data {:info     {:title       "Bob the Builder"
                         :version     "0.1"
                         :description "The modular, extensible CI/CD platform."}
              :consumes ["application/json"]
              :produces ["application/json"]}}}

      (rest/context "/api" []
        :tags ["Bob's API"]

        (rest/POST "/pipeline/:group/:name" []
          :return schema/SimpleResponse
          :path-params [group
                        :- String
                        name
                        :- String]
          :body [pipeline schema/Pipeline]
          :summary "Creates a new pipeline in a group with the specified name.
                   Takes list of steps, the base docker image, a list of environment vars
                   and a list of artifacts generated from pipeline as POST body."
          (p/create
            group
            name
            (:steps pipeline)
            (:vars pipeline)
            (:resources pipeline)
            (:image pipeline)))

        (rest/POST "/pipeline/start/:group/:name" []
          :return schema/SimpleResponse
          :path-params [group
                        :- String
                        name
                        :- String]
          :summary "Starts a pipeline in a group with the specified name."
          (p/start group name))

        (rest/POST "/pipeline/stop/:group/:name/:number" []
          :return schema/SimpleResponse
          :path-params [group
                        :- String
                        name
                        :- String
                        number
                        :- s/Int]
          :summary "Stops a pipeline run in a group with the specified name and number."
          (p/stop group name number))

        (rest/GET "/pipeline/logs/:group/:name/:number/:offset/:lines" []
          :return schema/LogsResponse
          :path-params [group
                        :- String
                        name
                        :- String
                        number
                        :- s/Int
                        offset
                        :- s/Int
                        lines
                        :- s/Int]
          :summary "Fetches logs for a pipeline run in a group with the specified
                    name, number, starting offset and the number of lines."
          (p/logs-of group name number offset lines))

        (rest/GET "/pipeline/status/:group/:name/:number" []
          :return schema/StatusResponse
          :path-params [group
                        :- String
                        name
                        :- String
                        number
                        :- s/Int]
          :summary "Fetches the status of pipeline run in a group with the specified name and number."
          (p/status group name number))

        (rest/DELETE "/pipeline/:group/:name" []
          :return schema/SimpleResponse
          :path-params [group
                        :- String
                        name
                        :- String]
          :summary "Deletes a pipeline in a group with the specified name."
          (p/remove group name))

        (rest/GET "/pipeline/status/running" []
          :return schema/RunningResponse
          :summary "Returns list of the running pipeline names."
          (p/running-pipelines))

        (rest/POST "/external-resource/:name" []
          :return schema/SimpleResponse
          :path-params [name
                        :- String]
          :body [attrs schema/ResourceAttributes]
          :summary "Registers an external resource with a unique name and its attributes."
          (r/register-external-resource name (:url attrs)))

        (rest/DELETE "/external-resource/:name" []
          :return schema/SimpleResponse
          :path-params [name
                        :- String]
          :summary "Un-registers an external resource with a unique name."
          (r/un-register-external-resource name))

        (rest/GET "/external-resources" []
          :return schema/ResourceResponse
          :summary "Lists all registered external resources by name."
          (r/all-external-resources))

        (rest/GET "/pipeline/:group/:name/:number/artifact/:artifact-name" []
          :summary "Returns the artifact archive of a pipeline"
          :path-params [group
                        :- String
                        name
                        :- String
                        number
                        :- s/Int
                        artifact-name
                        :- String]
          (a/stream-artifact group name number artifact-name))

        (rest/POST "/artifact-store/:name" []
          :return schema/SimpleResponse
          :path-params [name
                        :- String]
          :body [attrs schema/ArtifactStoreAttributes]
          :summary "Registers an artifact store by a unique name and its URL."
          (a/register-artifact-store name (:url attrs)))

        (rest/DELETE "/artifact-store/:name" []
          :return schema/SimpleResponse
          :path-params [name
                        :- String]
          :summary "Un-registers an external resource with a unique name."
          (a/un-register-artifact-store name))

        (rest/GET "/artifact-store" []
          :return schema/ArtifactStoreResponse
          :summary "Lists the registered artifact store."
          (a/get-registered-artifact-store))

        ;; TODO: Actually do some health checks here.
        (rest/GET "/can-we-build-it" []
          :return schema/SimpleResponse
          :summary "Runs health checks for Bob."
          (u/respond "Yes we can! \uD83D\uDD28 \uD83D\uDD28"))

        (rest/POST "/gc" []
          :return schema/SimpleResponse
          :summary "Runs the garbage collection for Bob, reclaiming resources."
          (e/gc))

        (rest/POST "/gc/all" []
          :return schema/SimpleResponse
          :summary "Runs the full garbage collection for Bob, reclaiming all resources."
          (e/gc true)))

      (rest/undocumented
        (route/not-found (u/respond "Took a wrong turn?"))))))
