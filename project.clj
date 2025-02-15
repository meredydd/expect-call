(defproject org.senatehouse/expect-call "0.4.0"
  :description "A Clojure library for no-fuss function mocking"
  :url "https://github.com/meredydd/expect-call"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/core.match "1.0.0"]]
  :profiles
  {;; these support running tests for different Clojure versions:
   ;; lein with-profile 1.10 test
   ;; also see https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md#merging:~:text=Another%20use%20of%20profiles%20is%20to%20test%20against%20various%20sets%20of%20dependencies
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}
   :1.11 {:dependencies [[org.clojure/clojure "1.11.4"]]}
   :1.12 {:dependencies [[org.clojure/clojure "1.12.0"]]}}
  :deploy-repositories [["clojars" {:sign-releases false :url "https://clojars.org/repo"}]])
