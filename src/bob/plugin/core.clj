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

(ns bob.plugin.core
  (:require [manifold.deferred :as d]
            [ring.util.http-response :as resp]
            [failjure.core :as f]
            [korma.core :as k]
            [bob.db.core :as db]
            [bob.util :as util]))

(defn register
  "Registers a plugin with an unique name and an URL."
  [name url]
  (d/let-flow [result (util/unsafe! (k/insert db/plugins
                                              (k/values {:name name
                                                         :url  url})))]
    (if (f/failed? result)
      (resp/conflict "Plugin already exists")
      (util/respond "Ok"))))

(defn un-register
  "Unregisters a plugin by its name."
  [name]
  (d/let-flow [_ (util/unsafe! (k/delete db/plugins
                                         (k/where {:name name})))]
    (util/respond "Ok")))

(defn all-plugins
  "Lists all plugins by name."
  []
  (d/let-flow [plugins (util/unsafe! (k/select db/plugins
                                               (k/fields :name)))]
    (util/respond
      (if (f/failed? plugins)
        []
        (map #(:name %) plugins)))))

(defn add-params
  "Saves the map of GET params to be sent to the plugin."
  [name params pipeline]
  (when (not (empty? params))
    (k/insert db/plugin-params
              (k/values (map #(hash-map :key (clojure.core/name (first %))
                                        :value (last %)
                                        :plugin name
                                        :pipeline pipeline)
                             params)))))

(defn url-of
  "Generates a GET URL for the plugin."
  [name pipeline]
  (let [url    (-> (k/select db/plugins
                             (k/where {:name name})
                             (k/fields :url))
                   (first)
                   (:url))
        params (k/select db/plugin-params
                         (k/where {:plugin   name
                                   :pipeline pipeline})
                         (k/fields :key :value))]
    (format "%s/bob_request?%s"
            url
            (clojure.string/join
              "&"
              (map #(format "%s=%s" (:key %) (:value %))
                   params)))))
