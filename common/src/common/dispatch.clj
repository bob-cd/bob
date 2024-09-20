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
  [config routes chan meta-data ^byte/1 payload]
  (let [msg (f/try* (json/read-str (String/new payload "UTF-8") :key-fn keyword))]
    (if (f/failed? msg)
      (err/publish-error chan (format "Could not parse '%s' as json" (String/new payload "UTF-8")))
      (do
        (log/infof "payload %s, meta: %s"
                   msg
                   meta-data)
        (if-let [routed-fn (some-> meta-data
                                   :type
                                   routes)]
          (routed-fn config chan msg)
          (err/publish-error chan (format "Could not route message: %s" msg)))))))

(comment
  (set! *warn-on-reflection* true))
