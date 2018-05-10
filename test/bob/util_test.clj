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

(ns bob.util-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.spec.alpha :as s]
            [bob.util :refer :all]))

(defspec respond-returns-a-ring-response
         100
         (prop/for-all [msg gen/string]
                       (= (respond msg) {:body    {:message msg}
                                         :headers {}
                                         :status  200})))

(s/def ::container-id
  (s/and string?
         #(> (count %) id-length)))

(defspec format-id-formats-given-id
         100
         (prop/for-all [msg (s/gen ::container-id)]
                       (<= (count (format-id msg)) id-length)))
