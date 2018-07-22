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

(ns bob.util
  (:require [ring.util.response :refer [response]])
  (:import (java.sql Clob)))

(def id-length 12)

(defmacro unsafe!
  [& body]
  `(try
     ~@body
     (catch Exception e# e#)))

(defn respond
  [msg]
  (response {:message msg}))

(defn format-id
  [^String id]
  (subs id 0 id-length))

(defn clob->str
  [^Clob clob]
  (.getSubString clob 1 (int (.length clob))))

;; TODO: Optimize as mentioned in:
;; https://www.reddit.com/r/Clojure/comments/8zurv4/critical_code_review_and_feedback/
(defn sh-tokenize
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
                          :single-quote (if (= char \\)
                                          (recur (rest cmd) escaped? :normal current-arg args)
                                          (recur (rest cmd) escaped? state (str current-arg char) args))
                          :double-quote (case char
                                          \" (recur cmd escaped? :normal current-arg args)
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
                                                (if (not (Character/isWhitespace char))
                                                  (recur (rest cmd) escaped? :normal (str current-arg char) args)
                                                  (if (= state :normal)
                                                    (recur (rest cmd) escaped? :no-token "" (conj args current-arg))
                                                    (recur (rest cmd) escaped? state current-arg args))))
                          (throw (IllegalStateException. (format "Tokenizer is in an invalid state: %s" state))))))))]
    (if escaped?
      (conj args (str current-arg \\))
      (if (not= state :no-token)
        (conj args current-arg)
        args))))
