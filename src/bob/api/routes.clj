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
            [compojure.api.sweet :refer [api context GET undocumented]]
            [bob.util :refer [respond]]
            [bob.api.schemas :refer :all]
            [bob.execution.core :refer [gc]]
            [bob.api.middleware :refer [ignore-trailing-slash]]))

(def bob-api
  (ignore-trailing-slash
    (api
      {:swagger
       {:ui   "/"
        :spec "/swagger.json"
        :data {:info     {:title       "Bob the Builder"
                          :description "The modular, extensible CI/CD platform."}
               :consumes ["application/json"]
               :produces ["application/json"]}}}

      (context "/api" []
        :tags ["Bob's API"]

        (GET "/can-we-build-it" []
          :return SimpleResponse
          :summary "Runs health checks for Bob."
          (respond "Yes we can! \uD83D\uDD28 \uD83D\uDD28"))

        (GET "/gc" []
          :return SimpleResponse
          :summary "Runs the garbage collection for Bob, reclaiming resources."
          (gc))

        (GET "/gc/all" []
          :return SimpleResponse
          :summary "Runs the full garbage collection for Bob, reclaiming all resources."
          (gc true)))

      (undocumented
        (route/not-found (respond "Took a wrong turn?"))))))
