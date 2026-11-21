# Stage 06 report: pure version-fence namespace

## Summary
Added `freeplane.script.fences` (pure, requires only `clojure.core` and `clojure.string`) with `supported`, `quirks`
(empty), `parse-version`, `compare-versions`, `at-least?`, `floor-violation`, `requirement-violation`,
`newer-than-tested`, `active-quirks`, `disabled-violation`, `stale-quirks`. `ClojureTestRunner` now runs a list of
two namespaces and reports per-namespace counts. Branch `stage-06`, base `919d84d7b4`.

## Files changed
- freeplane_plugin_clojure/src/main/resources/freeplane/script/fences.clj (new)
- freeplane_plugin_clojure/src/test/resources/freeplane/script/fences_test.clj (new)
- freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureTestRunner.java
- freeplane_plugin_clojure/src/test/resources/org/freeplane/plugin/clojure/test_main.clj (test 10 only)
- docs/stages/stage-06-REPORT.md

## Gate results
Counts from test-result XML after cleanTest (sum of testsuite tests/failures/errors):

| | Clojure plugin | Script plugin |
|---|---|---|
| Baseline (919d84d7b4) | 38 / 0 fail / 0 err | 99 / 0 / 0 |
| Final | 38 / 0 / 0 | 99 / 0 / 0 |

The Clojure JUnit count is unchanged because the runner is one JUnit test wrapping the `clojure.test` namespaces.
Runner output:
`Clojure tests passed: org.freeplane.plugin.clojure.test-main: 3 tests, 0 failures, 0 errors; freeplane.script.fences-test: 9 tests, 0 failures, 0 errors;`
(`Ran 3 tests containing 3 assertions.` / `Ran 9 tests containing 40 assertions.`)

- Compile: BUILD SUCCESSFUL.
- Tests: BUILD SUCCESSFUL.
- Deploy: copyOSGiJars BUILD SUCCESSFUL; lib/ = clojure-1.12.2.jar, core.specs.alpha-0.4.74.jar, plugin-1.13.4.jar, spec.alpha-0.5.238.jar.
- Java 8 API: runner uses `Arrays.asList`, no Java 9+ API.

## Deviations
1. Test 10 uses `(require 'freeplane.script.fences)` inside the deftest and `resolve`, rather than editing the `ns`
   form of `test_main.clj` (kept the edit to "add the check").
2. Unparsable version input to `compare-versions`/`at-least?` throws `IllegalArgumentException` (prompt is silent).
3. Two qualified versions with equal numbers compare by qualifier string (prompt is silent).
4. `disabled-violation` message text and extra data keys (`:dependency :found :op`) are my choice beyond the required `:fence :id :evidence`.
5. Second commit made at coordinator request: unparsable detected runtime versions never throw from the
   fence functions (floor/requirement/active-quirks/disabled return nil or []; `newer-than-tested` returns an
   unparsable warning); `compare-versions` returns exactly -1/0/1; added public `unparsable?`.
6. `gradlew` and `gradle/wrapper` copied in per README (gitignored, not committed).

## Open questions
- Should `plugin-1.13.4.jar` name vs `supported :freeplane :built-against "1.13.4"` be linked by a test? Not in scope.
- Answered: `fences.clj` IS in `plugin-1.13.4.jar` (verified by reviewer).
