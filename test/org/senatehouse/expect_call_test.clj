(ns org.senatehouse.expect-call-test
  (use clojure.test
       org.senatehouse.expect-call
       org.senatehouse.expect-call.internal))

;; These tests follow the examples in the README

(defn log [& args]
  (apply println args)
  :logged)

(defn check-error [a b]
  (when (= a :error)
    (log "ERROR:" (pr-str b))))


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
  (let [make-mock make-mock
        mock (eval (make-mock '(#{} log [:error _] :return-value)))
        do-mock (eval (make-mock '(#{:do} log [:error _])))]
    
    (is (= (mock :error "abc") :return-value))

    (expecting-failure
      (mock :not-an-error "abc"))

    (is (= (do-mock :error "abc") :logged) ":do mocks actually call the function")

    :ok))



(deftest readme-examples

  (testing "Basic pass"
    (with-expect-call (log ["ERROR:" _])
      (check-error :error "abc")))

  (testing "Basic fail"
    (expecting-failure
      (with-expect-call (log ["ERROR:" _])
        (check-error :success "abc"))))

  (testing "Function body executes"
    (with-expect-call (log ["ERROR:" msg] (is (= msg "\"abc\"")))
      (check-error :error "abc")
      (check-error :success "xyz")))

  (testing "Enforce multiple calls"
    (expecting-failure
     (with-expect-call [(log ["ERROR:" "\"abc\""])
                        (log ["ERROR:" "\"xyz\""])]
       (check-error :error "abc")
       (check-error :error "xyz")
       (check-error :error "Surprise!"))))

  (testing "Multiple calls"
    (with-expect-call [(log ["ERROR:" "\"abc\""])
                       (log ["ERROR:" "\"xyz\""])]
      (check-error :error "abc")
      (check-error :error "xyz"))))


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
     (with-expect-call (:never log) (log :test))))
  
  (testing "Not called"
    (check-line
     (with-expect-call (log))))
  
  (testing "Wrong function"
    (check-line (with-expect-call [(log) (println)] (println "hi"))))
  
  (testing "Wrong args"
    (check-line (with-expect-call (log [:x]) (log :y)))))

