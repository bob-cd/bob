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

(ns bob.api.schemas
  (:require [schema.core :as s])
  (:import (clojure.lang Keyword)))

(s/defschema SimpleResponse {:message String})

(s/defschema Resource {:name     String
                       :params   {Keyword String}
                       :type     String
                       :provider String})

(s/defschema Step {(s/required-key :cmd)            String
                   (s/optional-key :needs_resource) String})

(s/defschema Pipeline {:steps     [Step]
                       :image     String
                       :vars      {Keyword String}
                       :artifacts {Keyword String}
                       :resources [Resource]})

(s/defschema LogsResponse {:message [String]})

(s/defschema StatusResponse {:message (s/enum :running :passed :failed)})

(s/defschema RunningResponse {:message [{:group String
                                         :name  String}]})

(s/defschema ResourceAttributes {:url String})

(s/defschema ResourceResponse {:message [String]})
