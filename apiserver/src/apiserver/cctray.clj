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

(ns apiserver.cctray
  (:require [clojure.data.xml :as xml]
            [crux.api :as crux]
            [failjure.core :as f]))

(defn make-project
  [{:keys [group name status completed]
    :as   data}]
  (let [last-build-status (case status
                            (:passed :running :paused) "Success"
                            :failed                    "Failure"
                            :stopped                   "Exception"
                            "Unknown")
        last-build-label  (-> data
                              :crux.db/id
                              clojure.core/name)]
    [[:name
      (format "%s:%s"
              group
              name)]
     [:activity
      (if (= status :running)
        "Running"
        "Sleeping")]
     [:lastBuildStatus last-build-status]
     [:lastBuildLabel last-build-label]
     [:lastBuildTime completed]
     [:webUrl "#"]]))

(defn generate-report
  [db]
  (f/try-all [statuses (crux/q (crux/db db)
                               '{:find  [(pull run [:group :name :status :completed :crux.db/id])]
                                 :where [[pipeline :type :pipeline]
                                         [pipeline :group group]
                                         [pipeline :name name]
                                         [run :type :pipeline-run]
                                         [run :group group]
                                         [run :name name]]})]
    (-> [:Projects (map make-project statuses)]
        xml/sexp-as-element
        xml/emit-str)
    (f/when-failed [err]
      err)))
