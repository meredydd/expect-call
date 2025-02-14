(ns org.senatehouse.expect-call
  (:require [org.senatehouse.expect-call.internal :as internal]))

(defmacro expect-call
  "expected-fns: (fn arg-match body...)
                 or [(fn arg-match body...), (fn arg-match body...)...]
   Each fn may be preceded by keywords :more, :never or :do."
  [expected-fns & body]
  
  `(internal/-expect-call ~expected-fns ~@body))

;; This is an alias
(defmacro with-expect-call
  "This is an alias for expect-call, with a with- prefix
   so emacs clojure-mode indents it more nicely"
  [& args]
  `(internal/-expect-call ~@args))
