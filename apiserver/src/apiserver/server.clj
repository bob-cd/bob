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

;; TODO: Throw all of this away when https://github.com/juxt/apex can be used.
(ns apiserver.server
  (:require [clojure.java.io :as io]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.coercion.malli :as malli]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.interceptor.sieppari :as sieppari]
            [navi.core :as navi]
            [apiserver.handlers :as h]
            [apiserver.healthcheck :as hc]))

(defn system-interceptor
  [db queue]
  {:enter #(-> %
               (update-in [:request :db] (constantly db))
               (update-in [:request :queue] (constantly queue)))})


(defn server
  [database queue health-check-freq]
  (hc/schedule queue database health-check-freq)
  (http/ring-handler
    (http/router (-> "bob/api.yaml"
                     io/resource
                     slurp
                     (navi/routes-from h/handlers))
                 {:data {:coercion     malli/coercion
                         :muuntaja     m/instance
                         :interceptors [(parameters/parameters-interceptor)
                                        (muuntaja/format-negotiate-interceptor)
                                        (muuntaja/format-response-interceptor)
                                        (muuntaja/format-request-interceptor)
                                        (coercion/coerce-response-interceptor)
                                        (coercion/coerce-request-interceptor)
                                        (system-interceptor database queue)]}})
    (ring/routes
      (ring/create-default-handler
        {:not-found (constantly {:status  404
                                 :headers {"Content-Type" "application/json"}
                                 :body    "{\"message\": \"Took a wrong turn?\"}"})}))
    {:executor sieppari/executor}))

(comment
  (clojure.pprint/pprint (-> "bob/api.yaml"
                             io/resource
                             slurp
                             (navi/routes-from h/handlers))))
