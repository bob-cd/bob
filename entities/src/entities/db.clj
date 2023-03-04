; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns entities.db
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.tools.logging :as log]
   [common.errors :as err]
   [common.schemas]
   [failjure.core :as f]
   [xtdb.api :as xt]))

(defn validate-and-transact
  [db-client queue-chan schema data txn entity]
  (f/try-all [_ (when-not (spec/valid? schema data)
                  (throw (IllegalArgumentException. (str "Invalid artifact-store: " data))))
              _ (xt/submit-tx db-client txn)]
    "Ok"
    (f/when-failed [err]
      (let [msg (str entity " register/un-register failure: " (f/message err))]
        (log/errorf msg)
        (when queue-chan
          (err/publish-error queue-chan msg))))))
