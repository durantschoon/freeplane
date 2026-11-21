# Stage 09: Engine wiring for `fp`, script output, and the Clojure floor check

Read `docs/stages/README.md` first (gates, guardrails, worktree setup), plus the "Compatibility fences" and
"Phase 3 decisions" sections of `TODO_clojure.md`. This stage builds on stages 06-08. It is the riskiest stage of
Phase 3: if the helper fails to load, every Clojure script fails. So a failure must become a clear
`ScriptException` and never an `Error` or a broken engine.

## Motivation (measured)

- Scripts can't use an alias they `require` themselves (stage 07 prompt), so `fp` must exist in `user` before compile.
- `ClojureUserNamespace.Holder` (`ClojureUserNamespace.java`) does one-time setup in a static initializer. If
  that throws, the JVM gives `ExceptionInInitializerError` once and `NoClassDefFoundError` on every later call,
  which is unreadable for users.
- `GenericScript` sets the context's writer to the script output stream (`GenericScript.java:255-261`), but the engine
  never binds `clojure.core/*out*`. So `println` probably bypasses Freeplane's script output.
- Nothing checks the running Clojure version (`fences/floor-violation` exists but is not called).

## The change

1. `ClojureUserNamespace`:
   - One-time setup:
     - create `user`;
     - refer `clojure.core`;
     - `require` `freeplane.script`;
     - alias it as `fp` in `user`;
     - check the floor.
   - Before requiring `freeplane.script`, call `freeplane.script.fences/floor-violation` with `:clojure` and this
     runtime's `(clojure-version)`. Also log `newer-than-tested` via `LogUtils.warn` once, when non-nil.
   - Make the setup's outcome a value, held by the holder: either the ready `Namespace` or the failure (message plus
     cause). The static initializer must never throw.
   - `get()` returns the namespace, or throws `ScriptException` with the failure message (cause attached) on every
     call. On failure, log once with `LogUtils.warn`.
   - Expose the floor decision for tests as a package-private static `String startupProblem(String clojureVersion)`.
     It returns null or the floor-violation message.
2. `ClojureCompiledScript.eval`: also bind `clojure.core/*out*` to `context.getWriter()` and `*err*` to
   `context.getErrorWriter()`, only when non-null, in the same thread-binding push as `*ns*`. Flush both after
   invoking, in `finally`.
3. Engine callers of `ClojureUserNamespace.get()` let its `ScriptException` propagate unchanged.

## Allowed files

- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureUserNamespace.java`
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureCompiledScript.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureScriptEngineTest.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureUserNamespaceTest.java` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/script/ClojureGenericScriptTest.java` (add tests only)
- `docs/stages/stage-09-REPORT.md` (new)

## Tests

`ClojureUserNamespaceTest`:
1. `startupProblem("1.11.4")` returns the exact floor-violation message. `startupProblem("1.12.2")` returns null.
   `startupProblem("weird-build")` returns null.
2. `get()` returns the same namespace twice, and `(resolve 'fp/children)` in it is non-nil.

`ClojureScriptEngineTest`:
3. `(fp/text node)` returns `"t"` when `node` is a Mockito mock of `org.freeplane.api.Node` with `getText()` → `"t"`.
4. `(println "hello")` writes `hello` plus a newline to a `StringWriter` set as the context writer.
5. `(binding [*out* *err*] (println "oops"))` writes to the error writer.
6. A script that returns before printing still flushes what it printed (`(print "a") 1` gives writer `"a"`).
7. A script that defines its own `children` var (`(def children 1) children`) still works, and `fp/children`
   still resolves. This shows no `:refer` leaked into `user`.

`ClojureGenericScriptTest`:
8. Through `GenericScript`, `(fp/text node)` returns `"node text"`.
9. Through `GenericScript`, `(println "out")` reaches the `PrintStream` passed to `execute`.

## Definition of Done

- Compile and Tests gates pass, with the script plugin at 99/99. Report the XML counts and the runner line.
- `git grep -n "static {" -- freeplane_plugin_clojure/src/main` shows no static block that can throw.
- Java 8 API gate is clean, and new production Java has no comments.
- Preflight note in the report: list the manual checks a human should run in the real app. At least:
  - a menu script using `fp`;
  - `println` output;
  - two different scripts, to observe separate runtimes;
  - a syntax error jumping to its line.

## Commit

One commit on branch `stage-09`, message exactly:

`Load fp helper, bind script output, and check Clojure floor in engine`

The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-09-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results; Manual preflight checklist; Deviations; Open questions.

## Blocked protocol

If `require` cannot find `freeplane.script` under `GenericScript`'s class loader, or output binding breaks an
existing test: stop, commit nothing, and write the report with a `BLOCKED` section (exact output, what you tried,
the decision you need). Return that text as your final message.
