# Freeplane Clojure plugin

Runs Clojure (`.clj`) scripts from Freeplane's Scripts menu, through the same
script machinery as Groovy. The plugin supplies a JSR-223 engine on top of
`freeplane_plugin_script`, and a helper namespace, `freeplane.script`
(aliased `fp`), that wraps the public `org.freeplane.api` interfaces.

## Running a script

1. Put a `.clj` file in your user scripts directory. That is the `scripts`
   directory under the Freeplane user directory
   (`ScriptResources.getUserScriptsDir()` in `freeplane_plugin_script`; the
   `org.freeplane.scripts.user.dir` system property changes the name, default
   `scripts`). Subdirectories become submenus.
2. Select a node, then choose it from the Scripts menu (Tools > Scripts).
   Freeplane picks the engine from the `.clj` extension.
3. The script runs with the selected node(s) bound as `node`. Whatever the
   last form returns is the script result; `println` output goes to the script
   output.

Ready-made scripts are in `examples/` in this plugin's directory. They are not
installed automatically: copy the ones you want into your user scripts directory.

| Example | What it does |
|---|---|
| `examples/hello.clj` | Prints and returns a greeting using the node text. |
| `examples/basic.clj` | Lists children and attributes, adds a summary child. |
| `examples/etl_sum.clj` | Sums the `cost` attribute over the subtree into `total_cost`. |

## Bindings

Every script gets these names bound with `let`, so they are plain locals:

| Name | Meaning |
|---|---|
| `node` | The selected node (`org.freeplane.api.Node`). |
| `c` | The controller (`org.freeplane.api.Controller`). |
| `logger`, `ui`, `htmlUtils`, `textUtils`, `menuUtils`, `scriptUtils`, `config` | The standard Freeplane script helpers. |
| any other script-context variable | Bound when its name is a valid Clojure symbol; others are skipped. |

Scripts evaluate in the namespace `user`, with `clojure.core` referred and
`freeplane.script` aliased as `fp`. Nothing else is imported, so use fully
qualified names or `require` what you need.

## The `fp` API

`fp` takes the node or controller explicitly, returns eager vectors, and
returns the node from setters. Functions that change the map end in `!`.
All writes go through `org.freeplane.api`, so they are undoable.

| Function | Returns |
|---|---|
| `(fp/children n)` | Child nodes, vector. |
| `(fp/parent n)` | Parent node, or nil at the root. |
| `(fp/subtree n)` | `n` and all descendants, vector. |
| `(fp/find-nodes n pred)` | Nodes of the subtree for which `pred` is truthy. |
| `(fp/id n)` | Node id. |
| `(fp/text n)` | Raw text (plain or HTML). |
| `(fp/plain-text n)` | Text with HTML removed. |
| `(fp/details n)` | Details as plain text, or nil. |
| `(fp/note n)` | Note as plain text, or nil. |
| `(fp/attrs n)` | Attributes as an ordered `{String Object}` map; first value wins for repeated names. |
| `(fp/attr n k)` | First value of attribute `k`, or nil. |
| `(fp/node->map n)` | `{:id :text :attrs :details :note :children}`, recursively. |
| `(fp/set-text! n v)` | `n`. |
| `(fp/set-details! n s)` | `n`; nil removes the details. |
| `(fp/set-note! n s)` | `n`; nil removes the note. |
| `(fp/set-attr! n k v)` | `n`; nil `v` removes the attribute. |
| `(fp/add-child! n text)` / `(fp/add-child! n text attrs)` | The new child; `attrs` is a map set in order. |
| `(fp/delete! n)` | nil. |
| `(fp/move! n new-parent)` / `(fp/move! n new-parent pos)` | `n`. |
| `(fp/selected c)` | The selected node. |
| `(fp/selection c)` | Selected nodes, vector. |
| `(fp/select! c node-or-nodes)` | `c`. Not undoable. |
| `(fp/info & xs)`, `(fp/warn & xs)` | Log the joined `xs`; nil. |
| `(fp/status! c msg)` | `c`; shows `msg` in the status bar. |
| `(fp/require-clojure "1.12")` | nil, or throws when Clojure is older. |
| `(fp/require-freeplane c "1.13")` | nil, or throws when Freeplane is older. |
| `(fp/compatibility c)` | Diagnostics map, see below. |

Each function has a docstring: `(doc fp/attrs)`.

## Compatibility fences

The plugin tracks three numbers per dependency: the floor (minimum supported),
the version built against, and the version tested up to.

- `(fp/require-clojure "1.12")` and `(fp/require-freeplane c "1.13")` return nil
  when the running version is at least the needed one. Otherwise they throw an
  `ex-info` with `:fence :requirement`, plus the dependency, the version found
  and the version needed in its data (`ex-data`: `:dependency`, `:found`, `:needed`). Call one at the top of a script
  that relies on newer features, to fail early with a clear message.
- `(fp/compatibility c)` returns `{:clojure {...} :freeplane {...}}`. Each entry
  has `:found`, `:floor`, `:built-against`, `:tested-up-to`,
  `:newer-than-tested` (a warning string, or nil) and `:active-quirks`.
- Newer than tested only warns; it never blocks. Versions between the floor and
  tested-up-to run silently.
- Versions are compared numerically. A pre-release or release candidate sorts
  before its release, so `1.13.0-rc1` does not satisfy `"1.13"`.
- An unparsable *found* version (the running Clojure or Freeplane reports
  something that is not dotted numbers) is treated as unknown: the check passes
  rather than blocking, `:newer-than-tested` carries an "is unparsable;
  relying on capability probes" warning, and `:active-quirks` is empty. An
  unparsable *needed* string that you pass in throws
  `IllegalArgumentException` ("Unparsable version"); fix the string (use dotted
  numbers such as `"1.12"`).
- The engine itself refuses to run on a Clojure older than the floor and
  reports why in a `ScriptException`.

## Limitations

- Clojure cannot be used for formulas. Formulas are Groovy only.
- One Clojure runtime per script in the app: all Clojure scripts share the one
  `user` namespace and the one Clojure version on the plugin's classpath.
- `def` persists between runs of the same script, and across scripts that share
  `user`. A top-level `def` of a node keeps that node alive. Prefer `let`.
- Writes are undoable only when made through `fp` or the `org.freeplane.api`
  proxies. Anything reaching deeper into Freeplane's internals is not.
- `fp/select!` changes only the UI selection and is not undoable.
- Do not write to the map from other threads. Scripts run on the UI thread;
  `future`, `pmap` and agents must only compute, then write back from the script.

## Troubleshooting

- **Syntax error:** the error dialog and the script editor report the line of
  the offending form. A reader error points at the line where reading failed;
  a compile error (unresolved symbol, wrong `fp` name) points at the line of
  the form containing it. If the line looks off by a form, check for an
  unbalanced parenthesis above it.
- **`No such var: fp/...`:** the function does not exist in `freeplane.script`.
  See the table above, or `(keys (ns-publics 'freeplane.script))`.
- **Floor-check message:** when the Clojure on the classpath is older than the
  floor, the script does not start. The `ScriptException` names the version
  found and the version required. Remove the older Clojure jar from the
  Freeplane `plugins` directories, or update the Clojure plugin.
- **Stale state between runs:** a `def` from an earlier run is still visible.
  Restart Freeplane, or `(ns-unmap 'user 'the-name)`.
- **Logs:** `fp/info` and `fp/warn` write to the Freeplane log.

## Manual preflight checklist (running app)

1. Run `examples/hello.clj` from the Scripts menu and confirm its output.
2. Run `examples/basic.clj` and undo it with one Ctrl+Z; the summary child disappears.
3. Run two different scripts: `(def probe 1)` in one, `(println probe)` in the
   other. Confirm what you see matches the `def` limitation above, and that a
   `let` binding is never visible outside its script.
4. Put a syntax error on line 3 of a script (e.g. an extra `)`) and confirm the
   editor or error dialog points at line 3.
5. Run `(fp/compatibility c)` and read the result; `:found` should show the
   Clojure and Freeplane versions you are running.

## Building

```
gradle :freeplane_plugin_clojure:compileJava :freeplane_plugin_clojure:test
gradle :freeplane_plugin_clojure:copyOSGiJars
```

The deploy step puts the plugin and Clojure jars into
`BIN/plugins/org.freeplane.plugin.clojure/lib/`.
