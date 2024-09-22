; Copyright 2018- Rahul De
;
; Use of this source code is governed by an MIT-style
; license that can be found in the LICENSE file or at
; https://opensource.org/licenses/MIT.

(ns common.dispatch
  (:require
   [clojure.data.json :as json]
   [clojure.tools.logging :as log]
   [common.errors :as err]
   [failjure.core :as f]))

(defn queue-msg-subscriber
  [config routes ch meta ^bytes payload]
  (f/try-all [msg (json/read-str (String/new payload "UTF-8") :key-fn keyword)]
    (do
      (log/infof "payload %s, meta: %s" msg meta)
      (if-let [routed-fn (some-> meta :type routes)]
        (routed-fn config ch msg meta)
        (err/publish-error ch (str "Could not route message: " msg))))
    (f/when-failed [err]
      (err/publish-error ch (format "Could not parse '%s': %s" (String/new payload "UTF-8") (f/message err))))))

(comment
  (set! *warn-on-reflection* true))
