; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.dispatch
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [failjure.core :as f]
   [langohr.basic :as lb]))

(defn queue-msg-subscriber
  [config routes ch meta ^bytes payload]
  (f/try-all [msg (json/read-str (String/new payload "UTF-8") :key-fn keyword)]
    (do
      (log/infof "payload %s, meta: %s" msg meta)
      (if-let [routed-fn (some-> meta :type routes)]
        (routed-fn config ch msg meta)
        (do
          (log/error (str "Could not route message: " msg))
          (lb/nack ch (:delivery-tag meta) false false))))
    (f/when-failed [err]
      (log/errorf "Could not parse '%s': %s" (String/new payload "UTF-8") (f/message err))
      (lb/nack ch (:delivery-tag meta) false false))))

(comment
  (set! *warn-on-reflection* true))
