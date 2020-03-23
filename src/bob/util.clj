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

(ns bob.util
  (:require [ring.util.http-response :as res]
            [taoensso.timbre :as log]
            [failjure.core :as f]
            [bob.states :as states]
            [bob.pipeline.db :as db])
  (:import (java.util UUID)))

(def id-length 12)

(defn respond
  "Simple decorator for wrapping a message in the response format."
  [msg]
  (res/ok {:message msg}))

(defn service-unavailable
  "Decorator for returning code 503 Service Unavailable."
  [error]
  (res/service-unavailable {:message error}))

(defn format-id
  "Return docker container ids in the standard length"
  [^String id]
  (subs id 0 id-length))

(defn get-id [] (str (UUID/randomUUID)))

(defn name-of
  [group name]
  (format "%s:%s" group name))

(defn log-to-db
  [data run-id]
  (db/upsert-log states/db {:run     run-id
                            :content data}))

(defn log-and-fail
  [& strings]
  (let [errormessage (clojure.string/join " " strings)]
    (do (log/errorf errormessage)
        (f/fail errormessage))))

;; TODO: Optimize as mentioned in:
;; https://www.reddit.com/r/Clojure/comments/8zurv4/critical_code_review_and_feedback/
(defn sh-tokenize!
  "Tokenizes a shell command given as a string into the command and its args.
  Either returns a list of tokens or throws an IllegalStateException.
  Sample input: sh -c 'while sleep 1; do echo \\\"${RANDOM}\\\"; done'
  Output: [sh, -c, while sleep 1; do echo \"${RANDOM}\"; done]"
  [^String command]
  (let [[escaped?
         current-arg
         args
         state] (loop [cmd         command
                       escaped?    false
                       state       :no-token
                       current-arg ""
                       args        []]
                  (if (or (nil? cmd)
                          (zero? (count cmd)))
                    [escaped? current-arg args state]
                    (let [char ^Character (first cmd)]
                      (if escaped?
                        (recur (rest cmd) false state (str current-arg char) args)
                        (case state
                          :single-quote (if (= char \')
                                          (recur (rest cmd) escaped? :normal current-arg args)
                                          (recur (rest cmd) escaped? state (str current-arg char) args))
                          :double-quote (case char
                                          \" (recur (rest cmd) escaped? :normal current-arg args)
                                          \\ (let [next (second cmd)]
                                               (if (or (= next \")
                                                       (= next \\))
                                                 (recur (drop 2 cmd) escaped? state (str current-arg next) args)
                                                 (recur (drop 2 cmd) escaped? state (str current-arg char next) args)))
                                          (recur (rest cmd) escaped? state (str current-arg char) args))
                          (:no-token :normal) (case char
                                                \\ (recur (rest cmd) true :normal current-arg args)
                                                \' (recur (rest cmd) escaped? :single-quote current-arg args)
                                                \" (recur (rest cmd) escaped? :double-quote current-arg args)
                                                (if-not (Character/isWhitespace char)
                                                  (recur (rest cmd) escaped? :normal (str current-arg char) args)
                                                  (if (= state :normal)
                                                    (recur (rest cmd) escaped? :no-token "" (conj args current-arg))
                                                    (recur (rest cmd) escaped? state current-arg args))))
                          (throw (IllegalStateException.
                                  (format "Invalid shell command: %s, unexpected token %s found." command state))))))))]
    (if escaped?
      (conj args (str current-arg \\))
      (if (not= state :no-token)
        (conj args current-arg)
        args))))

(defn format-env-vars
  [env-vars]
  (map #(format "%s=%s" (name (first %)) (last %))
       env-vars))

(comment
  (log-and-fail "foo" :bar)

  (db/logs-of states/db {:run-id "1"}))
