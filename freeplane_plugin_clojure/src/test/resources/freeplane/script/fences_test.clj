(ns freeplane.script.fences-test
  (:require [clojure.test :refer :all]
            [freeplane.script.fences :as f]))

(def sample-quirk
  {:id :sample :dependency :freeplane :introduced "1.13.1" :fixed-in "1.13.3"
   :disables #{:render} :evidence "issue 42"})

(deftest parse-version-test
  (is (= {:numbers [1 12 2 0] :qualifier nil} (f/parse-version "1.12.2")))
  (is (= {:numbers [1 13 0 0] :qualifier nil} (f/parse-version "1.13")))
  (is (= {:numbers [1 13 0 0] :qualifier "alpha1"} (f/parse-version "1.13.0-alpha1")))
  (is (= {:numbers [1 13 0 0] :qualifier "rc"} (f/parse-version "1.13.0 rc")))
  (is (= {:numbers [1 12 2 0] :qualifier "SNAPSHOT"} (f/parse-version "1.12.2-SNAPSHOT")))
  (is (= {:numbers [1 13 4 0] :qualifier "nightly_build"} (f/parse-version "1.13.4 nightly_build")))
  (is (nil? (f/parse-version "x1")))
  (is (nil? (f/parse-version ""))))

(deftest compare-versions-test
  (is (neg? (f/compare-versions "1.9" "1.12")))
  (is (neg? (f/compare-versions "1.13.0 rc" "1.13.0")))
  (is (neg? (f/compare-versions "1.12.2-SNAPSHOT" "1.12.2")))
  (is (zero? (f/compare-versions "1.13" "1.13.0.0")))
  (is (zero? (f/compare-versions (f/parse-version "1.13") "1.13.0.0"))))

(deftest at-least-test
  (is (f/at-least? "1.12.0" "1.12.0"))
  (is (f/at-least? "1.12.1" "1.12.0"))
  (is (not (f/at-least? "1.11.9" "1.12.0"))))

(deftest floor-violation-test
  (is (nil? (f/floor-violation :clojure "1.12.2")))
  (let [e (f/floor-violation :clojure "1.11.4")]
    (is (instance? clojure.lang.ExceptionInfo e))
    (is (= "clojure 1.11.4 is older than the supported minimum 1.12.0" (ex-message e)))
    (is (= {:fence :floor :dependency :clojure :found "1.11.4" :needed "1.12.0"} (ex-data e)))))

(deftest requirement-violation-test
  (is (nil? (f/requirement-violation :freeplane "1.13.4" "1.13.2")))
  (let [e (f/requirement-violation :freeplane "1.13.0" "1.13.2")]
    (is (instance? clojure.lang.ExceptionInfo e))
    (is (= "freeplane 1.13.0 is older than the supported minimum 1.13.2" (ex-message e)))
    (is (= {:fence :requirement :dependency :freeplane :found "1.13.0" :needed "1.13.2"}
           (ex-data e)))))

(deftest newer-than-tested-test
  (is (nil? (f/newer-than-tested :clojure "1.12.2")))
  (is (string? (f/newer-than-tested :clojure "1.12.3"))))

(deftest active-quirks-test
  (let [quirks [sample-quirk]]
    (is (= [sample-quirk] (f/active-quirks quirks :freeplane "1.13.1")))
    (is (= [sample-quirk] (f/active-quirks quirks :freeplane "1.13.2")))
    (is (empty? (f/active-quirks quirks :freeplane "1.13.0")))
    (is (empty? (f/active-quirks quirks :freeplane "1.13.3")))
    (is (empty? (f/active-quirks quirks :clojure "1.13.2")))
    (is (= [(assoc sample-quirk :fixed-in nil)]
           (f/active-quirks [(assoc sample-quirk :fixed-in nil)] :freeplane "9.0")))))

(deftest disabled-violation-test
  (is (nil? (f/disabled-violation [sample-quirk] :freeplane "1.13.2" :other)))
  (is (nil? (f/disabled-violation [sample-quirk] :freeplane "1.13.3" :render)))
  (let [e (f/disabled-violation [sample-quirk] :freeplane "1.13.2" :render)]
    (is (instance? clojure.lang.ExceptionInfo e))
    (is (= :quirk (:fence (ex-data e))))
    (is (= :sample (:id (ex-data e))))
    (is (= "issue 42" (:evidence (ex-data e))))))

(deftest stale-quirks-test
  (let [stale (assoc sample-quirk :id :stale :fixed-in "1.13.0")
        live (assoc sample-quirk :id :live)
        open (assoc sample-quirk :id :open :fixed-in nil)]
    (is (= [stale] (f/stale-quirks [stale live open] f/supported))))
  (is (empty? (f/stale-quirks f/quirks f/supported))))

(deftest unparsable-runtime-version-test
  (is (f/unparsable? "weird-build"))
  (is (not (f/unparsable? "1.12.2")))
  (is (nil? (f/floor-violation :clojure "weird-build")))
  (is (nil? (f/requirement-violation :freeplane "weird-build" "1.13.2")))
  (is (= "clojure version weird-build is unparsable; relying on capability probes"
         (f/newer-than-tested :clojure "weird-build")))
  (is (= [] (f/active-quirks [sample-quirk] :freeplane "weird-build")))
  (is (nil? (f/disabled-violation [sample-quirk] :freeplane "weird-build" :render))))

(deftest compare-versions-sign-test
  (is (= -1 (f/compare-versions "1.13.0 alpha" "1.13.0 rc")))
  (is (= 1 (f/compare-versions "1.13.0 rc" "1.13.0 alpha")))
  (is (= -1 (f/compare-versions "1.9" "1.12")))
  (is (= 1 (f/compare-versions "1.12.2" "1.12.2-SNAPSHOT")))
  (is (= 0 (f/compare-versions "1.13" "1.13.0.0"))))

(deftest literal-misuse-still-throws-test
  (is (thrown? IllegalArgumentException (f/at-least? "1.12" "x")))
  (is (thrown? IllegalArgumentException (f/compare-versions "x" "1.12"))))

(deftest numbered-qualifier-order-test
  (is (= -1 (f/compare-versions "1.13.4-rc3" "1.13.4-rc10")))
  (is (= 1 (f/compare-versions "1.13.4-rc10" "1.13.4-rc3")))
  (is (= -1 (f/compare-versions "1.13.0-alpha2" "1.13.0-beta1")))
  (is (= 0 (f/compare-versions "1.13.0 RC1" "1.13.0 rc1")))
  (is (= -1 (f/compare-versions "1.13.0-rc1" "1.13.0"))))
