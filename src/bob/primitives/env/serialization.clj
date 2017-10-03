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

(ns bob.primitives.env.serialization
  (:require [cheshire.core :refer [generate-string parse-string]]
            [bob.primitives.env.env :refer [map->Env]]))
; TODO 1: Find a better way if possible, or Spec it.
(defn env-to-json [env]
  (generate-string env))

(defn json-to-env [json]
  (-> json
      (parse-string true)
      (map->Env)))
