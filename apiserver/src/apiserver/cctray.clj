; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.cctray
  (:require
   [clojure.data.xml :as xml]
   [failjure.core :as f]
   [xtdb.api :as xt]))

(defn make-project
  [{:keys [group name status completed]
    :as data}]
  (let [last-build-status (case status
                            (:passed :running) "Success"
                            :failed "Failure"
                            :stopped "Exception"
                            "Unknown")
        last-build-label (-> data
                             :xt/id
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
  (f/try-all [statuses (xt/q (xt/db db)
                             '{:find [(pull run [:group :name :status :completed :xt/id])]
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
