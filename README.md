# expect-call

A Clojure library that makes it simple to mock out functions for testing.

## Introduction

However much we would like to live in a purely functional world, we don't. Mocking is how we deal with the fact that the code we want to test is coupled with other functions that we don't want our tests to exercise.

`expect-call` is a straightforward library for testing code that calls functions. You just give it a series of function calls your code is going to make, and it verifies that your code really does make those calls.


## Installation

If you're using `lein` (which you should be), add the following dependency to your `project.clj` file:

```clojure
[org.senatehouse/expect-call "0.2.0"]
```


## Usage

### Basic Usage

Here's an example of a classic impure function that's a sensible design, but difficult to test: We want to verify that some of our code logs an error correctly. If we have a function like this:

```clojure
; Log a message. In real life (log) would be more sophisticated
; - possibly involving database access and all sorts of things
; we don't want to touch in a test.
(def log println)

; This function checks for an error in its input, and logs a
; message if it finds one.
(defn check-error [a b]
  (when (= a :bad-val)
    (log :error b)))
```

We can test it like so:

```clojure
(use 'clojure.test 'org.senatehouse.expect-call)

(deftest check-logging
  (with-expect-call (log [:error _])
    (check-error :bad-val "abc")))
```

If we run it, we will see that it passes:

```
user> (check-logging)
nil
user>
```

The following test will fail, because the log function is not called:

```clojure
(deftest check-logging-2
  (expect-call (log [:error _])
    (check-error :good-val "abc")))
```

And it will produce an report like this:

```
user> (check-logging-2)

FAIL in (check-logging-2) (NO_SOURCE_FILE:2)
Function log was not called
expected: (log :error _)
  actual: nil
```

The parameters we specify for our expected function calls is a `clojure.core.match` expression. We can use literal value, map and sequence destructuring, and variable bindings. If you bind a variable, you can use it in the body of your mock function. For example:

```clojure
(deftest check-logging-3
  (expect-call (log [:error msg] (is (= msg "abc")))
    (check-error :bad-val "abc")
    (check-error :good-val "xyz")))
```

Of course, we could have expressed that expectation much more concisely using pattern-matching: `(expect-call (log [:error "abc"]))`.


### Multiple Calls

You can expect multiple calls to the same function (or different functions), by specifying a vector of calls. The test will only pass if those functions are called in the order you specify.

Here's an example:

```clojure
(deftest check-logging-4
  (expect-call [(log [:error "abc"])
                (log [:error "xyz"])]
    (check-error :bad-val "abc")
    (check-error :bad-val "xyz")
    (check-error :bad-val "Surprise!")))
```

This test will fail, because although the first two "log" calls matched our expectations, the last one wasn't expected:

```
user> (check-logging-4)

FAIL in (check-logging-4) (NO_SOURCE_FILE:3)
Too many calls to log
expected: (nil)
  actual: (log :error "Surprise!")
```


### Quoth the raven, `:never` and `:more`

Sometimes, we want to test that a function *isn't* called. We can do this with `(:never function-name)`:

```clojure
(deftest check-logging-5
  (expect-call (:never log)
    (check-error :success "abc")))
```

(This test will pass, because the `log` function is never called.)

We can also say that we don't care about how many more times a function is called, once the test sequence we're interested in is complete:

```clojure
(deftest check-logging-5
  (expect-call [(log [:error "abc"]) (:more log)]
    (check-error :bad-val "abc")
    (check-error :bad-val "xyz")))
```

(This test will pass.)

We can specify patterns and function bodies with `:more`:

```clojure
(deftest check-logging-6
  (expect-call (:more log [:error _])
    (check-error :error "abc")
    (check-error :error "xyz")))
```

(This test will pass. If we had called `log` with first parameter other than `:error`, it would fail because it doesn't match the pattern.)

Note that you can't specify a pattern or test behaviour with `:never`. If a `:never` function is called, the test fails. If you want to do some checking of the arguments, like making sure that the second argument always has three characters, you can do it in the body of the mock function like this:

```clojure
(deftest check-logging-7
  (expect-call (:more log [:error s]
                 (is (= 3 (.length s))))
    (check-error :error "abc")
    (check-error :error "xyz")))
```


### `:do`

Sometimes, we might not want to stub out a function entirely - we want to verify that it's happened, but we want it to perform its normal duties as well. We specify this with `(:do fn-name params & body)`.

Once the body of a `:do` mock function has been executed, the real function is called. The value returned by the real function is what the caller (that is, the code under test) sees.

For example:
```clojure
(deftest check-test-error
  (expect-call (:do log [:error "abc"])
    (check-error :error "abc")))
```

This test will pass, and it will also print a log message.


You can combine :do and :more. For example, we want to suppress the log message that we're testing for - but if we see something unexpected, we might want to log it as normal. This is how we do it:

```clojure
(deftest check-test-error
  (expect-call [(log [:error "abc"]) (:do :more log)]
    (check-error :error "abc")
    (log "Unexpected log message")))
```

This test will pass, and also print `Unexpected log message` to the console.

Note that omitting the pattern in the second (log) clause means "accept any arguments".


## Larger example

Here is a fanciful example of the sort of code I often find myself writing when working with hardware.

```clojure
(defn launch-rocket []

  (when-let [key (launch-key-present?)]

    (start-fuel-pump)

    (while (< (get-fuel-pressure) 1000.0)
      (Thread/yield))

    (ignition/enable-with-key key)

    (when (< (get-fuel-pressure) 900.0)
      (abort!))

    (send-email "mission-control@example.com"
                (str "We have liftoff at " (java.util.Date.))))) 

```

It's a long sequence of imperative actions, and it's a pain to test. (And if it weren't a long sequence of imperative actions, it would be a pain to hack on.)

Here's a sample test case, using `expect-call`:

```clojure
(deftest successful-launch
  (with-expect-call [(launch-key-present? [] "secret key")
                     (start-fuel-pump)
                     ; Check it doesn't go anywhere until it has a good pressure reading
                     (get-fuel-pressure [] 860.0)
                     (get-fuel-pressure [] 1001.0)
                     ; After this, we don't care how many more times it checks the pressure
                     (:more get-fuel-pressure [] 950.0)
                     ; ...but it definitely fails the test if it aborts on this input data.
                     (:never abort!)
                     ; Does it use the right key?
                     (ignition/enable-with-key ["secret key"])
                     ; Use a binding to capture the email body
                     (send-email ["mission-control@example.com" body]
                       (assert (re-matches #"We have liftoff at .* \d{2}:\d{2}:\d{2}.*\d{4}" body)))]
    (launch-rocket))
```
Now, wasn't that so much nicer than dependency injection?


## Feedback

Please send feedback and pull requests to `meredydd@senatehouse.org`, or `meredydd` on GitHub.


## License

Copyright © 2012 Meredydd Luff <meredydd@senatehouse.org>

Distributed under the Eclipse Public License, the same as Clojure.

