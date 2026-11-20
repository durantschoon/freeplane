(ns org.freeplane.plugin.clojure.test-main
  (:require [clojure.test :refer :all]))

;; CLOJURE_VERSION is package-private, so Clojure's static field syntax cannot see it
(defn- declared-clojure-version []
  (let [field (.getDeclaredField org.freeplane.plugin.clojure.ClojureScriptEngineFactory "CLOJURE_VERSION")]
    (.setAccessible field true)
    (.get field nil)))

(deftest clojure-version-test
  (testing "Clojure version matches the version reported by the engine factory"
    (is (= (declared-clojure-version) (clojure-version)))))

(deftest rama-absent-test
  (testing "Rama is not on the classpath"
    (is (thrown? java.io.FileNotFoundException
                 (require 'com.rpl.rama)))))
