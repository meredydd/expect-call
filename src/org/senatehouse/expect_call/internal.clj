(ns org.senatehouse.expect-call.internal
  (:require [clojure.test :refer :all]
            [clojure.core.match :refer [match]]))

(def ^:dynamic *disable-interception* false)

(defn my-report
  "Disable interception, to prevent looping if the test reporting code
   uses a function we're intercepting. Also put accurate file and line
   information into the message."
  [msg]

  (binding [*disable-interception* true]
    (let [stack-trace (.getStackTrace (Throwable.))
          ^StackTraceElement s (->> stack-trace
                                    (drop-while #(.startsWith (.getClassName ^StackTraceElement %)
                                                              "org.senatehouse.expect_call.internal$"))
                                    (first))]
      (report
        (merge
          {:file        (.getFileName s) :line (.getLineNumber s)
           :stack-trace (seq stack-trace)}
          msg)))))

(defn -expected-call
  "Used by (expect-call) macro. You don't call this."
  [[more-fns calls :as _state] real-fn real-fn-name args]
  (if *disable-interception*
    (apply real-fn args)

    (let [[ex-real-fn ex-fn ex-real-fn-name ex-args] (first @calls)]
      (if (= real-fn ex-real-fn)
        (do ; It matched the next explicit expectation. Run it.
          (swap! calls rest)
          (apply ex-fn args))

        ;; It didn't match an explicit expectation - did it match
        ;; a :more or :never?
        (if-let [more-fn (more-fns real-fn)]
          (apply more-fn args)

          ;; Nope - it's just wrong
          (my-report {:type :fail
                      :message (if ex-real-fn
                                 "Wrong function called"
                                 (str "Too many calls to " real-fn-name))
                      :expected (cons ex-real-fn-name ex-args)
                      :actual (cons real-fn-name args)}))))))

(defn make-mock [[tags real-fn-name & [args & body]]]
  (let [args (or args '[& _])
        real-fn (gensym "real-fn")]
    `(let [~real-fn ~real-fn-name]
       (fn ~(gensym (str (name real-fn-name) "-mock")) [& ~'myargs]
         (match (apply vector ~'myargs)
                ~args (do ~@body ~@(when (:do tags) `((apply ~real-fn ~'myargs))))
                :else (my-report {:type :fail
                                  :message "Unexpected arguments"
                                  :expected (quote ~(cons real-fn-name args))
                                  :actual (cons (quote ~real-fn-name)
                                                ~'myargs)}))))))

(defmacro -expect-call
  "expected-fns: (fn arg-match body...)
                 or [(fn arg-match body...), (fn arg-match body...)...]
   Each fn may be preceded by keywords :more, :never or :do."
  [expected-fns & body]

  (let [expected-fns (if (vector? expected-fns) expected-fns [expected-fns])
        expected-fns (for [fspec expected-fns]
                       (cons (apply hash-set (take-while keyword? fspec))
                             (drop-while keyword? fspec)))

        state (gensym "state")]

    `(let [;; Format: {function closure, function closure}
           more-fns#
           ~(apply merge {}
                   (for [[tags real-fn :as expected-fn] expected-fns
                         :when (or (:more tags) (:never tags))]
                     (if (:more tags)
                       {real-fn (make-mock expected-fn)}
                       {real-fn `(fn [& args#]
                                   (my-report {:type :fail
                                               :message ~(str real-fn " should not be called")
                                               :expected (quote (:never ~real-fn))
                                               :actual (cons (quote ~real-fn)
                                                             args#)}))})))

           ;; Format: ([function closure fn-name arg-form],
           ;;          [function closure arg-form], ...)
           calls# (atom
                   (list
                    ~@(for [[tags real-fn args :as expected-fn] expected-fns
                            :when (not (or (:more tags) (:never tags)))]
                        [real-fn (make-mock expected-fn)
                         `(quote ~real-fn) `(quote ~args)])))

           ~state [more-fns# calls#]]

       (let [result#
             (with-redefs
               ~(apply vector
                       (let [fns (reduce (fn [set [_ real-fn]] (conj set real-fn))
                                         #{} expected-fns)]
                         (apply
                          concat
                          (for [f fns]
                            [f `(let [f# ~f]
                                  (fn ~(symbol (str (name f) "-mock")) [& a#]
                                    (-expected-call ~state f# (quote ~f) a#)))]))))
               ~@body)]
         ;; If we haven't used up all our calls, we error out
         (when-let [[_# _# ex-fn-name# ex-args#] (first @calls#)]
           (my-report {:type :fail
                       :message (str "Function " ex-fn-name# " was not called")
                       :expected (cons ex-fn-name# ex-args#)
                       :actual nil}))
         result#))))
