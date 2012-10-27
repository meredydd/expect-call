(ns org.senatehouse.expect-call
  (use clojure.test [clojure.core.match :only (match)]))

(def ^{:private true :dynamic true
       :doc "Format: ([function closure fn-name arg-form], [function closure arg-form], ...)"}
  *calls*)

(def ^{:private true :dynamic true
       :doc "Format: {function closure, function closure}"}
  *more-fns*)

(defn -expected-call
  "Used by (expect-call) macro. You don't call this."
  [real-fn real-fn-name args]
  (let [[ex-real-fn ex-fn ex-real-fn-name ex-args] (first *calls*)]
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
                    :message (if ex-real-fn
                               "Wrong function called"
                               (str "Too many calls to " real-fn-name))
                    :expected (cons ex-real-fn-name ex-args)
                    :actual (cons real-fn-name args)})))))

(defn ^:private make-mock [[tags real-fn-name & [args & body]]]
  (let [args (or args '[& _])
        real-fn (gensym "real-fn")]
    `(let [~real-fn ~real-fn-name]
       (fn ~(gensym (str real-fn-name "-mock")) [& ~'myargs]
         (match (apply vector ~'myargs)
                ~args (do ~@body ~@(when (:do tags) `((apply ~real-fn ~'myargs))))
                :else (do-report {:type :fail
                                  :message "Unexpected arguments"
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

    `(binding [*more-fns*
               ~(apply merge {}
                       (for [[tags real-fn :as expected-fn] expected-fns
                             :when (or (:more tags) (:never tags))]
                         (if (:more tags)
                           {real-fn (make-mock expected-fn)}
                           {real-fn `(fn [& args#]
                                       (do-report {:type :fail
                                                   :message (str ~real-fn " should not be called")
                                                   :expected (quote (:never ~real-fn))
                                                   :actual (cons (quote ~real-fn)
                                                                 args#)}))})))


               *calls* (list
                        ~@(for [[tags real-fn args :as expected-fn] expected-fns
                                :when (not (or (:more tags) (:never tags)))]
                            [real-fn (make-mock expected-fn)
                             `(quote ~real-fn) `(quote ~args)]))]
       (let [result#
             (with-redefs
               ~(apply vector
                       (let [fns (apply hash-set
                                        (for [[_ real-fn] expected-fns]
                                          real-fn))]
                         (apply
                          concat
                          (for [f fns]
                            [f `(let [f# ~f]
                                  (fn [& a#] (-expected-call f# (quote ~f) a#)))]))))
                       ~@body)]
         ;; If we haven't used up all our calls, we error out
         (when-let [[_# _# ex-fn-name# ex-args#] (first *calls*)]
           (do-report {:type :fail
                       :message (str "Function " ex-fn-name# " was not called")
                       :expected (cons ex-fn-name# ex-args#)
                       :actual nil}))
         result#))))
