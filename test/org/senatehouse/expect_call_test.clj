(ns org.senatehouse.expect-call-test
  (use clojure.test
       org.senatehouse.expect-call
       org.senatehouse.expect-call.internal))

;; These tests follow the examples in the README

(defn log [& args]
  (apply println args)
  :logged)


(deftest mocks
  (let [make-mock make-mock
        mock (eval (make-mock '(#{} log [:error _] :return-value)))
        do-mock (eval (make-mock '(#{:do} log [:error _])))]
    
    (is (= (mock :error "abc") :return-value))

    (expect-call (report) (mock :not-an-error "abc"))

    (is (= (do-mock :error "abc") :logged) ":do mocks actually call the function")

    :ok))



(defn check-error [a b]
  (when (= a :error)
    (log "ERROR:" (pr-str b))))

(defmacro expecting-failure
  "Execute body, expecting it to report a test failure"
  [& body]
  `(with-expect-call ~'(report [{:type :fail}])
     ~@body))

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
     (with-expect-call [(log ["ERROR:" "abc"])
                        (log ["ERROR:" "xyz"])]
       (check-error :error "abc")
       (check-error :error "xyz")
       (check-error :error "Surprise!"))))

  (testing "Multiple calls"
    (with-expect-call [(log ["ERROR:" "abc"])
                       (log ["ERROR:" "xyz"])]
      (check-error :error "abc")
      (check-error :error "xyz")))


  
)