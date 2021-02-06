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

(ns apiserver_next.server-test
  (:require [clojure.test :refer [deftest testing is]]
            [apiserver_next.server :as server])
  (:import [java.util Map LinkedHashMap]
           [io.swagger.v3.oas.models Operation PathItem]
           [io.swagger.v3.oas.models.media Content StringSchema IntegerSchema ObjectSchema ArraySchema MediaType]
           [io.swagger.v3.oas.models.parameters Parameter PathParameter QueryParameter RequestBody]))

(deftest map-to-malli-spec
  (testing "surrounding values of a clojure map to a malli map spec"
    (is (= {:path [:map [:x string?] [:y int?]]}
           (server/wrap-map :path {:path [[:x string?] [:y int?]]}))))
  (testing "surround ignores non matching key"
    (is (= {:query [:map [:x string?]]}
           (server/wrap-map :path {:query [:map [:x string?]]})))))

(deftest openapi-properties-to-malli-spec
  (testing "convert a required OpenAPI Map entry"
    (let [property (Map/entry "id" (StringSchema.))]
      (is (= [:id string?]
             (server/->prop-schema #{"id" "x"} property)))))
  (testing "convert an optional OpenAPI Map entry"
    (let [property (Map/entry "id" (StringSchema.))]
      (is (= [:id {:optional true} string?]
             (server/->prop-schema #{"x"} property))))))

(deftest openapi-parameters-to-malli-spec
  (testing "convert a required OpenAPI Parameter"
    (let [param (doto (Parameter.)
                  (.setName "x")
                  (.setRequired true)
                  (.setSchema (StringSchema.)))]
      (is (= [:x string?]
             (server/->param-schema param)))))
  (testing "convert an optional OpenAPI Map entry"
    (let [param (doto (Parameter.)
                  (.setName "x")
                  (.setSchema (StringSchema.)))]
      (is (= [:x {:optional true} string?]
             (server/->param-schema param))))))

(deftest openapi-schema-to-malli-spec
  (testing "string"
    (is (= string?
           (server/spec (StringSchema.)))))
  (testing "integer"
    (is (= int?
           (server/spec (IntegerSchema.)))))
  (testing "empty object"
    (is (= [:map {:closed false}]
           (server/spec (ObjectSchema.)))))
  (testing "object"
    (let [props (doto (LinkedHashMap.)
                  (.put "x" (IntegerSchema.))
                  (.put "y" (StringSchema.)))
          obj   (doto (ObjectSchema.)
                  (.setRequired ["y" "x"])
                  (.setProperties props))]
      (is (= [:map {:closed false} [:x int?] [:y string?]]
             (server/spec obj)))))
  (testing "empty array"
    (is (= [:sequential any?]
           (server/spec (ArraySchema.)))))
  (testing "array"
    (let [arr (doto (ArraySchema.)
                (.setItems (StringSchema.)))]
      (is (= [:sequential string?]
             (server/spec arr))))))

(deftest parameters-to-malli-spec
  (testing "path"
    (let [param (doto (PathParameter.)
                  (.setName "x")
                  (.setSchema (IntegerSchema.)))]
      (is (= {:path [[:x int?]]}
             (server/param->data param)))))
  (testing "query"
    (let [param (doto (QueryParameter.)
                  (.setName "x")
                  (.setRequired true)
                  (.setSchema (IntegerSchema.)))]
      (is (= {:query [[:x int?]]}
             (server/param->data param)))))
  (testing "request body"
    (let [media   (doto (MediaType.)
                    (.setSchema (ObjectSchema.)))
          content (doto (Content.)
                    (.put "application/json" media))
          param   (doto (RequestBody.)
                    (.setContent content))]
      (is (= {:body [:map {:closed false}]}
             (server/param->data param))))))

(deftest openapi-operation-to-malli-spec
  (testing "OpenAPI operation to reitit ring handler"
    (let [param     (doto (PathParameter.)
                      (.setName "x")
                      (.setSchema (IntegerSchema.)))
          operation (doto (Operation.)
                      (.setParameters [param])
                      (.setOperationId "TestOp"))
          handlers  {"TestOp" "a handler"}]
      (is (= {:handler    "a handler"
              :parameters {:path [:map [:x int?]]}}
             (server/operation->data operation handlers))))))

(deftest openapi-path-to-malli-spec
  (testing "OpenAPI path to reitit route"
    (let [param     (doto (PathParameter.)
                      (.setName "x")
                      (.setSchema (IntegerSchema.)))
          operation (doto (Operation.)
                      (.setParameters [param])
                      (.setOperationId "TestOp"))
          handlers  {"TestOp" "a handler"}
          path-item (doto (PathItem.)
                      (.setGet operation))]
      (is (= {:get {:handler    "a handler"
                    :parameters {:path [:map [:x int?]]}}}
             (server/path-item->data path-item handlers))))))
