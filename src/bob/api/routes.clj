;   This file is part of Bob.
;
;   Bob is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Affero General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   Bob is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Affero General Public License for more details.
;
;   You should have received a copy of the GNU Affero General Public License
;   along with Bob. If not, see <http://www.gnu.org/licenses/>.

(ns bob.api.routes
  (:require [compojure.route :as route]
            [compojure.api.sweet :as rest]
            [schema.core :as s]
            [bob.util :as u]
            [bob.api.health :as health]
            [bob.api.schemas :as schema]
            [bob.api.middleware :as m]
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

      (rest/POST "/pipelines/groups/:group/names/:name" []
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

      (rest/POST "/pipelines/start/groups/:group/names/:name" []
        :return schema/SimpleResponse
        :path-params [group
                      :- String
                      name
                      :- String]
        :summary "Starts a pipeline in a group with the specified name."
        (p/start group name))

      (rest/POST "/pipelines/stop/groups/:group/names/:name/number/:number" []
        :return schema/SimpleResponse
        :path-params [group
                      :- String
                      name
                      :- String
                      number
                      :- s/Int]
        :summary "Stops a pipeline run in a group with the specified name and number."
        (p/stop group name number))

      (rest/GET "/pipelines/logs/groups/:group/names/:name/number/:number/offset/:offset/lines/:lines" []
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

      (rest/GET "/pipelines/status/groups/:group/names/:name/number/:number" []
        :return schema/StatusResponse
        :path-params [group
                      :- String
                      name
                      :- String
                      number
                      :- s/Int]
        :summary "Fetches the status of pipeline run in a group with the specified name and number."
        (p/status group name number))

      (rest/DELETE "/pipelines/groups/:group/names/:name" []
        :return schema/SimpleResponse
        :path-params [group
                      :- String
                      name
                      :- String]
        :summary "Deletes a pipeline in a group with the specified name."
        (p/remove-pipeline group name))

       (rest/GET "/pipelines" []
        :return schema/PipelinesResponse
        :query-params [{group :- String nil}
                       {name :- String nil}
                       {status :- String nil}]
        :summary "Returns all defined Pipelines. Search params are case sensitive :-)"
        (p/get-pipelines group name status))

      (rest/POST "/external-resources/:name" []
        :return schema/SimpleResponse
        :path-params [name
                      :- String]
        :body [attrs schema/ResourceAttributes]
        :summary "Registers an external resource with a unique name and its attributes."
        (r/register-external-resource name (:url attrs)))

      (rest/DELETE "/external-resources/:name" []
        :return schema/SimpleResponse
        :path-params [name
                      :- String]
        :summary "Un-registers an external resource with a unique name."
        (r/un-register-external-resource name))

      (rest/GET "/external-resources" []
        :return schema/ResourceResponse
        :summary "Lists all registered external resources by name."
        (r/all-external-resources))

      (rest/GET "/pipelines/groups/:group/names/:name/number/:number/artifacts/store/:store-name/name/:artifact-name" []
        :summary "Returns the artifact archive of a pipeline"
        :path-params [group
                      :- String
                      name
                      :- String
                      number
                      :- s/Int
                      artifact-name
                      :- String
                      store-name
                      :- String]
        (a/stream-artifact group name number artifact-name store-name))

      (rest/POST "/artifact-stores/:name" []
        :return schema/SimpleResponse
        :path-params [name
                      :- String]
        :body [attrs schema/ArtifactStoreAttributes]
        :summary "Registers an artifact store by a unique name and its URL."
        (a/register-artifact-store name (:url attrs)))

      (rest/DELETE "/artifact-stores/:name" []
        :return schema/SimpleResponse
        :path-params [name
                      :- String]
        :summary "Un-registers an external resource with a unique name."
        (a/un-register-artifact-store name))

      (rest/GET "/artifact-stores" []
        :return schema/ArtifactStoreResponse
        :summary "Lists the registered artifact store."
        (a/get-registered-artifact-stores))

      (rest/GET "/can-we-build-it" []
        :return schema/SimpleResponse
        :summary "Runs health checks for Bob."
        (health/respond-to-health-check))

    (rest/undocumented
     (route/not-found (u/respond "Took a wrong turn?")))))))
