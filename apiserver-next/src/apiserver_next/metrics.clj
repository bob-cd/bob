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

(ns apiserver_next.metrics
  (:require [iapetos.core :as prometheus]
            [iapetos.export :as export]
            [failjure.core :as f]
            [crux.api :as crux]
            [langohr.queue :as lq]))

(defonce registry
         (-> (prometheus/collector-registry)
             (prometheus/register
               (prometheus/gauge :bob/queued-entities {:description "Number of queued entity changes to be applied"})
               (prometheus/gauge :bob/queued-jobs {:description "Number of queued jobs to be picked up"})
               (prometheus/gauge :bob/errors {:description "Number of errors"})
               (prometheus/gauge :bob/running-jobs {:description "Number of jobs currently running"})
               (prometheus/gauge :bob/failed-jobs {:description "Number of failed jobs"})
               (prometheus/gauge :bob/passed-jobs {:description "Number of passed jobs"})
               (prometheus/gauge :bob/paused-jobs {:description "Number of paused jobs"})
               (prometheus/gauge :bob/stopped-jobs {:description "Number of stopped jobs"}))))

(defn count-statuses
  [db]
  (f/try-all [statuses [:running :passed :failed :paused :stopped]
              counts   (pmap (fn [status]
                               {:status status
                                :count  (count (crux/q (crux/db db)
                                                       `{:find  [run]
                                                         :where [[run :type :pipeline-run]
                                                                 [run :status ~status]]}))})
                             statuses)]
    (doseq [{:keys [status count]} counts]
      (case status
        :running (prometheus/set (registry :bob/running-jobs) count)
        :passed  (prometheus/set (registry :bob/passed-jobs) count)
        :failed  (prometheus/set (registry :bob/failed-jobs) count)
        :paused  (prometheus/set (registry :bob/paused-jobs) count)
        :stopped (prometheus/set (registry :bob/stopped-jobs) count)))
    (f/when-failed [err]
      err)))

(defn collect-metrics
  [queue db]
  (f/try-all [_ (->> "bob.jobs"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/queued-jobs)))
              _ (->> "bob.entities"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/queued-entities)))
              _ (->> "bob.errors"
                     (lq/message-count queue)
                     (prometheus/set (registry :bob/errors)))
              _ (count-statuses db)]
    (export/text-format registry)
    (f/when-failed [err]
      err)))
