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

(ns bob.primitives.env.persistence
  (:require [korma.core :refer :all]
            [bob.primitives.env.env :refer [map->Env]]
            [bob.storage.db :refer [migration-config]])
  (:import (bob.primitives.env.env Env)))

(declare env envvars)

(defentity env
           (table :ENV)
           (has-many envvars))

(defentity envvars
           (table :ENVVARS))

(defn put-env [e]
  (let [id (.id e)
        vars (.vars e)]
    (do (delete env (where {:ID [= id]}))
        (insert env (values {:ID id}))
        (doseq [key (keys vars)]
          (insert envvars (values {:ID    id
                                   :KEY   (name key)
                                   :VALUE (key vars)}))))))

(defn- get-vars [id]
  (->> (select envvars (where {:ID [= id]}))
       (map #(hash-map (keyword (:KEY %)) (:VALUE %)))
       (into (hash-map))))

(defn get-env [id]
  (let [result (select env (where {:ID [= id]}))]
    (if (empty? result)
      nil
      (Env. id (get-vars id)))))

(defn del-env [id]
  (delete env (where {:ID [= id]})))
