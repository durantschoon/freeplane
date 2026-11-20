# Stage 02 Report

## Summary
Added pure `ClojureScriptSource` (bindableNames, wrap). Rewrote `ClojureScriptEngine.eval(String, ScriptContext)`:
GLOBAL then ENGINE scope bindings merged into a map, script wrapped as `(fn [bindings] (let [...] \n script \n))`,
read and evaluated with `*ns*` thread-bound to `user` (clojure.core referred), `popThreadBindings` in `finally`,
any `Exception` wrapped in `ScriptException(cause)`. Deleted unused `ClojureScript.java`.

## Files changed
- A ClojureScriptSource.java, ClojureScriptSourceTest.java (4 tests), ClojureScriptEngineTest.java (11 tests)
- M ClojureScriptEngine.java
- D ClojureScript.java
- A docs/stages/stage-02-REPORT.md

## Gate results
Command: `gradlew :freeplane_plugin_clojure:compileJava :freeplane_plugin_clojure:compileTestJava :freeplane_plugin_clojure:test :freeplane_plugin_script:test` -> BUILD SUCCESSFUL.
- Clojure plugin: 15 new tests PASSED (4 source + 11 engine) plus ClojureTestRunner (2 clojure.test, 0 failures).
- Script plugin: 99 tests, 0 failures (summed from build/test-results XML).
- `git grep -n "ClojureScript\b" -- '*.java'`: no output.
- Java 8 gate: reviewed own diff; no Java 9+ API (lambdas in tests are Java 8). Deploy gate not run (not listed in the prompt's DoD).

## Deviations
1. Base: worktree started at 64d510ec0b; I ran `git checkout -B stage-02 cca3f79a62` as instructed.
2. `./gradlew` is not tracked in the repo; I invoked `/root/Repos/others/freeplane/gradlew -p .` from the worktree (the gradlew lives in the main checkout only, untracked).
3. Baseline was not re-measured on the unmodified base; README baseline (1 JUnit / 99 script) was relied on.
4. Test 13 uses `satisfies(e -> assertThat(e.getCause()).isNotNull())` instead of `hasCauseInstanceOf`, to assert non-null cause exactly.
5. Spec lists 15 tests; the Clojure test count is 4 + 11 = 15.

## Open questions
- `refer 'clojure.core` runs on every eval (iterates core publics); stage A2 caching could do it once.
- Names matching special forms or core fns (e.g. a binding named `if`, `map`) would shadow core fns in the script body; the name filter does not exclude them.
- `read-string` still honours `*read-eval*`; safe reading deferred to A2 per prompt.
