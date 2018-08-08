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
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :as params]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [bob.execution.core :refer [gc]]
            [bob.middleware :refer [ignore-trailing-slash]]
            [bob.util :refer [respond]]))

(def status (respond "\uD83D\uDD28 Bob's here! \uD83D\uDD28"))

(defroutes routes
  (GET "/" [] status)
  (GET "/gc" [] (gc))
  (GET "/gc/all" [] (gc true))
  (route/not-found (respond "Took a wrong turn?")))

(def bob-routes
  (-> routes
      (ignore-trailing-slash)
      (wrap-json-response)
      (params/wrap-params)))
