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

(ns apiserver_next.server
  (:require [clojure.java.io :as io]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.coercion.malli :as malli]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.interceptor.sieppari :as sieppari]
            [apiserver_next.handlers :as h])
  (:import [java.util Map$Entry]
           [io.swagger.v3.oas.models.media StringSchema IntegerSchema ObjectSchema ArraySchema MediaType]
           [io.swagger.v3.oas.models.parameters PathParameter QueryParameter RequestBody Parameter]
           [io.swagger.v3.oas.models Operation PathItem]
           [io.swagger.v3.parser OpenAPIV3Parser]
           [io.swagger.v3.parser.core.models ParseOptions]))

(declare spec)

;; TODO: 🤮 -> 🤢
(defn wrap-map
  "Surrounds the key in a map for malli conformance"
  [k m]
  (if (contains? m k)
    (update-in m
               [k]
               #(into [:map] %))
    m))

;; TODO: 🤮 -> 🤢
(defn ->prop-schema
  "Given a property and a required keys set, returns a malli spec.

  Intended for RequestBody"
  [required ^Map$Entry property]
  (let [k          (.getKey property)
        key-schema [(keyword k)]
        key-schema (if (contains? required k)
                     key-schema
                     (conj key-schema {:optional true}))]
    (conj key-schema (spec (.getValue property)))))

(defn ->param-schema
  "Given a param applies the similar logic as prop to schema

  Intended for Parameter"
  [^Parameter param]
  (let [key-spec [(-> param
                      .getName
                      keyword)]
        key-spec (if (.getRequired param)
                   key-spec
                   (conj key-spec {:optional true}))]
    (conj key-spec
          (-> param
              .getSchema
              spec))))

(defmulti spec class)

(defmethod spec
  StringSchema
  [_]
  string?)

(defmethod spec
  IntegerSchema
  [_]
  int?)

(defmethod spec
  ObjectSchema
  [^ObjectSchema schema]
  (let [required (->> schema
                      .getRequired
                      (into #{}))
        schemas  (->> schema
                      .getProperties
                      (map #(->prop-schema required %))
                      (into []))]
    (into [:map {:closed false}] schemas)))

(defmethod spec
  ArraySchema
  [^ArraySchema schema]
  (let [items-schema (-> schema
                         .getItems
                         spec)]
    (if (seq items-schema)
      [:sequential items-schema]
      [:sequential any?])))

(defmulti param->data class)

;; TODO: 🤮🤮 -> 🤢 The extra [] is there to help with merge-with into
(defmethod param->data
  PathParameter
  [param]
  {:path [(->param-schema param)]})

(defmethod param->data
  QueryParameter
  [param]
  {:query [(->param-schema param)]})

(defmethod param->data
  RequestBody
  [^RequestBody param]
  (let [^MediaType content (-> param
                               .getContent
                               .values
                               .stream
                               .findFirst
                               .get)]
    {:body (-> content
               .getSchema
               spec)}))

(defn operation->data
  [^Operation op]
  (let [params       (into [] (.getParameters op))
        request-body (.getRequestBody op)
        params       (if (nil? request-body)
                       params
                       (conj params request-body))
        schemas      (->> params
                          (map param->data)
                          (apply merge-with into)
                          (wrap-map :path)
                          (wrap-map :query))
        handler      {:handler (get h/handlers (.getOperationId op))}]
    (if (seq schemas)
      (assoc handler :parameters schemas)
      handler)))

(defn path-item->data
  [^PathItem path-item]
  (->> path-item
       .readOperationsMap
       (map #(vector (-> ^Map$Entry %
                         .getKey
                         .toString
                         .toLowerCase
                         keyword)
                     (-> ^Map$Entry %
                         .getValue
                         operation->data)))
       (into {})))

(defn ->routes
  [^String api-spec]
  (let [parse-options (doto (ParseOptions.)
                        (.setResolveFully true))]
    (->> (.readContents (OpenAPIV3Parser.) api-spec nil parse-options)
         .getOpenAPI
         .getPaths
         (mapv #(vector (.getKey ^Map$Entry %)
                        (-> ^Map$Entry %
                            .getValue
                            path-item->data))))))

(defn system-interceptor
  [db queue]
  {:enter #(-> %
               (update-in [:request :db] (constantly db))
               (update-in [:request :queue] (constantly queue)))})

(defn server
  [database queue]
  (http/ring-handler
    (http/router (-> "bob/api.yaml"
                     io/resource
                     slurp
                     ->routes)
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
  (set! *warn-on-reflection* true)

  (require '[clojure.pprint :as pp])
  (pp/pprint (-> "bob/api.yaml"
                 io/resource
                 slurp
                 ->routes)))
