# Clojure & Rama Integration TODO

Tracking work to bring Clojure scripting and optional Rama support to upstream-ready state.

**Current state (2026-11-21):** Phases 1-3 done. `freeplane_plugin_clojure` depends only on
`org.clojure:clojure:1.12.2`, runs through `GenericScript` via JSR-223 with all script bindings
(`node`, `c`, `logger`, `ui`, ...), reads scripts safely, compiles once per binding set, auto-aliases the `fp` helper namespace, checks the Clojure floor at load, and has 56 passing tests (plus 30 `clojure.test` tests inside the runner). Stage history: `docs/stages/`. Clojure execution is
intended via JSR-223 (`ClojureScriptEngine` + SPI) through `GenericScript`. Rama is bundled
into the Clojure plugin as both runtime and demo (`rama_hello.clj`) — that coupling is wrong
for production. Activator, preferences, and several config properties are stubbed or unused.

**Branches:** Work lives on `clojure-plugin` (fork `durantschoon/freeplane`), based on
`upstream/1.13.x`. `1.12.x`/`1.13.x` track upstream unchanged. Personal branding is kept as
`private-extensions/patches/branding.patch`. Pre-cleanup history is tagged
`archive/1.12.x-pre-cleanup` and `archive/feature-clojure-integration`.

**Where to work:** all code changes are commits on `clojure-plugin` in this fork; that branch
becomes the upstream PR. `private-extensions/` (separate private repo, gitignored here) holds
only private material: branding patch, product notes, experimental scripts, `bb` helpers
(`bb status`, `bb brand`/`bb unbrand`, `bb test`, `bb build`, `bb run`).

**Build note:** a JRE-only Java breaks the Gradle toolchain (`does not provide ... JAVA_COMPILER`);
point `JAVA_HOME` at a full JDK and pass
`-Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=$JAVA_HOME`.

## Roadmap

| Phase | Scope | Sections |
|---|---|---|
| 0 — Cleanup | Branch off 1.13.x, drop stashes/old branches, branding out of tree | done |
| 1 — Correct Clojure plugin | Plain `org.clojure:clojure`, binding fix, single JSR-223 path, build metadata | A0, A1 — done (stages 01-02) |
| 2 — Safety and settings | Safe reading, compile cache decision, preferences + translations | A2 — done (stages 03-05) |
| 3 — Developer experience | Helper ns, proxy-API examples, `.clj` highlighting, GenericScript test, docs | A3, A4 — done (stages 06-10) |
| 4 — Upstream PR | Clojure plugin only, against `1.13.x` | — |
| 5 — Rama plugin | Separate optional plugin, managed runtime, module registry, facade | Part B |
| Later | nREPL, add-on JARs, editor actions | Part C |

Each phase: extract logic into testable classes first, test it, then wire it in (see `CLAUDE.md`).
Check fit with upstream 1.13.x scripting additions (MCP, script AI calls) during Phase 1.

**Compatibility fences** (formerly "version fences"; principle for every external dependency we don't control: Clojure, Freeplane's API,
Rama, other libraries, the JVM). Reviewed by Fable and renamed 2026-11-21.
0. *Three numbers per dependency*: floor (minimum supported), built-against, tested-up-to.
1. *Floor*: checked once at load, against the runtime that will execute the code. On failure, disable only
   ourselves, with a visible reason; never break host startup.
2. *Gate*:
   - Probe capabilities first, e.g. `(resolve 'clojure.core/parse-long)` or does the method exist; bind wrappers once.
   - Use version numbers only where people think in versions (`(fp/require-clojure "1.12")`,
     `(fp/require-freeplane c "1.13")`) or where behaviour can't be probed.
3. *Accrete-only on our side*: our API only grows, error data included. Deprecate with a one-time warning and
   never remove; a true break gets a new namespace (`...v2`).
4. *Known-version (quirks) table*: version *ranges* (`:introduced`, `:fixed-in`) per evidence-backed issue,
   listing operations to disable. Each entry has a test. A CI check fails on stale entries (fixed at or below the floor).
5. *Tested-up-to*: newer than tested means warn once, never block. Unknown versions inside the range run silently.
6. *Observable and overridable*: structured `ex-info` errors (`:fence :dependency :found :needed :evidence`),
   a diagnostics call, one log line per fence that fires, and a user override.

Parse versions numerically, and order pre-releases/SNAPSHOTs before their release. Freeplane's `compareTo`
ignores the release type; use `isFinal()`. Degrade only when the result would be identical; otherwise fail, at
load rather than mid-render. Don't shadow or re-export what we can't control.

**Phase 3 decisions (human, 2026-11-21):**
- Keep one Clojure runtime per script, and measure it.
- Fix `GenericScript` to pass script-context variables (`args` etc.), as its own commit.
- Drop `.clj` highlighting, the jsyntaxpane dependency, and copying examples into the Scripts menu.
- The helper namespace is `freeplane.script`, aliased `fp`. It takes explicit `node`/`c`, and writes only through
  `org.freeplane.api` (never `getDelegate()`).
- Clojure cannot be used for formulas; they are Groovy-only.

**Architecture goal:** three layers — Clojure runtime, optional Rama runtime, optional
mind-map bridge — not one plugin doing everything.

```
freeplane_plugin_script  →  GenericScript (.clj)
freeplane_plugin_clojure →  Clojure + JSR-223 only
freeplane_plugin_rama    →  Rama lifecycle + module registry + script facade (optional)
```

---

## Part A — Clojure plugin (`freeplane_plugin_clojure`)

### A0 — Correctness and integration

- [x] **Fix binding mismatch in `ClojureScriptEngine`** (stage 02: all ENGINE/GLOBAL bindings that are valid symbols are let-bound; eval in ns `user`)
  - `GenericScript` binds `node` and `c` (controller proxy), not `script-context`.
  - Engine should read `c` first, fall back to `script-context` for compatibility.
  - File: `freeplane_plugin_clojure/.../ClojureScriptEngine.java`

- [x] **Resolve duplicate execution paths** (stage 02: JSR-223 only, `ClojureScript.java` deleted)
  - Choose one: JSR-223 via `GenericScript` (recommended) OR dedicated `ClojureScript` in `ScriptingEngine.compile()`.
  - If JSR-223 only: delete `ClojureScript.java` to avoid two semantics.
  - If dedicated path: wire `.clj` in `ScriptingEngine` like Groovy and remove redundant engine duplication.

- [x] **Clean stale JARs in plugin lib directory** (stage 01: `copyOSGiJars.doFirst` deletes the lib dir; runs only when the task is not up to date)
  - `copyOSGiJars` appends without cleaning; old `clojure-1.10.3.jar` can coexist with Rama's `clojure-1.12.2.jar`.
  - Clean `BIN/plugins/org.freeplane.plugin.*/lib/` before copy, or delete plugin lib dir on rebuild.

- [x] **Revert local/personal changes before upstream**
  - Branding moved to `private-extensions/patches/branding.patch` (`bb brand` / `bb unbrand`).

### A1 — Dependencies and build metadata

- [x] **Use explicit Clojure dependency (decouple from Rama)** (stage 01: `org.clojure:clojure:1.12.2`; `rama_hello.clj` kept in private-extensions)
  - Replace `com.rpl:rama` in this module with `org.clojure:clojure` (1.11.x conservative or 1.12.x current).
  - Remove Rama-specific Maven repos from `freeplane_plugin_clojure/build.gradle`.
  - Remove `rama_hello.clj` from shipped scripts; relocate to `freeplane_plugin_rama` examples.

- [x] **Fix stale `bundleExports`** (stage 01)
  - Remove export of deleted package `org.freeplane.plugin.clojure.proxy`.
  - File: `freeplane_plugin_clojure/build.gradle`

- [x] **Fix broken javadoc task sources** (stage 01: javadoc task removed until there is an API to document)
  - References deleted `ClojureScriptBaseClass.java` and `ClojureStaticImports.java`.
  - Point at existing API surface or disable until proxy/helpers exist.

- [x] **Align reported Clojure version** (stage 01: `CLOJURE_VERSION`, checked against `(clojure-version)` in tests)
  - `ClojureScriptEngineFactory.getLanguageVersion()` must match the pinned dependency.
  - Share version constant with tests.

- [x] **Remove committed `.class` files if any reappear** (none on `clojure-plugin`)
  - Feature branch briefly had `.class` files under `src/main/java/`; keep source-only.

### A2 — Security, caching, and configuration

- [x] **Use safe reading instead of bare `read-string`** (stage 03: every form read with `*read-eval*` false; stray parens are errors; error lines reach the script editor)
  - Wrapped script eval uses `clojure.core/read-string` (reader macros, injection risk).
  - Prefer `load-string` with `*read-eval*` false or `clojure.tools.reader` with `:read-eval false`.

- [x] **Implement or drop `Compilable`** (stage 04: implemented; compiled once per binding-name set, syntax errors at compile time; `user` ns set up once)
  - `ClojureCompiledScript.eval()` re-runs full eval; no real compile cache.
  - Either compile once to `IFn` and reuse, or remove `Compilable` and document interpret-on-run.

- [x] **Wire preferences and defaults** (stage 05: removed; no key was read, and each duplicated `GenericScript` or described ClojureScript; Activator emptied)
  - `defaults.properties` and `preferences.xml` exist but nothing reads them.
  - `Activator` has TODOs; restore minimal `ClojurePluginRegistration`:
    - load defaults into option panel (mirror `ScriptingRegistration.addPropertiesToOptionPanel()`)
    - honor or remove: `clojure_script_enabled`, `clojure_script_cache_size`, `clojure_script_timeout`, `clojure_script_security_enabled`

- [x] **Add translation keys** (not needed after stage 05)
  - `plugins/Clojure/...` keys in `preferences.xml` need entries in `Resources_en.properties` (and format_translation).

### A2.1 — Follow-ups found in Phases 1-2

- [ ] **Stale jars still reach the plugin lib dir.** The root `copyOSGiJars` copies `build/libs/*.jar` except the
  current version, so an old `freeplane_plugin_clojure-<old>.jar` left in `build/libs` is redeployed (seen
  2026-11-20; fixed locally with `:freeplane_plugin_clojure:clean`). The plugin's `doFirst` delete also skips when
  the task is up to date. The same exclude affects every plugin (`BIN/plugins/org.freeplane.plugin.script/lib/` has
  a stale `plugin-1.12.15.jar`); consider raising upstream instead of patching it in this plugin.
- [ ] Line numbers are exact for top-level forms only; an error deep inside a multi-line form reports the form's line.
- [ ] The per-script cache of compiled functions is unbounded (one entry per distinct binding-name set; small in practice).

### A3.1 — Follow-ups found in Phase 3

- [ ] **Manual preflight in the running app** (no GUI here). Checklist in `freeplane_plugin_clojure/README.md`:
  run a script using `fp`, see `println` output, undo a script with one Ctrl+Z, confirm two scripts don't share `def`s,
  check that a syntax error points at its line, and read `(fp/compatibility c)`. Record results here.
- [ ] **Measure per-script runtimes in the app.** Each loaded script gets its own Clojure runtime (`ScriptClassLoader`
  parent can't see Clojure). Startup was ~110-135 ms in a warm JVM (Fable); memory not yet measured.
- [ ] **`@ExecutionModes` menu placement for `.clj`.** `ScriptMetaData` regex-matches whole file text regardless of
  language, so a `;;` comment should work. Unverified in the app.
- [ ] **No same-script `import`.** All forms compile as one fn, so `(import ...)` can't affect later symbols. The guide
  says to use fully qualified names. A future engine change could hoist leading `ns`/`import`/`require` forms.
- [ ] **Private tutorials are wrong.** `private-extensions/docs/CLOJURE_TUTORIAL.md` (Clojure formulas, `.setStyle`) and
  `CLOJURE_ETL_TUTORIAL.md` (`.children`, `.setAttr`) predate `fp`. Rewrite them on `fp` and the README.
- [ ] **How users get the examples.** They are no longer copied into the Scripts menu; decide whether to ship them in
  docs, an add-on, or a "copy examples" action.
- [ ] `attrs` keeps the first value for repeated attribute names; add an all-values variant if needed.

### A3 — API, examples, and developer experience

- [x] **Fix example scripts to use proxy API** (stage 10: `examples/{hello,basic,etl_sum}.clj` on `fp`, compiled by `ExamplesCompileTest`; no longer copied into the Scripts menu)
  - Prefer `c` controller and proxy methods over raw Java interop (`.getChildren`, `.getText` on node).
  - Files: `clojure/basic.clj`, `clojure/advanced.clj`, `clojure/hello.clj`

- [x] **Ship helper namespace** (stages 07, 09: `freeplane.script`, auto-aliased `fp`; accrete-only; fences in `freeplane.script.fences`, stage 06)
  - Add `freeplane.clojure` (or `freeplane.script`) ns with common requires and idiomatic wrappers.
  - Auto-require in engine wrapper so scripts get same globals as Groovy (`logger`, `ui`, `textUtils`, etc.).

- [x] **Syntax highlighting for `.clj`** (dropped: script editor is hard-wired to `text/groovy`; jsyntaxpane dependency removed in stage 10)
  - Register with jsyntaxpane (already a plugin dependency).

- [x] **Document Clojure scripting** (stage 10: `freeplane_plugin_clojure/README.md`)
  - Short guide: bindings (`node`, `c`), menu discovery, `@ExecutionModes` convention, user script dirs.

### A4 — Clojure testing

- [x] **Update `ClojureTestRunner` after Rama decoupling** (stage 01: Rama tests replaced by a Rama-absent test)
  - Remove Rama load / version assertions from clojure plugin tests.
  - Test Clojure version against explicit dependency only.

- [x] **Add Java integration test via `GenericScript`** (stage 08: `ClojureGenericScriptTest`; plus upstream fixes for context variables and in-memory sources)
  - Execute minimal `(+ 1 2)` on `.clj` with mock node; no full Swing/OSGi if avoidable.

### A5 — Target Clojure module shape

```
freeplane_plugin_clojure/
  Activator.java
  ClojurePluginRegistration.java    # preferences + defaults only
  ClojureScriptEngine.java          # JSR-223, fixed bindings, safe read
  ClojureScriptEngineFactory.java
  META-INF/services/javax.script.ScriptEngineFactory
  resources/
    clojure/freeplane/script.clj    # helper ns
    examples/*.clj                  # not copied to BIN/scripts by default
  build.gradle                      # org.clojure:clojure only
```

Script execution stays in `freeplane_plugin_script` via `GenericScript`. The Clojure plugin
is a **language runtime + configuration**, not a second scripting framework.

---

## Part B — Rama plugin (`freeplane_plugin_rama`) — do Rama right

Rama is a **long-lived stream-processing runtime** (clusters, modules, depots, topologies).
Freeplane is a **desktop app with optional scripts**. Rama must be a **managed OSGi service**,
not `launch-ipc!` inside a menu script.

### Why the current demo is wrong

`rama_hello.clj` calls `(rama/launch-ipc! {:modules [MyModule]})` inside a script. Problems:

| Issue | Why it matters |
|---|---|
| Cluster per script run | Expensive, racy, leaks if script errors before close |
| `defmodule` in menu scripts | Modules belong in a registry, not ad hoc eval |
| No OSGi lifecycle | Cluster must start/stop with app or explicit user action |
| No security boundary | Rama runs arbitrary stream logic; must not inherit permissive defaults silently |
| 108MB+ on every install | Only Rama users should pay the cost |

**Rule:** user scripts never call `launch-ipc!` directly in production.

### B0 — Module setup

- [ ] **Create `freeplane_plugin_rama` subproject**
  - Add to `settings.gradle`.
  - `implementation project(':freeplane')`
  - `implementation project(':freeplane_plugin_script')`
  - `implementation project(':freeplane_plugin_clojure')`
  - `lib 'com.rpl:rama:1.3.0'` (pin version)
  - Maven repos: Maven Central + `https://nexus.redplanetlabs.com/repository/maven-public-releases`
  - OSGi: `Require-Bundle: org.freeplane.core`; import script + clojure packages

- [ ] **Remove Rama from `freeplane_plugin_clojure`**
  - Delete `rama` deps, repos, `rama_hello.clj`, Rama tests from clojure module.
  - Ensure exactly one Clojure version on classpath (explicit dep in clojure plugin; rama plugin uses same).

- [ ] **Clean plugin lib dir on build** (shared with Part A)

### B1 — Managed runtime service

- [ ] **Define `RamaRuntimeService` interface (Java)**
  ```java
  boolean isRunning();
  void startLocalCluster();
  void stopLocalCluster();
  void registerModule(Object moduleVar);   // or Class<?> / namespace name
  Object foreignDepot(String moduleName, String depotName);
  // optional: connectExternalCluster(URI or config path)
  ```

- [ ] **Implement `RamaRuntimeServiceImpl`**
  - Owns **one** IPC handle (`Closeable ipc`), not one per script.
  - Lazy start on first use OR when preference `rama_auto_start=true`.
  - Synchronized start/stop; idempotent.

- [ ] **Register OSGi service in `Activator`**
  - `context.registerService(RamaRuntimeService.class.getName(), impl, props)`
  - On `stop()`: call `stopLocalCluster()` and release resources.

- [ ] **Wire application lifecycle**
  - Register `ApplicationLifecycleListener.onApplicationStopped()` → stop cluster.
  - Mirror pattern in `ScriptingRegistration.registerInitScripts()`.

- [ ] **`RamaClojureBridge` (Java ↔ Clojure interop)**
  - Invoke `com.rpl.rama/launch-ipc!`, `foreign-depot`, `append!` via `clojure.lang.IFn`.
  - Keep Rama-specific Clojure calls out of user scripts.

### B2 — Module registry

Modules must not be defined inside one-off menu scripts.

- [ ] **Define module search paths**
  ```
  ~/.freeplane/rama/modules/           # user modules
  BIN/rama/modules/                    # shipped examples (optional)
  {addon}/modules/                     # script add-on private dirs
  ```

- [ ] **Module loader at cluster startup**
  - Scan paths for `.clj` namespaces or precompiled module JARs.
  - `require` each ns; collect `defmodule` vars.
  - Pass module set to single `launch-ipc!` call.

- [ ] **Ship example module** (relocate from `rama_hello.clj`)
  - `resources/modules/examples/hello_module.clj` with `defmodule` only.
  - Separate demo script calls facade, not `launch-ipc!`.

- [ ] **Document production path**
  - IPC + interpreted `defmodule` = **dev/demo only**.
  - Production Rama = precompiled module JARs (Rama's normal workflow); plan add-on packaging later.

### B3 — Script-facing facade

- [ ] **Implement `freeplane.rama.facade` namespace**
  - `(rama/append! module-name depot-name value)`
  - `(rama/query module-name query-spec ...)` (when needed)
  - Delegates to `RamaRuntimeService`; auto-starts cluster if permitted.

- [ ] **Expose `rama` binding in Clojure scripts when plugin present**
  - Option A: new `IScriptBindingContributor` hook in script plugin (cleanest).
  - Option B: Clojure engine looks up `RamaRuntimeService` OSGi service and adds binding.
  - If service absent: clear error ("install Rama plugin"), not silent failure.

- [ ] **Rewrite demo script**
  ```clojure
  ;; user script — no launch-ipc!
  (rama/append! "HelloModule" "*in-depot" (str "From node: " (.text node)))
  ```

### B4 — Connection modes and preferences

- [ ] **Add `defaults.properties` + `preferences.xml`**
  - `rama_mode` = `disabled` | `ipc` | `external` (default: `disabled`)
  - `rama_auto_start` = false
  - `rama_modules_path` = user path override
  - `rama_external_config` = path/URI for external cluster (Phase B+)

- [ ] **Register preferences via `RamaPluginRegistration`**
  - Mirror `ScriptingRegistration.addPropertiesToOptionPanel()`.
  - Add English translation keys (+ format_translation).

- [ ] **IPC mode (Phase A)**
  - Single in-process cluster in JVM.

- [ ] **External cluster mode (Phase B+)**
  - Connect via config; no `launch-ipc!`; foreign depots against remote cluster.

### B5 — Security and permissions

Rama is heavier than `node.setText`. Separate permission tier:

- [ ] **Define `RamaScriptPermission` enum**
  - `NONE` — default; Rama API unavailable
  - `USE_EXISTING` — append/query only on running cluster
  - `ADMIN` — start/stop cluster, register modules

- [ ] **Enforce in facade**
  - Menu scripts: `USE_EXISTING` max unless user opts in.
  - Init scripts / signed add-ons: configurable.
  - Do not expose raw `launch-ipc!` to untrusted scripts.

- [ ] **Integrate with `ScriptingPermissions` or parallel check**
  - Rama disabled by default in preferences.

### B6 — Optional mind-map bridge (product feature)

Only if goal is "maps drive Rama streams", not just "Rama callable from scripts":

- [ ] **Node attribute conventions** (e.g. `rama-depot`, `rama-module`)
- [ ] **Menu action: sync selection → depot**
- [ ] **Query results → child nodes** (topology output back into map)
- [ ] **Keep stream logic in Rama modules; UI wiring in Freeplane**

### B7 — Rama testing

- [ ] **Move Rama tests to `freeplane_plugin_rama`**
  - `ClojureTestRunner` + `clojure.test` for namespace load, version.
  - `RamaRuntimeServiceTest`: start/stop, no leak on double-start.

- [ ] **Integration test (optional, slow)**
  - IPC append → topology receives value.
  - Gate behind Gradle property: `-PramaTests`.

- [ ] **Remove Rama assertions from clojure plugin tests**

### B8 — Target Rama module shape

```
freeplane_plugin_rama/
  src/main/java/org/freeplane/plugin/rama/
    Activator.java
    RamaPluginRegistration.java
    RamaRuntimeService.java
    RamaRuntimeServiceImpl.java
    RamaClojureBridge.java
    RamaScriptPermission.java
  src/main/resources/org/freeplane/plugin/rama/
    defaults.properties
    preferences.xml
    modules/examples/hello_module.clj
    clojure/freeplane/rama/facade.clj
  src/test/java/...
    RamaRuntimeServiceTest.java
    ClojureTestRunner.java
  build.gradle
```

### B9 — Rama rollout phases

| Phase | Deliverable |
|---|---|
| **A — Infrastructure** | Separate plugin, `RamaRuntimeService`, start/stop lifecycle, example module, one menu script via facade |
| **B — Script API** | `rama` binding, preferences, security tier, external cluster config stub |
| **C — Map integration** | Node attributes, sync actions, query → nodes (if product goal) |
| **D — Production Rama** | Precompiled module JAR workflow, add-on packaging, real cluster docs |

---

## Part C — Other future work

- [ ] **nREPL server** for live Clojure development (dev-only, preference-gated)
- [ ] **Clojure add-on JAR support** via existing script classpath machinery
- [ ] **Dedicated Clojure script editor actions** only if JSR-223 + generic script editor is insufficient

---

## Done (already in tree)

- [x] Phase 0 cleanup: `clojure-plugin` branch on `upstream/1.13.x`; stashes and
  `feature/clojure-integration` removed (tagged `archive/*`); editor caches untracked

- [x] `freeplane_plugin_clojure` module in `settings.gradle`
- [x] OSGi plugin build (`pluginJar`, `copyOSGiJars`, bundle activator)
- [x] `ScriptingConfiguration.addClasspathForOsgiPlugins()` for SPI discovery
- [x] JSR-223 factory + SPI registration
- [x] `copyClojureScripts` → `BIN/scripts/`
- [x] `ClojureTestRunner` bridging JUnit to `clojure.test` (needs Rama decoupling)
- [x] `rama_hello.clj` IPC spike proving classpath + basic append works (to be refactored, not kept as-is)
