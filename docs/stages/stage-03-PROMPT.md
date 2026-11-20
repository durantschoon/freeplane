# Stage 03: Safe, complete reading of Clojure scripts

Read `docs/stages/README.md` first (gates, guardrails, worktree setup). Roadmap item:
`TODO_clojure.md` A2 "Use safe reading instead of bare `read-string`".

## Motivation (measured)

- `ClojureScriptEngine.eval` builds `"(fn [bindings] (let [...]\n" + script + "\n))"` and passes it to
  `clojure.core/read-string`, which reads **one** form. A script with one closing paren too many,
  such as `(+ 1 2)) (def lost 1)`, closes the wrapper early; everything after it is silently dropped.
- `read-string` honours `*read-eval*` (default true), so `#=(...)` runs code at read time.
- `GenericScript.handleScriptRuntimeException` (`freeplane_plugin_script/.../GenericScript.java:308`)
  calls `errorHandler.gotoLine(e.getLineNumber())`. The engine throws `new ScriptException(e)`, whose
  line number is always -1, so the script editor never jumps to the error.

## The change

1. New package-private final class `ClojureScriptReader`:
   - `static List<Object> readForms(String script) throws ScriptException` reads every top-level form
     until EOF from a `clojure.lang.LineNumberingPushbackReader` over the string, with
     `clojure.core/*read-eval*` thread-bound to `false` (push/pop in `finally`). Empty or comment-only
     scripts give an empty list.
   - A reader failure becomes `ScriptException(message, null, line)`. `LispReader.ReaderException`
     has a public `line` field. Keep the original exception as cause (`initCause`).
2. New package-private final class `ClojureFunctionForm`:
   - `static Object of(List<Object> forms, List<String> bindableNames)` returns the Clojure data
     `(fn [bindings] (let [n1 (get bindings "n1") ...] form1 form2 ...))`, built with `clojure.lang`
     collections and symbols (`fn`, `let` and `get` as `clojure.core`-qualified symbols are fine).
     No string building, no reading.
3. `ClojureScriptSource`: delete `wrap`; keep `bindableNames`. Remove the `wrap` tests from
   `ClojureScriptSourceTest`.
4. `ClojureScriptEngine.eval(String, ScriptContext)`: `readForms`, then `ClojureFunctionForm.of`, then `eval`
   (still inside the `user` namespace binding), then invoke with the bindings map.
   - `Compiler.CompilerException` from `eval`: throw `ScriptException(message, null, line)`, where
     `line` comes from the exception's `:clojure.error/line` data (via `getData()`), or -1 if absent.
     Keep the cause.
   - Other exceptions: unchanged (`ScriptException` with cause).
   - Guardrail 3 is relaxed for this stage: `ClojureScriptReader` and `ClojureFunctionForm` may call the
     Clojure runtime, but must stay free of I/O and Freeplane classes.

## Allowed files

- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptReader.java` (new)
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureFunctionForm.java` (new)
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptSource.java`
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptEngine.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptSourceTest.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptEngineTest.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptReaderTest.java` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureFunctionFormTest.java` (new)
- `docs/stages/stage-03-REPORT.md` (new)

Stage 05 runs in parallel and owns `Activator.java`, `defaults.properties` and `preferences.xml`.

## Tests

`ClojureScriptReaderTest`:
1. `"1 (+ 1 2) :k"` gives three forms, in order.
2. `""` and `"; only a comment"` give empty lists.
3. A form on line 2 carries `:line 2` metadata.
4. `"#=(java.lang.System/setProperty \"stage03.readeval\" \"ran\")"` throws `ScriptException`, and the
   system property stays unset.
5. `"1\n2\n)"` throws `ScriptException` with `getLineNumber() == 3` and a non-null cause.

`ClojureFunctionFormTest`:
6. `of([(+ 1 2)], [node, c])` evaluates (via `clojure.core/eval`) to a fn; invoking it with a map
   `{"node" 1, "c" 2}` and body `(+ node c)` returns `3L`.
7. With no names and no forms, the fn returns nil.
8. The form's printed shape is `(clojure.core/fn [bindings] (clojure.core/let [...] ...))` or the
   unqualified equivalent: assert its first element is the `fn` symbol and the `let` vector pairs.

`ClojureScriptEngineTest` (keep all existing tests passing, unchanged except where noted):
9. `"(+ 1 2)) (def stage03-dropped 1)"` throws `ScriptException`, and `user/stage03-dropped` is not interned.
10. `"1\n2\nstage03-undefined-symbol"` throws `ScriptException` with `getLineNumber() == 3`.
11. An empty script evaluates to `null`.
12. Existing test 13 (unbalanced `(+ 1`) still passes.

## Definition of Done

- Compile and Tests gates pass; script plugin still 99/99; Clojure plugin total test count is reported.
- `git grep -n read-string -- freeplane_plugin_clojure/src/main` returns nothing.
- Java 8 API gate clean; no comments or Javadoc in new production Java.

## Commit

One commit on branch `stage-03`, message exactly:

`Read every Clojure script form with read-eval off and report error lines`

Body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-03-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results (commands + pass counts); Deviations; Open questions
(include anything about Clojure exception data or line metadata later stages should know).

## Blocked protocol

If `LispReader.ReaderException` or `CompilerException` do not expose line information as assumed,
or a test cannot pass without leaving the allow-list or weakening it: stop, commit nothing, and
write `docs/stages/stage-03-REPORT.md` with a `BLOCKED` section (what you tried, exact output,
the decision you need). Return that text as your final message.
