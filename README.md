# expect-call

A Clojure library making it simple to mock out functions.

## Usage

### Basic Usage

Here's an example of a classic impure function that it's difficult to inject: We want to verify that a function logs something. If we have a function like this:

		(def log println)
		
		(defn check-error [a b]
		  (when (= a :error)
		    (log "ERROR:" (pr-str b))))

We can test it like so:

		(use 'clojure.test 'org.senatehouse.expect-call)

		(deftest check-test-error
		  (expect-call (log [:error _])
		    (check-error :error "abc")))


The following test will fail, because the log function is not called:

		(deftest check-test-error
		  (expect-call (log [:error _])
		    (check-error :success "abc")))


The "parameter list" for the function we're expecting to call is a clojure.core.match expression - so as well as specifying literal expected values, we can use wildcards like _ - and even binding forms to perform further tests or compose the function's return value. For example:

		(deftest check-test-error
		  (expect-call (log [:error msg] (is (= msg "\"abc\"")))
		    (check-error :error "abc")
		    (check-error :success "xyz")))

Of course, that expectation could be much more concisely specified using pattern-matching syntax, as (log [:error "abc"]).

### Multiple Calls

If the function is called more times than (expect-call) was told to expect, the test will fail as well. Above, we're just expecting a single function call. But we can provide a vector of calls too:

		(deftest check-test-error
		  (expect-call [(log [:error "abc"])
		                (log [:error "xyz"])]
		    (check-error :error "abc")
		    (check-error :error "xyz")
		    (check-error :error "Surprise!")))

This test will fail, because although the first two "log" calls matched our expectations, the last one wasn't expected.

### :never and :more

Sometimes, we want to test that a function *isn't* called. We can do this with (:never function-name):

		(deftest check-test-error
		  (expect-call (:never log)
		    (check-error :success "abc")))
(This test will pass.)

We can also say that we don't care about how many more times a function is called, once the test sequence we're interested in is complete:

		(deftest check-test-error
		  (expect-call [(log [:error "abc"]) (:more log)]
		    (check-error :error "abc")
		    (check-error :error "xyz")))

(This test will pass.)

We can specify patterns and function bodies with :more:

		(deftest check-test-error
		  (expect-call (:more log [:error _])
		    (check-error :error "abc")
		    (check-error :error "xyz")))

(This test will pass)

Note that you can't specify a pattern or test behaviour with :never - if a :never function is called, the test fails.


### :do

Sometimes, we might not want to stub out a function entirely - we want to verify that it's happened, but we want it to execute as well. We specify this with (:do fn-name params & body). Once the body of our test function has been executed, the real function is called - and its return value is returned in the original call site.

For example:
		(deftest check-test-error
		  (expect-call (:do log [:error "abc"])
		    (check-error :error "abc")))

This test will pass, and it will also print 'ERROR: "abc"'.


You can combine :do and :more. For example, this test swallows the log message that our test is looking for, but lets any other, unexpected messages through to the console:

		(deftest check-test-error
		  (expect-call [(log [:error "abc"]) (:do :more log)]
		    (check-error :error "abc")
		    (do something else...)))

This test will pass. Note that omitting the pattern in the second (log) clause means "accept any arguments".


## License

Copyright © 2012 Meredydd Luff <meredydd@senatehouse.org>

Distributed under the Eclipse Public License, the same as Clojure.

