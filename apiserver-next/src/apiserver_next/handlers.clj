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

(ns apiserver_next.handlers
  (:require [clojure.java.io :as io]))

(defn respond
  ([content]
   (respond content 200))
  ([content status]
   {:status  status
    :headers {"Content-Type" "application/json"}
    :body    content}))

(defn api-spec
  [_]
  {:status  200
   :headers {"Content-Type" "application/yaml"}
   :body    (-> "bob/api.yaml"
                io/resource
                io/input-stream)})

(def handlers
  {"GetApiSpec" api-spec})
