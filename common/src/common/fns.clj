; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.fns
  (:require
   [clojure.spec.alpha :as spec]
   [common.schemas]
   [failjure.core :as f]
   [xtdb.api :as xt]))

(defn get-logger
  [db group name]
  (f/try-all [logger (->> {:find ['(pull logger [*])]
                           :where [['pipeline :type :pipeline]
                                   ['pipeline :group group]
                                   ['pipeline :name name]
                                   ['pipeline :logger 'logger-name]
                                   ['logger :type :logger]
                                   ['logger :name 'logger-name]]}
                          (xt/q (xt/db db))
                          (ffirst))
              _ (when-not logger
                  (f/fail "Cannot locate logger for pipeline"))
              _ (when-not (spec/valid? :bob.db/logger logger)
                  (f/fail (str "Invalid logger: " logger)))]
    logger
    (f/when-failed [err] err)))
