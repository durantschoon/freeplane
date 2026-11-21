# Stage 07 REPORT: `freeplane.script` helper namespace

## Summary
Added `freeplane.script` (`freeplane_plugin_clojure/src/main/resources/freeplane/script.clj`) with all 26 functions of the
prompt table, wrapping `org.freeplane.api` only (plus `LogUtils`). Added `freeplane.script-test` (14 tests, 58 assertions)
and registered it in `ClojureTestRunner`. No BLOCKED rows.

## Files changed
- `freeplane_plugin_clojure/src/main/resources/freeplane/script.clj` (new)
- `freeplane_plugin_clojure/src/test/resources/freeplane/script_test.clj` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureTestRunner.java` (one list entry)
- `docs/stages/stage-07-REPORT.md` (new)

## Gate results
Base `ee5415e241`, measured before the change (cleanTest, then test; counts summed from test-result XML):

| | Baseline | Final |
|---|---|---|
| `:freeplane_plugin_clojure:test` JUnit (XML sum, includes `ClojureGenericScriptTest` 7) | 45 tests, 0 fail, 0 err | 45 tests, 0 fail, 0 err |
| `:freeplane_plugin_script:test` (XML sum) | 99 tests, 0 fail, 0 err | 99 tests, 0 fail, 0 err |
| Runner per-namespace line | `test-main: 3 tests; fences-test: 12 tests` | `test-main: 3 tests, 0 failures, 0 errors; freeplane.script.fences-test: 12 tests, 0 failures, 0 errors; freeplane.script-test: 14 tests, 0 failures, 0 errors` |

The JUnit count stays 45 because the new tests run inside the single `ClojureTestRunner` JUnit test. (My first
baseline sum of 38 missed `ClojureGenericScriptTest`; the 45 above is from re-summing the XML.)

- Compile gate (`compileJava`, `compileTestJava`): pass.
- Tests gate: pass (`BUILD SUCCESSFUL`).
- Deploy: `copyOSGiJars` lib dir holds `clojure-1.12.2.jar core.specs.alpha-0.4.74.jar plugin-1.13.4.jar spec.alpha-0.5.238.jar`;
  `jar tf plugin-1.13.4.jar` lists `freeplane/script.clj`, `freeplane/script/fences.clj`.
- Java 8 gate: no Java source changed beyond one string literal in the runner's list.
- Mutation check: temporarily removing type hints made `no-reflection-warnings-test` fail with reflection warnings; the file was restored.

## API notes
- `children`/`parent`/`subtree`: `NodeRO.getChildren`, `getParent`, `findAll` (breadth-first per its Javadoc).
- `details`/`note`: `NodeRO.getDetails()`/`getNote()` return null when absent; present values via `Convertible.getPlain`
  (plain text, formulas possibly evaluated, per the Javadoc). `getDetailsText`/`getNoteText` return HTML.
- `attrs`: `Attributes.size/getKey(i)/get(i)`, building a `PersistentArrayMap` via `apply array-map` (stays insertion-ordered
  past 8 entries). Repeated names keep the first value, consistent with `attr`, which uses `getFirst`.
- `set-attr!`: `Node.putAt(name, value)`; its Javadoc says null unsets.
- `add-child!`: `Node.createChild(Object)`, then `putAt` per attr.
- `move!`: `Node.moveTo(parent)` and `moveTo(parent, int)`. `delete!`: `Node.delete()`.
- `select!`: `Controller.select(Node)` or `select(Collection)`. `selected`/`selection`: `getSelected`/`getSelecteds`.
- `status!`: `Controller.setStatusInfo(String)`; returns c.
- Freeplane version string: `maj.mid.min` when `isFinal`, else `maj.mid.min-<type><num>` ("pre" if type is blank,
  num omitted when 0), which the fences parser sorts before the release.

## Deviations
1. `select!` and `status!` return `c` (the prompt specifies no return value); `delete!` returns nil (void `delete()`).
2. `compatibility` entries merge the whole `supported` map (`:floor :built-against :tested-up-to`) with `:found`,
   `:newer-than-tested`, `:active-quirks`.
3. `attrs` keeps the first value of a repeated attribute name (prompt silent).
4. Test 8 also covers `select!` with a non-vector seq, and test 10 adds an rc-vs-"1.13.3" case.

## Open questions
- `attrs` collapses repeated attribute names; a variant returning vectors of all values (`getAll`) may be wanted later.
- `details`/`note` use `Convertible.getPlain`, which can evaluate formulas; stub tests cannot confirm that live behaviour.
- Real-Freeplane behaviour of the helpers (undo, `putAt nil`) is not exercised: tests use `reify` stubs only.
