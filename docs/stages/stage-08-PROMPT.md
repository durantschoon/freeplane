# Stage 08: Pass script-context variables in GenericScript, and test Clojure end to end

Read `docs/stages/README.md` first (gates, guardrails, worktree setup). Roadmap item: `TODO_clojure.md` A4 "Add Java
integration test via `GenericScript`". This stage makes **two commits**. The first touches upstream code only, so
maintainers can take or drop it separately (decided by the human, 2026-11-21).

## Motivation (measured)

- Groovy scripts receive every variable bound in Freeplane's `ScriptContext` (`getBoundVariables()`), then `c` and
  `node`. The upstream 1.13 `args` variable arrives this way (`freeplane_plugin_script/.../FreeplaneScriptBaseClass.java:309-318`).
  `GenericScript.createBinding` (`GenericScript.java:264-270`) binds only `c`, `node` and the static properties, so
  JSR-223 languages, Clojure included, never see them.
- No test runs Clojure through `GenericScript`, the path Freeplane actually uses. `ScriptingEngineTest`
  (`freeplane_plugin_script/src/test/java/.../ScriptingEngineTest.java`) shows how to run scripts headless: it mocks
  `ResourceController` with `mockStatic`, uses its nested `TestResourceController`, and builds a real `NodeModel`.
- A design review found each `GenericScript` gets its own `ScriptClassLoader`. In the app that probably means one
  Clojure runtime per script. Measure what the test environment does; do not change it.

## Commit A: upstream fix

`GenericScript.createBinding` puts the `ScriptContext`'s bound variables into the bindings first (when the context is
non-null), then `c`, `node` and the static properties as now. That matches Groovy's precedence: `c` and `node` win.

Allowed in commit A: `freeplane_plugin_script/src/main/java/org/freeplane/plugin/script/GenericScript.java` only.
Message exactly: `Pass script context variables to JSR-223 script engines like Groovy scripts`.
The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Commit B: Clojure end-to-end test

Add `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/script/ClojureGenericScriptTest.java`, in package
`org.freeplane.plugin.script` so it can call package-private `ScriptResources.setClasspath`. Copy the minimal
`TestResourceController` it needs into this class. Do not depend on the script plugin's test classes.

Create scripts with `new GenericScript(source, "clj", ScriptingPermissions.getPermissiveScriptingPermissions())` and
execute them on a real `NodeModel` with text `"node text"`.

Tests:
1. `(+ 1 2)` returns `3L`.
2. `(.getText node)` returns `"node text"`. This shows the node proxy is bound.
3. `c` and `textUtils` are non-nil (`(and (some? c) (some? textUtils))`).
4. A variable bound in the `ScriptContext` (use the real API to bind one, e.g. `args` to a string) is visible as that
   symbol. This depends on commit A.
5. `"1\n2\n)"` raises the exception `GenericScript` raises, and the error handler's `gotoLine` receives 3.
6. A `def` in one `GenericScript` instance is not visible in a second instance's script. Assert whichever behaviour you
   observe, and record in the report whether the two share a Clojure runtime (compare
   `(System/identityHashCode (class clojure.lang.RT))` or similar) and why.

Allowed in commit B: the new test class, `freeplane_plugin_clojure/build.gradle` (test dependencies only, and only if
needed; say why), and `docs/stages/stage-08-REPORT.md`.
Message exactly: `Test Clojure scripts end to end through GenericScript`.
The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

Stage 06 runs in parallel and owns `ClojureTestRunner.java`, `test_main.clj` and `src/{main,test}/resources/freeplane/`.

## Definition of Done

- After each commit, the Compile and Tests gates pass. Report the counts from the XML after each, with the script
  plugin still at 99/99 after commit A.
- Java 8 API gate is clean.

## Report (`docs/stages/stage-08-REPORT.md`, in commit B)

Sections: Summary; Files changed (per commit); Gate results; Runtime-sharing observation (test 6); Deviations;
Open questions.

## Blocked protocol

If `GenericScript` cannot find the Clojure engine in a headless test, or proxies need a running controller for these
calls: stop. Commit A may stay if its gates pass. Write the report with a `BLOCKED` section (exact output, what you
tried, the decision you need) and return that text as your final message.
