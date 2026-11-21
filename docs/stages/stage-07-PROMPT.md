# Stage 07: `freeplane.script` helper namespace

Read `docs/stages/README.md` first (gates, guardrails, worktree setup), plus the "Compatibility fences" and
"Phase 3 decisions" sections of `TODO_clojure.md`. This stage builds on stage 06 (`freeplane.script.fences`).
Stage 09 will auto-alias it as `fp` in `user`; this stage only creates and tests it.

## Motivation (measured)

A design review (Fable, 2026-11-21) ran Clojure 1.12.2 against this plugin and found that plain scripts cannot:
- use an alias they `require` in the same script, because all forms compile as one fn (`ClojureFunctionForm.java`);
- call the static helpers through their instances: `(.info logger "x")` fails, since `logger`, `ui`, `textUtils`
  and `menuUtils` are instances of static-method classes (`ScriptingConfiguration.java:192-198`);
- pass a Clojure fn where `org.freeplane.api.NodeCondition` is expected.

The tutorials also call methods that don't exist (`.children`, `.setAttr`, `.setStyle`). A small, stable helper
namespace fixes all of this.

## The change

New namespace `freeplane.script` in `freeplane_plugin_clojure/src/main/resources/freeplane/script.clj`.

Ground rules:
- It is a thin wrapper over `org.freeplane.api` interfaces only (plus `org.freeplane.core.util.LogUtils` for logging).
- Never call `getDelegate`, and never reference `org.freeplane.features`. Every write must go through the proxies so
  it stays undoable.
- It has `(set! *warn-on-reflection* true)` at the top and type hints, so loading it emits no reflection warnings.
- Every public var has a docstring.
- Collections are returned as eager vectors.
- Every function that changes the map ends in `!`; setters return the node.
- No public name collides with `clojure.core`.
- This namespace is accrete-only from now on: say so in the ns docstring.

Functions. `n` is an `org.freeplane.api.Node`, `c` an `org.freeplane.api.Controller`. Read the interfaces to
choose the right methods.

| Function | Semantics |
|---|---|
| `(children n)` | child nodes |
| `(parent n)` | parent node, or nil at the root |
| `(subtree n)` | n and all descendants, breadth-first (`findAll`) |
| `(find-nodes n pred)` | `(filterv pred (subtree n))` |
| `(id n)`, `(text n)`, `(plain-text n)` | id; raw text (Groovy parity); plain text |
| `(details n)`, `(note n)` | plain-text string, or nil when absent |
| `(attrs n)` | attributes as an insertion-ordered `{String Object}` map |
| `(attr n k)` | first value for k, or nil |
| `(node->map n)` | `{:id :text :attrs :details :note :children [ ...recursive... ]}` |
| `(set-text! n v)`, `(set-details! n s)`, `(set-note! n s)` | setters, returning n |
| `(set-attr! n k v)` | sets k (nil v removes k), returns n |
| `(add-child! n text)`, `(add-child! n text attrs)` | creates and returns the child; sets attrs in map order |
| `(delete! n)` | deletes n |
| `(move! n new-parent)`, `(move! n new-parent pos)` | moves n |
| `(selected c)`, `(selection c)` | selected node; vector of selected nodes |
| `(select! c node-or-nodes)` | selects; docstring says this is UI-only and not undoable |
| `(info & xs)`, `(warn & xs)` | log via `LogUtils`, joining xs with spaces |
| `(status! c msg)` | status-bar text |
| `(require-clojure needed)` | throws the `requirement-violation` ex-info if this runtime's `(clojure-version)` is older; returns nil |
| `(require-freeplane c needed)` | the same against `(.getFreeplaneVersion c)`. Build the version string so a non-final version keeps its type (use `isFinal`/`getType`) and sorts as a pre-release |
| `(compatibility c)` | diagnostics: `{:clojure {...} :freeplane {...}}`, each with `:found`, the `supported` numbers, `:newer-than-tested` (string or nil) and `:active-quirks` |

`ClojureTestRunner` adds `freeplane.script-test` to its namespace list.

## Allowed files

- `freeplane_plugin_clojure/src/main/resources/freeplane/script.clj` (new)
- `freeplane_plugin_clojure/src/test/resources/freeplane/script_test.clj` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureTestRunner.java` (namespace list only)
- `docs/stages/stage-07-REPORT.md` (new)

## Tests (`clojure.test`, in `script_test.clj`, using `reify` stubs of `org.freeplane.api` interfaces; atoms for state)

1. Read functions on a stub tree (root, two children, one grandchild): `children`, `parent` (nil at root),
   `subtree` order is breadth-first, `find-nodes`, `id`, `text`, `plain-text`.
2. `attrs` keeps insertion order. `attr` returns the first value, or nil.
3. `details` and `note` return nil when absent and a string when present.
4. `node->map` on the stub tree equals the expected nested map exactly.
5. Each `!` setter calls the right interface method with the right arguments and returns n. `set-attr!` with nil removes.
6. `add-child!` with attrs creates the child, sets the attrs in order, and returns the child.
7. `delete!` and both arities of `move!` call the right methods.
8. `selected`, `selection`, `select!` (single and collection) and `status!` against a stub `Controller`.
9. `require-clojure`: nil for `"1.12"`. For `"99.0"` it throws an ex-info with data `:fence :requirement`.
10. `require-freeplane` with a stub `FreeplaneVersion`: final 1.13.4 meets `"1.13.4"`; a non-final 1.13.4 (type
    `"rc"`) does NOT meet `"1.13.4"`; 1.12.18 fails `"1.13"` with `:fence :requirement`.
11. `compatibility` returns both keys with `:found` filled in.
12. Loading the namespace fresh with `*warn-on-reflection*` true writes nothing about reflection to `*err*`.
13. The namespace source (via `clojure.java.io/resource`) contains neither `getDelegate` nor `org.freeplane.features`.
14. No public var name is also a public var of `clojure.core`.
15. Every public var has a docstring.

## Definition of Done

- Compile and Tests gates pass, with the script plugin still at 99/99. Report the XML counts and the runner's
  per-namespace line.
- The plugin jar contains `freeplane/script.clj` (check with `$JAVA_HOME/bin/jar tf`).
- Java 8 API gate is clean.

## Commit

One commit on branch `stage-07`, message exactly:

`Add freeplane.script helper namespace for Clojure scripts`

The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-07-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results; API notes (any interface method you chose and why); Deviations;
Open questions.

## Blocked protocol

If an `org.freeplane.api` method needed for a row doesn't exist or behaves differently from the table, do not
invent a substitute. Implement the rest and list the row under `BLOCKED` in the report with the evidence. If the
namespace can't load in tests without leaving the allow-list: stop, commit nothing, write the report with a
`BLOCKED` section, and return that text as your final message.
