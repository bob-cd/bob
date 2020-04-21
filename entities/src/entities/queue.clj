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

(ns entities.queue
  (:require [langohr.consumers :as lc]
            [langohr.queue :as lq]
            [taoensso.timbre :as log]))

(defn subscribe
  [ch queue]
  (letfn [(handler [_ch meta-data ^bytes payload]
                   (log/infof "payload %s" (String. payload "UTF-8"))
                   (log/infof "meta %s" meta-data))]
    (lq/declare ch
                queue
                {:exclusive   false
                 :auto-delete false})
    (lc/subscribe ch queue handler {:auto-ack true})))
