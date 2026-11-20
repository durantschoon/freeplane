# Stage 02: Testable Clojure script source and correct bindings

Read `docs/stages/README.md` first (gates, guardrails). Roadmap items: `TODO_clojure.md` A0
"Fix binding mismatch" and "Resolve duplicate execution paths" (choose JSR-223).

## Motivation (measured)

- `GenericScript.createBinding` (`freeplane_plugin_script/.../GenericScript.java:264`) puts `c`,
  `node`, and every entry of `ScriptingConfiguration.getStaticProperties()` (`logger`, `ui`,
  `htmlUtils`, `textUtils`, `menuUtils`, `scriptUtils`, `config`, ...) into ENGINE_SCOPE.
  `ClojureScriptEngine.eval` reads only `node` and `script-context`, so `c` and all helpers are invisible
  to Clojure scripts and `script-context` is always nil.
- The engine wraps code as `"(fn [node script-context] " + script + ")"`. A script whose last line is
  a `;` comment eats the closing paren and fails to read.
- Forms are evaluated with `*ns*` at its root value, `clojure.core`, so a script's `def` interns
  into `clojure.core`.
- `ClojureScript.java` (an `IScript`) is referenced nowhere (`git grep -n "ClojureScript\b"`),
  duplicates the engine with different semantics, and uses Java 11 `Files.readString`.

## The change

1. New package-private final class `ClojureScriptSource` in `org.freeplane.plugin.clojure`, pure
   (no Clojure runtime calls, no I/O):
   - `static List<String> bindableNames(Collection<String> names)`: keeps the names that match
     `[A-Za-z_*+!?<>=][A-Za-z0-9_*+!?<>=-]*`, in input order, without duplicates.
   - `static String wrap(String script, List<String> bindableNames)`: returns
     `(fn [bindings] (let [n1 (get bindings "n1") n2 (get bindings "n2") ...]` + `"\n"` + script + `"\n"` + `))`.
     The newlines are required so a trailing line comment cannot swallow the closing parens.
     With no names, use an empty `let` vector.
2. Rewrite `ClojureScriptEngine.eval(String, ScriptContext)`:
   - Collect bindings from GLOBAL_SCOPE, then ENGINE_SCOPE (engine scope wins), into a
     `java.util.Map<String,Object>` (Clojure `get` works on it).
   - Wrap with `ClojureScriptSource`, read and eval in namespace `user`: create it if missing, refer
     `clojure.core` into it, and push a thread binding of `clojure.core/*ns*` to it around the
     read and eval (pop in `finally`). Keep `read-string` for now; safe reading is stage A2.
   - Invoke the resulting fn with the map and return the result.
   - Any `Exception` becomes `ScriptException` with the original as cause; keep the message.
   - Keep `eval(Reader, ...)`, `compile(...)` and `ClojureCompiledScript` behaviour
     (compile still evaluates on each `eval`; real caching is stage A2). Small helpers may be
     extracted inside this class.
3. Delete `ClojureScript.java`.

## Allowed files

- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptSource.java` (new)
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptEngine.java`
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScript.java` (delete)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptSourceTest.java` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptEngineTest.java` (new)
- `docs/stages/stage-02-REPORT.md` (new)

Stage 01 is running in parallel and owns `build.gradle`, `ClojureScriptEngineFactory.java`,
`test_main.clj` and `ClojureTestRunner.java`. Do not touch them. Your base still gets Clojure
through Rama; that is expected.

## Tests (JUnit 4 + AssertJ)

`ClojureScriptSourceTest`:
1. `bindableNames` keeps `node`, `c`, `textUtils`, `script-context`, `valid?`; drops `a.b`, `a/b`,
   `1x`, `""`, `"has space"`; keeps input order; removes duplicates.
2. `wrap` with `[node, c]` produces exactly the expected string (assert the whole string).
3. `wrap` with no names produces an empty `let` vector.
4. `wrap` puts the script between newlines (trailing-comment safety, checked on the string).

`ClojureScriptEngineTest` (real Clojure runtime, `new ClojureScriptEngine()`, `SimpleScriptContext`):
5. `(+ 1 2)` returns `3L`.
6. ENGINE_SCOPE `node` and `c` are visible (`node` → the object put in; `(str c)` works).
7. A static-property-style name (`textUtils`) is visible.
8. GLOBAL_SCOPE binding is visible; ENGINE_SCOPE wins over GLOBAL_SCOPE for the same name.
9. A binding named `a.b` is ignored without error.
10. Script ending in `; trailing comment` evaluates.
11. Multi-form script returns the last form's value.
12. `(def stage02-probe 42) stage02-probe` returns `42L`; afterwards `user/stage02-probe` is bound
    and `clojure.core` has no `stage02-probe` mapping.
13. Unbalanced `(+ 1` throws `ScriptException` with a non-null cause.
14. `compile("(+ 40 2)").eval(context)` returns `42L`, and a compiled script sees a binding put in after compile.
15. `new ScriptEngineManager().getEngineByExtension("clj")` returns a `ClojureScriptEngine` (SPI registration works).

## Definition of Done

- Compile and Tests gates pass; script plugin still 99/99; all 15 tests present and passing.
- `git grep -n "ClojureScript\b" -- '*.java'` returns nothing.
- Java 8 API gate clean. No comments or Javadoc in new production Java.

## Commit

One commit on your stage branch, message exactly:

`Extract ClojureScriptSource and pass all script bindings into Clojure engine`

End the message body with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
Try `git push`; credential failure is expected and fine.

## Report (`docs/stages/stage-02-REPORT.md`, in the same commit)

Sections: Summary; Files changed; Gate results (commands + pass counts); Deviations (every
departure from this prompt, with the reason); Open questions (anything about Clojure/OSGi
behaviour you observed that later stages should know).

## Blocked protocol

If namespace handling, the SPI test or any enumerated test cannot be made to pass without
changing a file outside the allow-list or weakening the test: stop, commit nothing, and write
`docs/stages/stage-02-REPORT.md` with a `BLOCKED` section (what you tried, exact error output,
what decision you need). Return that text as your final message.
