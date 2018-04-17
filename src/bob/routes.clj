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
            [bob.execution.core :refer [start logs-of stop]]
            [bob.middleware :refer [ignore-trailing-slash]]
            [bob.util :refer [m]])
  (:import (org.apache.tools.ant.types Commandline)))

(defn status
  [_]
  (m "Bob's here!"))

(defroutes routes
  (GET "/" [] status)
  (GET "/status" [] status)
  ;; TODO: Parse the command with something else/lighter?
  (POST "/start" [& args] (start (seq (Commandline/translateCommandline (args "cmd")))
                                 (args "img")))
  (GET "/read/:id/:count" [id count] (logs-of id (Integer/parseInt count)))
  (GET "/stop/:id" [id] (stop id))
  (route/not-found (m "Took a wrong turn?")))

(def bob-routes
  (-> routes
      (ignore-trailing-slash)
      (wrap-json-response)
      (params/wrap-params)))
