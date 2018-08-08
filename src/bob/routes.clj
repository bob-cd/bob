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

(ns bob.routes
  (:require [clojure.string :refer [split]]
            [ring.util.http-response :refer [ok not-found]]
            [compojure.route :as route]
            [compojure.api.sweet :refer [api context GET undocumented]]
            [schema.core :as s]
            [bob.execution.core :refer [gc]]
            [bob.middleware :refer [ignore-trailing-slash]]
            [bob.util :refer [respond]]))

(def bob-api
  (ignore-trailing-slash
    (api
      {:swagger
       {:ui   "/"
        :spec "/swagger.json"
        :data {:info     {:title       "Bob the Builder"
                          :description "Can we build it? \uD83D\uDD28"}
               :consumes ["application/json"]
               :produces ["application/json"]}}}

      (context "/api" []
        :tags ["Bob's API"]

        (GET "/gc" []
          :return s/Str
          :summary "Runs the garbage collection for Bob, reclaiming resources."
          (gc))

        (GET "/gc/all" []
          :return s/Str
          :summary "Runs the full garbage collection for Bob, reclaiming all resources."
          (gc true)))

      (undocumented
        (route/not-found (ok {:message "Took a wrong turn?"}))))))
