; Copyright 2018-2021 Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

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
            [reitit.http.interceptors.exception :as exception]
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
                         :interceptors [(exception/exception-interceptor)
                                        (parameters/parameters-interceptor)
                                        (muuntaja/format-negotiate-interceptor)
                                        (muuntaja/format-response-interceptor)
                                        (muuntaja/format-request-interceptor)
                                        (coercion/coerce-exceptions-interceptor)
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
