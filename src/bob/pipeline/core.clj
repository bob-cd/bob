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

(ns bob.pipeline.core
  (:require [korma.db :refer [defdb]]
            [korma.core :refer [defentity table has-many insert values where select fields]]
            [manifold.deferred :refer [let-flow]]
            [failjure.core :as f]
            [bob.execution.internals :refer [default-image]]
            [bob.pipeline.internals :refer [exec-steps]]
            [bob.db.core :refer [db-spec]]
            [bob.util :refer [respond perform!]])
  (:import (bob.java ShellCmd)))

(defdb _ db-spec)

(defentity steps
  (table :STEPS))

(defentity pipelines
  (table :PIPELINES)
  (has-many steps))

(def name-of (memoize #(str %1 ":" %2)))

(defn create
  ([group name pipeline-steps] (create group name pipeline-steps default-image))
  ([group name pipeline-steps image]
   (let-flow [result (f/attempt-all [_ (perform! (insert pipelines (values {:NAME  (name-of group name)
                                                                            :IMAGE image})))
                                     _ (perform! (doseq [step pipeline-steps]
                                                   (insert steps (values {:CMD      step
                                                                          :PIPELINE (name-of group name)}))))]
                       "Ok"
                       (f/when-failed [err] (f/message err)))]
     (respond result))))

(defn start
  [group name]
  (let-flow [result (f/attempt-all [steps (perform! (select steps (where {:PIPELINE (name-of group name)})))
                                    steps (map (fn [step] {:cmd (ShellCmd/tokenize (:CMD step) false)
                                                           :id  (:ID step)}) steps)
                                    image (perform! (-> (select pipelines
                                                                (fields [:IMAGE])
                                                                (where {:NAME (name-of group name)}))
                                                        (first)
                                                        (:IMAGE)))]
                      (exec-steps image steps)
                      (f/when-failed [err] (f/message err)))]
    (respond result)))
