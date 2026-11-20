# Stage 01 Report

## Summary
Clojure plugin now depends only on `org.clojure:clojure:1.12.2`; Rama, extra repositories, bogus
export, stale javadoc block and `rama_hello.clj` removed. `copyOSGiJars` empties the plugin lib dir
first. `ClojureScriptEngineFactory.CLOJURE_VERSION` ("1.12.2") backs `getLanguageVersion()`.
Tests: `clojure-version-test` compares against the constant; `rama-absent-test` added.

## Files changed
build.gradle, ClojureScriptEngineFactory.java, test_main.clj (clojure plugin); rama_hello.clj deleted;
this report. ClojureTestRunner untouched.

## Gate results
- Baseline (cca3f79a62): compile OK; clojure plugin 1 JUnit (2 clojure.test, 2 assertions) pass; script 99/99; deploy lib had 195 jars incl. rama-1.3.0, rama-shaded-*.
- Final: compile OK; clojure plugin 1 JUnit (2 clojure.test, 2 assertions, 0 failures) pass; script 99/99 (0 fail, 0 err).
- Runner check: temporarily changed assertion to `(= "0.0.0" (clojure-version))`: output `1 failures, 0 errors`, JUnit FAILED with `RuntimeException: Clojure tests failed: 1 failures, 0 errors.`, build failed. Reverted, not committed.
- Java 8 API: diff has no Java 9+ API (only a constant and a return).

## Deploy listing
Deployed at base (195 jars), added `stale.jar`, then deployed with the change:
```
clojure-1.12.2.jar
core.specs.alpha-0.4.74.jar
plugin-1.13.4.jar
spec.alpha-0.5.238.jar
```
`plugin-1.13.4.jar` is the plugin's own jar. No rama, no stale.jar, one Clojure version.

## Deviations
1. Worktree was based on 64d510ec0b, not cca3f79a62; created `stage-01` from cca3f79a62 as instructed.
2. `gradlew` is gitignored and absent in the worktree; copied `gradlew` and `gradle/wrapper` from the main checkout (untracked/ignored, not committed).
3. `(ClojureScriptEngineFactory/CLOJURE_VERSION)` failed ("no matches found for static method"): Clojure cannot see package-private fields. Kept the field package-private as specified and read it by reflection (`getDeclaredField` + `setAccessible`) in test_main.clj, with a short comment. No test-classpath wiring was needed (`lib` already visible).
4. DoD `git grep -n -i rama -- freeplane_plugin_clojure` is not empty: the mandated `rama-absent-test` must name `com.rpl.rama`. Only test_main.clj matches (lines 14-17).
5. Deploy "old set": used the actual base deploy (195 jars) plus dummy stale.jar.
6. Used a scratchpad wrapper script for the Gradle preamble (zsh word-splitting of `$G`); same command.

## Open questions
- Should `CLOJURE_VERSION` be public, or derived from build.gradle, to avoid duplicating "1.12.2"? 
- `jar` task may include `plugin-1.13.4.jar` naming from root build; untouched.
