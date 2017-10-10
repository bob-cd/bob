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

(ns bob.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as neg]
            [cheshire.core :refer [generate-string]]))

(def supported-types ["application/json"])

(def content-neg (neg/negotiate-content supported-types))

(defn accepted-type [context]
  (get-in context [:request :accept :field] "application/json"))

(defn transform-content
  [body content-type]
  (case content-type
    "application/json" (generate-string body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name  ::coerce-body
   :leave (fn [context]
            (cond-> context
                    (nil? (get-in context [:response :body :headers "Content-Type"]))
                    (update-in [:response] coerce-to (accepted-type context))))})

(defn status [request]
  {:status 200 :body {:status "Ok"}})

(def routes
  (route/expand-routes
    #{["/status" :get [coerce-body content-neg status] :route-name :status]}))

(def service {:env          :prod
              ::http/routes routes
              ::http/type   :immutant
              ::http/port   7777})
