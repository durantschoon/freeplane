(ns org.freeplane.plugin.clojure.test-main
  (:require [clojure.test :refer :all]))

(deftest clojure-version-test
  (testing "Clojure version matches the version reported by the engine factory"
    (is (= (.getLanguageVersion (org.freeplane.plugin.clojure.ClojureScriptEngineFactory.))
           (clojure-version)))))

(deftest rama-absent-test
  (testing "Rama is not on the classpath"
    (is (thrown? java.io.FileNotFoundException
                 (require 'com.rpl.rama)))))

(deftest supported-clojure-matches-factory-test
  (testing "built-against Clojure in the fence table matches the engine factory"
    (require (quote freeplane.script.fences))
    (is (= (.getLanguageVersion (org.freeplane.plugin.clojure.ClojureScriptEngineFactory.))
           (get-in @(resolve (quote freeplane.script.fences/supported)) [:clojure :built-against])))))
