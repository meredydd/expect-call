(ns org.senatehouse.expect-call-test
  (:require [clojure.test :refer :all]
            [org.senatehouse.expect-call :as sut]
            [org.senatehouse.expect-call.internal :as internal]))

;; These tests follow the examples in the README

(defn log [& args]
  (apply println args)
  :logged)

(defn check-error [a b]
  (when (= a :error)
    (log "ERROR:" (pr-str b))))

(defn destructuring [_x])

(defmacro expecting-failure
  "Execute body, expecting it to report a test failure.

   It would be neat to implement this as:

   (with-expect-call ~'(report [{:type :fail}])
     ~@body)

   ...however, expect-call disables its interception hooks
   while calling (report), to avoid going into an infinite
   loop. So it doesn't work in this case. Sadly."
  [& body]
  `(let [reported?# (atom false)]
     (with-redefs [report (fn [m#]
                            (when-not m#
                              (throw (Exception. "(report) requires a parameter")))
                            (swap! reported?# #(or % m#)))]
       (try
         ~@body
         (finally
          (cond
           (not @reported?#) (report {:type :fail,
                                      :expected {:type :fail},
                                      :actual nil,
                                      :message "Expected test to fail"})
           (not= (:type @reported?#) :fail) (report @reported?#)

           :else :ok))))
     @reported?#))

(deftest mocks
  (let [make-mock internal/make-mock
        mock (eval (make-mock `(#{} log [:error ~'_] :return-value)))
        do-mock (eval (make-mock `(#{:do} log [:error ~'_])))]

    (is (= (mock :error "abc") :return-value))

    (expecting-failure
      (mock :not-an-error "abc"))

    (is (= (do-mock :error "abc") :logged) ":do mocks actually call the function")

    :ok))

(deftest readme-examples

  ;; These are patterned after (although not quite identical to) the examples
  ;; in the README.

  (testing "Basic pass"
    (sut/with-expect-call (log ["ERROR:" _])
      (check-error :error "abc")))

  (testing "Basic fail"
    (expecting-failure
     (sut/with-expect-call (log ["ERROR:" _])
       (check-error :success "abc"))))

  (testing "Omitting parameters means we don't care what they are"
    (sut/with-expect-call (log)
      (check-error :error "abc")))

  (testing "Function body executes"
    (sut/with-expect-call (log ["ERROR:" msg] (is (= msg "\"abc\"")))
      (check-error :error "abc")
      (check-error :success "xyz")))

  (testing "Enforce multiple calls"
    (expecting-failure
     (sut/with-expect-call [(log ["ERROR:" "\"abc\""])
                            (log ["ERROR:" "\"xyz\""])]
       (check-error :error "abc")
       (check-error :error "xyz")
       (check-error :error "Surprise!"))))

  (testing "Multiple calls"
    (sut/with-expect-call [(log ["ERROR:" "\"abc\""])
                           (log ["ERROR:" "\"xyz\""])]
      (check-error :error "abc")
      (check-error :error "xyz")))

  (testing "arg checking against binding"
    (let [foo ":foo"]
      (sut/with-expect-call (log ["ERROR:" foo])
                            (check-error :error :foo))
      (expecting-failure
        (sut/with-expect-call (log ["ERROR:" foo])
                              (check-error :error :bar)))))

  (testing "sequence destructuring"
    (sut/with-expect-call
     (destructuring [[a b]] (is (= a b)))
     (destructuring (mapv inc [3 3])))

    (expecting-failure
     (sut/with-expect-call
      (destructuring [[a b]] (is (= a b)))
      (destructuring (mapv inc [3 4]))))

    (sut/with-expect-call
     (destructuring [[a [b [c :foo]]]] (is (= a b c)))
     (destructuring [1 [1 [1 :foo]]]))

    ;; :as not supported
    #_(sut/with-expect-call
     (destructuring [[a b :as c]] (is (= [4 4] c)))
     (destructuring (mapv inc [3 3])))

    (testing "with arg checking against binding"
      (let [foo :foo]
        (sut/with-expect-call
         (destructuring [[foo bar]] (is (= foo bar)))
         (destructuring [:foo :foo]))

        (expecting-failure
         (sut/with-expect-call
          (destructuring [[foo bar]] (is (= foo bar)))
          (destructuring [:bar :bar])))

        (sut/with-expect-call
         (destructuring [[foo :bar]])
         (destructuring [:foo :bar]))

        (expecting-failure
         (sut/with-expect-call
          (destructuring [[foo :bar]])
          (destructuring [:foo :foo])))

        (expecting-failure
         (sut/with-expect-call
          (destructuring [[foo :bar]])
          (destructuring [:bar :bar])))))

    ;; map destructuring not allowed
    #_(sut/with-expect-call
       (destructuring [{foo :foo}] (is (= foo 1)))
       (destructuring [{:foo 1 :bar 1}]))
    #_(expecting-failure
       (sut/with-expect-call
        (destructuring [{foo :foo bar :bar}] (is (= foo bar)))
        (destructuring {:foo 1 :bar 2}))))

  (testing "matching literal vectors and maps"
    (sut/with-expect-call
     (destructuring [[1 1]])
     (destructuring (mapv inc [0 0])))

    (sut/with-expect-call
     (destructuring [[nil nil]])
     (destructuring (into [] (repeat 2 nil))))

    (sut/with-expect-call
     (destructuring [[:foo :bar]])
     (destructuring [:foo :bar]))

    (expecting-failure
     (sut/with-expect-call
      (destructuring [[1 1]])
      (destructuring (mapv inc [0 3]))))

    (sut/with-expect-call
     (destructuring [{:foo :bar}])
     (destructuring (zipmap [:foo] [:bar])))

    (expecting-failure
     (sut/with-expect-call
      (destructuring [{:foo :bar}])
      (destructuring (zipmap [:foo] [:qux]))))))

(defmacro check-line [expr]
  `(let [report# (expecting-failure ~expr)
         ~'file-and-line (str (:file report#) ":" (:line report#))]
     (is (~'= ~'file-and-line ~(str "expect_call_test.clj:" (:line (meta expr)))))
     (when-not (= ~'file-and-line ~(str "expect_call_test.clj:" (:line (meta expr))))
       (println "Actual report:" (pr-str report#))
       (println "Actual stack trace:")
       (doseq [s# (take 10 (:stack-trace report#))]
         (println s#)))))

(deftest line-number-reporting
  ;; Test that every (report) mode we have yields the correct
  ;; line number

  (testing ":never"
    (check-line
     (sut/with-expect-call (:never log) (log :test))))

  (testing "Not called"
    (check-line
     (sut/with-expect-call (log))))

  (testing "Wrong function"
    (check-line (sut/with-expect-call [(log) (println)] (println "hi"))))

  (testing "Wrong args"
    (check-line (sut/with-expect-call (log [:x]) (log :y)))))
