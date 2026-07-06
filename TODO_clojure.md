# Clojure & Rama Integration TODO

Tracking work to bring Clojure scripting and optional Rama support to upstream-ready state.

**Current state:** `freeplane_plugin_clojure` compiles and tests pass. Clojure execution is
intended via JSR-223 (`ClojureScriptEngine` + SPI) through `GenericScript`. Rama is bundled
into the Clojure plugin as both runtime and demo (`rama_hello.clj`) — that coupling is wrong
for production. Activator, preferences, and several config properties are stubbed or unused.

**Branches:** Integration work lives on `feature/clojure-integration`; current branch is
`1.12.x` with a hygienic re-add of the module.

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

- [ ] **Fix binding mismatch in `ClojureScriptEngine`**
  - `GenericScript` binds `node` and `c` (controller proxy), not `script-context`.
  - Engine should read `c` first, fall back to `script-context` for compatibility.
  - File: `freeplane_plugin_clojure/.../ClojureScriptEngine.java`

- [ ] **Resolve duplicate execution paths**
  - Choose one: JSR-223 via `GenericScript` (recommended) OR dedicated `ClojureScript` in `ScriptingEngine.compile()`.
  - If JSR-223 only: delete `ClojureScript.java` to avoid two semantics.
  - If dedicated path: wire `.clj` in `ScriptingEngine` like Groovy and remove redundant engine duplication.

- [ ] **Clean stale JARs in plugin lib directory**
  - `copyOSGiJars` appends without cleaning; old `clojure-1.10.3.jar` can coexist with Rama's `clojure-1.12.2.jar`.
  - Clean `BIN/plugins/org.freeplane.plugin.*/lib/` before copy, or delete plugin lib dir on rebuild.

- [ ] **Revert local/personal changes before upstream**
  - `ApplicationName=DurantsFreeplane`, `workspaceTitle`, `apple.awt.application.name` in `freeplane.properties`
  - `Bundle-Vendor` override in root `build.gradle`
  - Unrelated core file edits from original integration commits (if still present)

### A1 — Dependencies and build metadata

- [ ] **Use explicit Clojure dependency (decouple from Rama)**
  - Replace `com.rpl:rama` in this module with `org.clojure:clojure` (1.11.x conservative or 1.12.x current).
  - Remove Rama-specific Maven repos from `freeplane_plugin_clojure/build.gradle`.
  - Remove `rama_hello.clj` from shipped scripts; relocate to `freeplane_plugin_rama` examples.

- [ ] **Fix stale `bundleExports`**
  - Remove export of deleted package `org.freeplane.plugin.clojure.proxy`.
  - File: `freeplane_plugin_clojure/build.gradle`

- [ ] **Fix broken javadoc task sources**
  - References deleted `ClojureScriptBaseClass.java` and `ClojureStaticImports.java`.
  - Point at existing API surface or disable until proxy/helpers exist.

- [ ] **Align reported Clojure version**
  - `ClojureScriptEngineFactory.getLanguageVersion()` must match the pinned dependency.
  - Share version constant with tests.

- [ ] **Remove committed `.class` files if any reappear**
  - Feature branch briefly had `.class` files under `src/main/java/`; keep source-only.

### A2 — Security, caching, and configuration

- [ ] **Use safe reading instead of bare `read-string`**
  - Wrapped script eval uses `clojure.core/read-string` (reader macros, injection risk).
  - Prefer `load-string` with `*read-eval*` false or `clojure.tools.reader` with `:read-eval false`.

- [ ] **Implement or drop `Compilable`**
  - `ClojureCompiledScript.eval()` re-runs full eval; no real compile cache.
  - Either compile once to `IFn` and reuse, or remove `Compilable` and document interpret-on-run.

- [ ] **Wire preferences and defaults**
  - `defaults.properties` and `preferences.xml` exist but nothing reads them.
  - `Activator` has TODOs; restore minimal `ClojurePluginRegistration`:
    - load defaults into option panel (mirror `ScriptingRegistration.addPropertiesToOptionPanel()`)
    - honor or remove: `clojure_script_enabled`, `clojure_script_cache_size`, `clojure_script_timeout`, `clojure_script_security_enabled`

- [ ] **Add translation keys**
  - `plugins/Clojure/...` keys in `preferences.xml` need entries in `Resources_en.properties` (and format_translation).

### A3 — API, examples, and developer experience

- [ ] **Fix example scripts to use proxy API**
  - Prefer `c` controller and proxy methods over raw Java interop (`.getChildren`, `.getText` on node).
  - Files: `clojure/basic.clj`, `clojure/advanced.clj`, `clojure/hello.clj`

- [ ] **Ship helper namespace**
  - Add `freeplane.clojure` (or `freeplane.script`) ns with common requires and idiomatic wrappers.
  - Auto-require in engine wrapper so scripts get same globals as Groovy (`logger`, `ui`, `textUtils`, etc.).

- [ ] **Syntax highlighting for `.clj`**
  - Register with jsyntaxpane (already a plugin dependency).

- [ ] **Document Clojure scripting**
  - Short guide: bindings (`node`, `c`), menu discovery, `@ExecutionModes` convention, user script dirs.

### A4 — Clojure testing

- [ ] **Update `ClojureTestRunner` after Rama decoupling**
  - Remove Rama load / version assertions from clojure plugin tests.
  - Test Clojure version against explicit dependency only.

- [ ] **Add Java integration test via `GenericScript`**
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

- [x] `freeplane_plugin_clojure` module in `settings.gradle`
- [x] OSGi plugin build (`pluginJar`, `copyOSGiJars`, bundle activator)
- [x] `ScriptingConfiguration.addClasspathForOsgiPlugins()` for SPI discovery
- [x] JSR-223 factory + SPI registration
- [x] `copyClojureScripts` → `BIN/scripts/`
- [x] `ClojureTestRunner` bridging JUnit to `clojure.test` (needs Rama decoupling)
- [x] `rama_hello.clj` IPC spike proving classpath + basic append works (to be refactored, not kept as-is)
