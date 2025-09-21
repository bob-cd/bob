; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns apiserver.cctray
  (:require
   [clojure.data.xml :as xml]
   [common.store :as store]
   [failjure.core :as f]))

(defn make-project
  [{:keys [group name status completed id]}]
  (let [last-build-status (case status
                            (:passed :running) "Success"
                            :failed "Failure"
                            :stopped "Exception"
                            "Unknown")]
    [[:name (format "%s:%s" group name)]
     [:activity (if (= status :running) "Running" "Sleeping")]
     [:lastBuildStatus last-build-status]
     [:lastBuildLabel id]
     [:lastBuildTime completed]
     [:webUrl "#"]]))

(defn generate-report
  [db]
  (f/try-all [projects (->> (store/get db "bob.pipeline.run/" {:prefix true})
                            (map :value)
                            (mapcat make-project))]
    (-> [:Projects projects]
        xml/sexp-as-element
        xml/emit-str)
    (f/when-failed [err] err)))
