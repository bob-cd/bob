; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.server
  (:require
   [apiserver.handlers :as h]
   [apiserver.healthcheck :as hc]
   [clojure.java.io :as io]
   [common.heartbeat :as hb]
   [muuntaja.core :as m]
   [navi.core :as navi]
   [reitit.coercion.malli :as malli]
   [reitit.http :as http]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.exception :as exception]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.http.interceptors.parameters :as parameters]
   [reitit.interceptor.sieppari :as sieppari]
   [reitit.ring :as ring])
  (:import
   [java.util.concurrent Executors]))

(defn system-interceptor
  [db queue queue-conn-opts stream-env]
  {:enter #(-> %
               (assoc-in [:request :db] db)
               (assoc-in [:request :queue] queue)
               (assoc-in [:request :queue-conn-opts] queue-conn-opts)
               (assoc-in [:request :stream] stream-env))})

(defn server
  [database queue queue-conn-opts health-check-freq stream]
  (hb/schedule #(hc/check (Executors/newVirtualThreadPerTaskExecutor) {:queue queue :db database})
               "healthcheck"
               health-check-freq)
  (hb/schedule #(hb/beat-it database queue-conn-opts :bob/node-type :apiserver)
               "heartbeat"
               health-check-freq)
  (http/ring-handler
   (http/router (-> "bob/api.yaml"
                    io/resource
                    slurp
                    (navi/routes-from h/handlers))
                {:data {:coercion malli/coercion
                        :muuntaja m/instance
                        :interceptors [(parameters/parameters-interceptor)
                                       (muuntaja/format-negotiate-interceptor)
                                       (muuntaja/format-response-interceptor)
                                       (exception/exception-interceptor)
                                       (muuntaja/format-request-interceptor)
                                       (coercion/coerce-exceptions-interceptor)
                                       (coercion/coerce-response-interceptor)
                                       (coercion/coerce-request-interceptor)
                                       (system-interceptor database queue queue-conn-opts stream)]}})
   (ring/routes
    (ring/create-default-handler
     {:not-found (constantly {:status 404
                              :headers {"Content-Type" "application/json"}
                              :body "{\"message\": \"Took a wrong turn?\"}"})}))
   {:executor sieppari/executor}))
