(ns org.senatehouse.expect-call
  (use clojure.test [clojure.core.match :only (match)]))

(def ^{:private true :dynamic true
       :doc "Format: ([function closure arg-form], [function closure arg-form], ...)"}
  *calls*)

(def ^{:private true :dynamic true
       :doc "Format: {function closure, function closure}"}
  *more-fns*)

(defn ^:private -expected-call [real-fn args]
  (let [[ex-real-fn ex-fn ex-args] (first *calls*)]
    (if (= real-fn ex-real-fn)
      (do ; It matched the next explicit expectation. Run it.
        (set! *calls* (rest *calls*))
        (apply ex-fn args))

      ;; It didn't match an explicit expectation - did it match
      ;; a :more or :never?
      (if-let [more-fn (*more-fns* real-fn)]
        (apply more-fn args)

        ;; Nope - it's just wrong
        (do-report {:type :fail
                    :message "Wrong function called"
                    :expected (cons ex-real-fn ex-args)
                    :actual (cons real-fn args)})))))

(defn ^:private make-mock [[tags real-fn-name & [args & body]]]
  (let [args (or args '[& _])
        real-fn (gensym "real-fn")]
    `(let [~real-fn ~real-fn-name]
       (fn ~(gensym (str real-fn-name "-mock")) [& ~'myargs]
         (match (apply vector ~'myargs)
                ~args (do ~@body ~@(when (:do tags) `((apply ~real-fn ~'myargs))))
                :else (do-report {:type :fail
                                  :message "Invalid arguments"
                                  :expected (quote ~(cons real-fn-name args))
                                  :actual (cons (quote ~real-fn-name) ~'myargs)}))))))
              
(defmacro expect-call
  "expected-fns: (fn arg-match body...)
                 or [(fn arg-match body...), (fn arg-match body...)...]
   Each fn may be preceded by keywords :more, :never or :do."
  [expected-fns & body]
  
  (let [expected-fns (if (vector? expected-fns) expected-fns [expected-fns])
        expected-fns (for [fspec expected-fns]
                       (cons (apply hash-set (take-while keyword? fspec))
                             (drop-while keyword? fspec)))]

    `(binding [*more-fns* nil])))

