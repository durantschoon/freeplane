# Stage 10: Examples on `fp`, trimmed build, and the user guide

Read `docs/stages/README.md` first (gates, guardrails, worktree setup), plus the "Compatibility fences" and
"Phase 3 decisions" sections of `TODO_clojure.md`, and `freeplane_plugin_clojure/src/main/resources/freeplane/script.clj`.
Stage 09 runs in parallel. It makes the engine auto-alias `freeplane.script` as `fp` in `user`, bind `*out*`/`*err*` to
the script output, and refuse to run (with a clear `ScriptException`) on a Clojure older than the floor. Write the guide
to that behaviour.

## Motivation (measured)

- The three shipped examples (`src/main/resources/org/freeplane/plugin/clojure/clojure/{hello,basic,advanced}.clj`)
  use raw Java interop and top-level `def`. `def` persists across runs and retains nodes (Fable review). The
  `copyClojureScripts` task in `freeplane_plugin_clojure/build.gradle` copies them into `BIN/scripts`, so they appear
  in every user's Scripts menu.
- `freeplane_plugin_clojure/build.gradle` depends on `freeplane_plugin_jsyntaxpane` and imports its packages, but no
  plugin source uses it. `.clj` highlighting was dropped, since the script editor is hard-wired to `text/groovy`
  (`ScriptEditorPanel.java:89`).
- There is no user documentation for Clojure scripting in the fork.

## The change

1. Move the examples to `freeplane_plugin_clojure/examples/` (not packaged, not copied to `BIN`) and rewrite them:
   - Use `fp/...` only. No raw interop on `node` or `c` except where `fp` has no wrapper.
   - Use `let`, not top-level `def`.
   - Each example starts with a comment block saying what it does and which node it acts on.
   - Content:
     - `hello.clj`: prints and returns a greeting with `(fp/text node)`.
     - `basic.clj`: reads the selected node's children and attributes, and adds a summary child.
     - `etl_sum.clj`: sums a numeric attribute `cost` over `(fp/subtree node)`, using `fp/node->map` and pure
       transforms, then writes the total with `fp/set-attr!`.
2. Delete the old example resources and the now-empty resource directory.
3. `freeplane_plugin_clojure/build.gradle`:
   - Remove the `copyClojureScripts` task and its `dependsOn`.
   - Remove the `freeplane_plugin_jsyntaxpane` dependency, and `org.freeplane.plugin.jsyntaxpane.*` from `bundleImports`.
   - Change nothing else.
4. Add `ExamplesCompileTest` (JUnit). For each `examples/*.clj` file, read the forms with `ClojureScriptReader` and build
   `ClojureFunctionForm.of(forms, names)`, where names are `c node logger ui htmlUtils textUtils menuUtils scriptUtils config`.
   `eval` the form inside `user` with `fp` aliased (the test sets up the alias itself if stage 09 is not merged), and
   do **not** invoke it. This catches unresolved symbols and wrong `fp` names.
   - Locate `examples/` relative to the Gradle project dir: use a system property set in `build.gradle`'s `test`
     block, or `user.dir`, and say which.
   - Assert at least 3 examples were found.
5. `freeplane_plugin_clojure/README.md`, the user guide, under 250 lines:
   - what the plugin does;
   - how to run a `.clj` script (Scripts menu; where to put user scripts: find the real directory in
     `ScriptResources`/`ScriptingConfiguration` and cite it);
   - available bindings;
   - an `fp` API table;
   - compatibility fences: `fp/require-clojure`, `fp/require-freeplane` and `fp/compatibility`, what each tells you,
     and what an unparsable version means;
   - limitations:
     - no Clojure formulas (Groovy only);
     - one Clojure runtime per script in the app;
     - `def` persists between runs of the same script;
     - writes are undoable only through `fp`/proxies;
     - `select!` is not undoable;
     - don't write to the map from other threads;
   - troubleshooting: syntax error line numbers, the floor-check message;
   - a **manual preflight checklist** for the running app:
     1. run `examples/hello.clj` from the Scripts menu and see its output;
     2. run `basic.clj` and undo it with one Ctrl+Z;
     3. run two different scripts and confirm a `def` in one is not visible in the other;
     4. introduce a syntax error on line 3 and confirm the editor or error dialog points at line 3;
     5. run `(fp/compatibility c)` and read the result.

## Allowed files

- `freeplane_plugin_clojure/examples/{hello,basic,etl_sum}.clj` (new)
- `freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/clojure/*.clj` (delete)
- `freeplane_plugin_clojure/build.gradle` (only the edits listed above, plus a test system property if you choose that)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ExamplesCompileTest.java` (new)
- `freeplane_plugin_clojure/README.md` (new)
- `docs/stages/stage-10-REPORT.md` (new)

Stage 09 owns `ClojureUserNamespace.java`, `ClojureCompiledScript.java`, `ClojureScriptEngineTest.java`,
`ClojureUserNamespaceTest.java` and `ClojureGenericScriptTest.java`.

## Unspecified behaviour you must decide and disclose

For each example, state what happens when the node has no children or no `cost` attributes. Non-numeric `cost`
values are skipped with an `fp/warn`, not an exception.

## Definition of Done

- Compile, Tests and Deploy gates pass, with the script plugin at 99/99. Report the XML total **and** the runner's
  per-namespace line.
- After a clean deploy, `BIN/scripts/` contains no `.clj` from this plugin, and the plugin jar has no `org/freeplane/plugin/clojure/clojure/`.
- `git grep -n jsyntaxpane -- freeplane_plugin_clojure` returns nothing.
- Java 8 API gate is clean.

## Commit

One commit on branch `stage-10`, message exactly:

`Rewrite Clojure examples on fp, drop unused build pieces, and add user guide`

The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-10-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results; Decisions on unspecified behaviour; Deviations; Open questions.

## Blocked protocol

If removing the jsyntaxpane dependency breaks compilation or OSGi resolution of this bundle, or the examples can't
compile against `fp` because a needed wrapper is missing: stop, commit nothing, and write the report with a `BLOCKED`
section (exact output, the decision you need). Do not add wrappers to `script.clj`. Return that text as your final message.
