# Stage 04: Real compile cache and one-time namespace setup

Read `docs/stages/README.md` first (gates, guardrails, worktree setup). Roadmap item:
`TODO_clojure.md` A2 "Implement or drop `Compilable`" (decision: implement). This stage builds on
stage 03 (`ClojureScriptReader`, `ClojureFunctionForm`).

## Motivation (measured)

- `GenericScript.compileAndCache` (`freeplane_plugin_script/.../GenericScript.java:284`) calls
  `engine.compile(script)` once and then `compiledScript.eval(context)` on every run. Our
  `ClojureCompiledScript.eval` calls `engine.eval(script, context)`, so every run re-reads and
  re-compiles the whole script. Syntax errors also surface only at run time, never at compile time.
- `ClojureScriptEngine.eval` calls `clojure.core/refer 'clojure.core` into `user` on every evaluation
  (stage 02 open question).
- `eval(Reader, ...)` and `compile(Reader)` wrap every exception, including a `ScriptException` that
  already carries a line number, in a new `ScriptException`. The line number is lost (stage 03 open question).

## The change

1. New package-private final class `ClojureUserNamespace` with `static Namespace get()`. It creates `user`
   and refers `clojure.core` into it exactly once per class loader (initialization-on-demand holder or
   equivalent thread-safe lazy init), then returns the same `Namespace`.
2. `ClojureCompiledScript` (package-private static nested class of `ClojureScriptEngine`, or a top-level
   package-private class; your choice, report which):
   - It is created by `compile(String)`, which calls `ClojureScriptReader.readForms` **at compile time**,
     so syntax errors throw from `compile`.
   - `eval(ScriptContext)` collects bindings as now, computes `bindableNames`, and looks up a compiled `IFn`
     for that exact name list. On a miss it builds `ClojureFunctionForm.of(forms, names)`, evals it with
     `*ns*` bound to `ClojureUserNamespace.get()`, and stores it. Then it invokes the `IFn` with the map.
     The lookup and store must be thread-safe, and two threads must never compile the same name list twice.
   - A package-private `int compiledFunctionCount()` exposes the cache size to tests.
   - `CompilerException` is mapped to a `ScriptException` with its line, as in stage 03.
3. `ClojureScriptEngine.eval(String, ScriptContext)` becomes `compile(script).eval(context)`. There is one
   evaluation path.
4. `eval(Reader, ...)` and `compile(Reader)`: rethrow a `ScriptException` unchanged; wrap only other exceptions.

## Allowed files

- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptEngine.java`
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureUserNamespace.java` (new)
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureCompiledScript.java` (new, only if top-level)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptEngineTest.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureCompiledScriptTest.java` (new)
- `docs/stages/stage-04-REPORT.md` (new)

## Tests

`ClojureCompiledScriptTest`:
1. `compile("(+ 1")` throws `ScriptException` from `compile` itself, with a line number.
2. Evaluating the same compiled script twice with the same binding names gives `compiledFunctionCount() == 1`,
   and both results are correct.
3. Adding a new binding name between evaluations gives count 2, and the new name is visible.
4. Pass an atom through a binding named `probe`. `compile("(swap! probe inc)")` leaves the atom at 0
   (compiling must not run the body), and three evaluations leave it at 3.
5. 8 threads evaluate one compiled script concurrently with the same bindings. All results are correct
   and `compiledFunctionCount() == 1`.
6. `CompilerException` from an undefined symbol on line 2 gives `getLineNumber() == 2`.

`ClojureScriptEngineTest`:
7. `eval(new StringReader("1\n2\n)"), context)` throws a `ScriptException` with `getLineNumber() == 3`.
8. `compile(new StringReader("(+ 1"))` throws a `ScriptException` with a line number.
9. `ClojureUserNamespace.get()` returns the same instance on repeated calls, and `user` resolves `inc`
   to `clojure.core/inc`.
10. All existing engine tests still pass unchanged.

## Definition of Done

- Compile and Tests gates pass; script plugin still 99/99; Clojure plugin total test count is reported.
- `git grep -n '"refer"' -- freeplane_plugin_clojure/src/main` shows exactly one call site, in `ClojureUserNamespace`.
- Java 8 API gate clean (no `computeIfAbsent` lambdas are needed, but Java 8 lambdas are fine); no comments
  or Javadoc in new production Java.

## Commit

One commit on branch `stage-04`, message exactly:

`Compile Clojure scripts once per binding set and set up user namespace once`

Body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-04-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results (commands + pass counts); Deviations; Open questions.

## Blocked protocol

If the cache cannot be made thread-safe without leaving the allow-list, or a test cannot pass without
weakening it: stop, commit nothing, and write `docs/stages/stage-04-REPORT.md` with a `BLOCKED` section
(what you tried, exact output, the decision you need). Return that text as your final message.
