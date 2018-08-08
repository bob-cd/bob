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

(ns bob.main
  (:require [aleph.http :as http]
            [bob.routes :refer [bob-api]]
            [bob.db.core :refer [init-db]])
  (:gen-class))

(def PORT 7777)

(defn -main
  "Defines the entry point of Bob.
  Starts up the HTTP API on port *7777* by default."
  [& _]
  (do (init-db)
      (println (format "Bob's listening on http://0.0.0.0:%d/" PORT))
      (http/start-server bob-api {:port PORT})))
