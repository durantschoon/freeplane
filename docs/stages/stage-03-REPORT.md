# Stage 03 REPORT: Safe, complete reading of Clojure scripts

## Summary
Scripts are now read form by form with `*read-eval*` false, wrapped as Clojure data (no string
building), and reader/compiler errors surface as `ScriptException` with the script line.
`git grep read-string` over `freeplane_plugin_clojure/src/main` returns nothing.

## Files changed
New: `ClojureScriptReader`, `ClojureFunctionForm`, `ClojureScriptReaderTest`, `ClojureFunctionFormTest`.
Modified: `ClojureScriptSource` (`wrap` removed), `ClojureScriptEngine`, `ClojureScriptSourceTest` (wrap tests
removed), `ClojureScriptEngineTest` (3 tests added). Plus this report.

## Gate results
Baseline not re-measured; used README baseline after stages 01-02 (Clojure 16, script 99).
Final (JDK 25 preamble): compileJava, compileTestJava, `:freeplane_plugin_clojure:test`,
`:freeplane_plugin_script:test` all BUILD SUCCESSFUL.
- Clojure plugin: 24 JUnit tests (1 runner + 5 reader/engine... per class: 1, 5, 1, 3, 14), 0 failures.
- Script plugin: 99/99.
- Deploy: `BIN/plugins/org.freeplane.plugin.clojure/lib/` = clojure-1.12.2.jar, core.specs.alpha-0.4.74.jar,
  plugin-1.13.4.jar, spec.alpha-0.5.238.jar.
- Java 8 API: no Java 9+ API used in production code (tests use Java 8 lambdas only).

## Deviations
1. Prompt test 10 (`"1\n2\nstage03-undefined-symbol"` gives line 3) failed as specified: a bare symbol carries no
   line metadata and the compiler reported line 0 (the generated fn/let forms have none). Resolution, inside the
   allow-list: `ClojureScriptReader` attaches `:line` (line of the first non-whitespace, non-comment character)
   to every non-seq `IObj` top-level form lacking one (symbols, vectors, maps, sets); `ClojureFunctionForm` wraps
   such a form as `(do form)` carrying that `:line` meta, since the compiler only honours line meta on seqs.
   Lists keep reader-provided line meta and are not wrapped. Non-IObj forms (numbers, keywords, strings) are unwrapped.
2. A first attempt using `reader.getLineNumber()` after the read gave 4 (it is the post-read position), hence the
   pre-read whitespace/comment skip (`lineOfNextForm`).
3. Baseline not re-run; README numbers used.
4. Reader catches non-ReaderException failures and reports `reader.getLineNumber()` with cause kept.

5. Made at coordinator request (second commit): deviation 1 originally put `:line` on the user's own non-list form,
   which leaked `{:line N}` into returned values. Now the reader leaves forms untouched and returns `(do form)` with
   `:line` only on the `do` list for non-list IObj forms; `ClojureFunctionForm` just places forms. Tests added for
   null/preserved metadata. Deviation 1's description of `ClojureFunctionForm` wrapping no longer applies.

## Open questions
- Line is only exact for top-level forms; errors nested inside a multi-line non-list top-level form (e.g. a long
  vector literal) report the form's first line. Nested lists report their own reader lines.
- `ReaderException.line` and `CompilerException.getData()` `:clojure.error/line` behaved as assumed (Clojure 1.12.2).
- The wrapper is inside the fn, so script line numbers equal script lines (no offset).
- `ClojureScriptEngine.eval(Reader)` still wraps any exception (including ScriptException) in a new ScriptException,
  losing the line number; it was not in this stage's scope.

## Coordinator review

- The reviewer's count from the test XML at `66aa58064d` is 28 Clojure plugin tests (Reader 6, Engine 17,
  FunctionForm 3, Source 1, Runner 1), not the 24 stated under Gate results. After merging with stage 05: 29.
- Deviation 1 as finally merged: the reader returns non-list `IObj` forms as `(do form)`, with `:line` only on
  the `do` list (commit `66aa58064d`). An earlier version put `:line` on the user's form and leaked it into
  returned values; the coordinator found this and sent it back.
