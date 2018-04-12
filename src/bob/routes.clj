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
  (:require [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :as params]
            [compojure.core :as compojure]
            [compojure.route :as route]
            [compojure.core :as compojure :refer [GET]]
            [bob.execution.core :refer [start logs-of stop]]
            [bob.middleware :refer [ignore-trailing-slash]]
            [bob.util :refer [m]]))

(defn status
  [_]
  (m "Bob's here!"))

(def routes
  (-> (compojure/routes
        (GET "/" [] status)
        (GET "/status" [] status)
        (GET "/start" [] start)
        (GET "/read/:id/:count" [id count] (logs-of id (Integer/parseInt count)))
        (GET "/stop/:id" [id] (stop id))
        (route/not-found (m "Took a wrong turn?")))
      (ignore-trailing-slash)
      (wrap-json-response)
      (params/wrap-params)))
