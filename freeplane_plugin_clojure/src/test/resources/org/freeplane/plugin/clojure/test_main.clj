(ns org.freeplane.plugin.clojure.test-main
  (:require [clojure.test :refer :all]))

(deftest rama-dependencies-test
  (testing "Rama namespace is loadable"
    (is (try
          (require '[com.rpl.rama])
          true
          (catch Exception _ false))
        "com.rpl.rama namespace should be loadable")))

(deftest clojure-version-test
  (testing "Clojure version check"
    (let [v (clojure-version)]
      (is (= "1.12.2" v) (str "Expected 1.12.2 but got " v)))))
