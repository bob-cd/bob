; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.errors
  (:require
   [clojure.data.json :as json]
   [langohr.basic :as lb]
   [taoensso.timbre :as log]))

(defn publish-error
  [chan message]
  (log/error message)
  (lb/publish chan
              "" ; Default exchange
              "bob.errors"
              (json/write-str {:message message} :key-fn #(subs (str %) 1))
              {:content-type "application/json"
               :type "message/error"}))
