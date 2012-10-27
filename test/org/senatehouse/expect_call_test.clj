(ns org.senatehouse.expect-call-test
  (use clojure.test
        org.senatehouse.expect-call))

;; These tests follow the examples in the README

(defn log [& args]
  (apply println args)
  :logged)


(deftest mocks
  (let [make-mock @#'org.senatehouse.expect-call/make-mock
        mock (eval (make-mock '(#{} log [:error _] :return-value)))
        do-mock (eval (make-mock '(#{:do} log [:error _])))]
    
    (is (= (mock :error "abc") :return-value))

    ;; Can't use this yet, because I haven't 
    ;;(expect-called (report) (mock :not-an-error "abc"))
    (mock :not-an-error "abc")
    (println "Test should have failed above.")


    (is (= (do-mock :error "abc") :logged) ":do mocks actually call the function")
    ))

