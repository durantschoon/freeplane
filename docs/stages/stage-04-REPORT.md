# Stage 04 REPORT: Real compile cache and one-time namespace setup

## Summary
`compile(String)` now reads the script at compile time (syntax errors throw from `compile`). Evaluation caches one
compiled `IFn` per exact bindable-name list; `clojure.core` is referred into `user` once. `eval(String)` is
`compile(script).eval(context)`; `eval(Reader)` is `compile(reader).eval(context)`. `ScriptException`s from the
reader paths are no longer re-wrapped, so line numbers survive.

## Files changed
- New: `ClojureUserNamespace` (initialization-on-demand holder), `ClojureCompiledScript` (top-level package-private
  class, chosen over a nested class), `ClojureCompiledScriptTest`.
- Modified: `ClojureScriptEngine` (delegates; unused imports/constants removed), `ClojureScriptEngineTest` (+3 tests).
- `docs/stages/stage-04-REPORT.md`.

## Gate results
Commands: README preamble; `cleanTest` then `:freeplane_plugin_clojure:compileJava compileTestJava test` and
`:freeplane_plugin_script:test`. All BUILD SUCCESSFUL. Counts from test-result XML:
- Clojure plugin: 38 tests, 0 failures/errors (CompiledScript 6, Engine 20, Reader 6, FunctionForm 3, Activator 1,
  Source 1, Runner 1). Engine was 17 before this stage, so all 17 existing tests pass unchanged.
- Script plugin: 99 tests, 0 failures/errors.
- Deploy: `copyOSGiJars` OK; `BIN/plugins/org.freeplane.plugin.clojure/lib/` = clojure-1.12.2.jar,
  core.specs.alpha-0.4.74.jar, plugin-1.13.4.jar, spec.alpha-0.5.238.jar.
- `git grep -n --untracked '"refer"' -- freeplane_plugin_clojure/src/main`: one hit, `ClojureUserNamespace.java:14`.
- Java 8 API: none used; no comments/Javadoc in new production Java.
- Baseline not re-measured on the unmodified base (stage 03 base; its merged state per coordinator review was 28-29 tests).

## Deviations
1. Cache is a `HashMap` guarded by `synchronized`, with compilation done inside the lock. This guarantees one compile
   per name list but serializes first-time compiles of different name lists on one script. Invocation is outside the lock.
2. `eval(ScriptContext)` still binds `*ns*` around the invoke as well as the compile (prior behaviour), so scripts
   see `*ns*` = `user` at run time.
3. Prompt test 3 ("new name is visible") is implemented with a helper macro `stage04-has-local` defined in `user`
   via the engine, since an unbound new name would otherwise be a compile error. That macro stays interned in `user`
   for the JVM (as other tests' defs do).
4. Prompt test 9 is in `ClojureScriptEngineTest`; tests 1-6 in `ClojureCompiledScriptTest` (6 tests); `compile(Reader)`
   test and reader-eval test use fully qualified `java.io.StringReader`.

## Open questions
- Name-list cache is unbounded; a script evaluated with many distinct binding sets would grow it. Unlikely in Freeplane.
- A thread-bound `*ns*` other than `user` set by the caller is overridden; intended.
- Found while writing this report and fixed in the same commit: `ClojureUserNamespace.get()` is evaluated before `*ns*`
  is pushed, so the holder now binds `*ns*` to `user` itself around the one `refer` call (no extra tests; test 9 and
  the whole suite cover it).
