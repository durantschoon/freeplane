# Stage 10 report

## Summary
Examples moved to `freeplane_plugin_clojure/examples/` and rewritten on `fp`; old resource examples and
`copyClojureScripts` removed; jsyntaxpane dependency and bundle import removed; `ExamplesCompileTest` added;
`README.md` user guide written (160 lines). Branch `stage-10`, base `166120be41`.

## Files changed
- new: `examples/{hello,basic,etl_sum}.clj`, `README.md`, `ExamplesCompileTest.java`, this report
- deleted: `src/main/resources/org/freeplane/plugin/clojure/clojure/{hello,basic,advanced}.clj`
- `build.gradle`: removed jsyntaxpane dependency and import, removed `copyClojureScripts` and its `build.dependsOn`,
  added `systemProperty 'clojure.examples.dir', file('examples').absolutePath` to `test`.

## Gate results (counts from XML after cleanTest)
| | Baseline (166120be41) | Final |
|---|---|---|
| Clojure plugin JUnit XML total | 45, 0 fail | 46, 0 fail (+ExamplesCompileTest) |
| Runner line | test-main 3, fences-test 13, script-test 14, 0 fail/err | same |
| Script plugin | 99/99 | 99/99 |
- Compile gate: BUILD SUCCESSFUL. Test gate: BUILD SUCCESSFUL.
- Deploy (after `:freeplane_plugin_clojure:clean copyOSGiJars`): `BIN/plugins/org.freeplane.plugin.clojure/lib/` =
  `clojure-1.12.2.jar core.specs.alpha-0.4.74.jar plugin-1.13.4.jar spec.alpha-0.5.238.jar`; `BIN/scripts` has 0 `.clj`;
  plugin jar has no `org/freeplane/plugin/clojure/clojure/` entries (checked with python zipfile; no `unzip` here).
- `git grep -n jsyntaxpane -- freeplane_plugin_clojure`: no output.
- Java 8 API: lambda in the test only; no Java 9+ API used.
- Negative probe: a temporary `examples/zz.clj` with `(fp/nope node)` made ExamplesCompileTest FAIL (then removed).

## Decisions on unspecified behaviour
- hello: empty-text node gives "Hello from Clojure, !". No children/attrs concerns.
- basic: no children and no attributes gives child "Summary: 0 children, 0 attributes", no listing lines.
- etl_sum: no `cost` anywhere gives total 0.0, and `total_cost` is set to 0.0. Numbers and numeric strings (trimmed) count;
  other values are skipped with `fp/warn` naming raw value, node id and text. The selected node's own `cost` counts.
  Total is a double. Output attribute name is `total_cost` (prompt did not name it).
- Test locates `examples/` through system property `clojure.examples.dir` set in `build.gradle` (falls back to `examples`).
- Test sets up the `fp` alias itself only if `user` lacks one.

## Deviations
1. etl_sum writes `total_cost`, not an attribute named in the prompt (none was named).
2. README's description of the floor-check message and of `*out*` binding is written to the prompt's description of
   stage 09; exact wording of stage 09's message is not quoted. Verify against stage 09 after merge.
3. README says unparsable *found* versions pass with a warning, unparsable *needed* throws IllegalArgumentException,
   per `fences.clj` (the prompt said only "what an unparsable version means").
4. A blank line was left removed in `build.gradle` after `bundleExports` to avoid a double blank.
5. A second commit corrected two README statements (script menus, default imports) at coordinator request.

## Open questions
- The README states the Scripts menu is under Tools > Scripts and the user directory is the `scripts` dir under
  `getFreeplaneUserDirectory()`; menu path not verified in a running app (manual checklist covers behaviour).
- README claims scripts run on the UI thread; not verified here.
- README "Stale state" tip uses `ns-unmap`; untested in the app.
- `fp/status!`, `fp/info` doc in README taken from script.clj docstrings; `fp` has no wrapper for `Controller`-level
  undo or for creating sibling nodes.
