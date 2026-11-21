# Stage 09 report

## Summary

- `ClojureUserNamespace`: setup is now a value (`Outcome`: ready `Namespace`, or failure message plus cause).
  `Holder.OUTCOME = setUp()` catches `Throwable`, so the static initializer cannot throw. Setup order: read
  `(clojure-version)`, `startupProblem` (floor check), `newer-than-tested` warn once, create `user`, refer
  `clojure.core`, require `freeplane.script`, alias `fp`. Failures are logged once with `LogUtils.warn`.
  `get()` throws `ScriptException` (message plus cause) on every call after a failure.
  Package-private static `startupProblem(String)` returns null or the floor-violation message.
- `ClojureCompiledScript.eval`: `*ns*`, plus `*out*`/`*err*` when the context writers are non-null, in one thread-binding
  push; both writers flushed in `finally` before the pop. A `ScriptException` from `get()` propagates unchanged.
- 9 tests added (2 `ClojureUserNamespaceTest`, 5 engine, 2 `GenericScript`).

## Files changed

See `git diff fbd62d61f6 --stat` in the commit. Production: `ClojureUserNamespace.java`, `ClojureCompiledScript.java`.
Tests: `ClojureUserNamespaceTest.java` (new), `ClojureScriptEngineTest.java`, `ClojureGenericScriptTest.java`.

## Gate results

Counts from test-result XML after `cleanTest`:

| | Baseline (`fbd62d61f6`) | Final |
|---|---|---|
| Clojure plugin | 45 tests, 0 fail/err/skip | 54 tests, 0 fail/err/skip |
| Script plugin | 99, 0 fail/err/skip | 99, 0 fail/err/skip |

Runner line: `Clojure tests passed: org.freeplane.plugin.clojure.test-main: 3 tests, 0 failures, 0 errors; freeplane.script.fences-test: 13 tests, 0 failures, 0 errors; freeplane.script-test: 14 tests, 0 failures, 0 errors;`

Compile: passes (implied by the test run). Deploy: `clojure-1.12.2.jar core.specs.alpha-0.4.74.jar plugin-1.13.4.jar spec.alpha-0.5.238.jar`.
`git grep -n "static {" -- freeplane_plugin_clojure/src/main`: no matches. Java 8 API: no Java 9+ API used in the diff.
Blocked conditions did not occur: `freeplane.script` loads under `GenericScript`, no existing test broke.

## Manual preflight checklist (real app)

1. A menu script using `fp` (e.g. `(fp/text node)`) runs without its own `require`.
2. `println` output appears in the script output pane; `*err*` output too.
3. Two different scripts: observe whether they get separate Clojure runtimes (Phase 3 decision: one runtime per script).
4. A syntax error jumps to its line in the editor.
5. Optional: run with a Clojure older than 1.12.0 on the plugin classpath and confirm the floor message appears per script, with Freeplane still starting.

## Deviations

1. `ClojureScriptEngineTest.userNamespaceIsSharedAndReferencesClojureCore` is an existing test; `get()` now declares
   `throws ScriptException`, so the method got `throws Exception` (signature only, assertions unchanged). Needed to compile.
2. Test 2 checks `fp/children` via `clojure.core/ns-resolve` with the symbol `fp/children`.
3. The floor-failure cause is a synthesized `IllegalStateException(problem)`, since `startupProblem` returns a String.
4. The failure path (setup throwing) and the floor-failure path in `get()` are not exercised by a test; the holder is one-shot per JVM.

## Open questions

- A test for the failure path would need an injectable setup step; not added (no speculative generality).
- `ClojureScriptEngineTest.fpAliasIsAvailableToScripts` mocks `org.freeplane.api.Node`; the clojure plugin test classpath already provides it.
