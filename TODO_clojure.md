# Clojure Integration TODO

Tracking work to bring `freeplane_plugin_clojure` from scaffold to upstream-ready feature.

**Current state:** Module compiles and tests pass. Execution is intended via JSR-223
(`ClojureScriptEngine` + SPI) through the existing script plugin (`GenericScript`).
Activator, preferences, and several config properties are stubbed or unused. A parallel
`ClojureScript` (`IScript`) implementation exists but is never wired.

**Branches:** Integration work lives on `feature/clojure-integration`; current branch is
`1.12.x` with a hygienic re-add of the module.

---

## P0 — Correctness and integration

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
  - Clean `BIN/plugins/org.freeplane.plugin.clojure/lib/` before copy, or delete plugin lib dir on rebuild.

- [ ] **Revert local/personal changes before upstream**
  - `ApplicationName=DurantsFreeplane`, `workspaceTitle`, `apple.awt.application.name` in `freeplane.properties`
  - `Bundle-Vendor` override in root `build.gradle`
  - Unrelated core file edits from original integration commits (if still present)

---

## P1 — Dependencies and build metadata

- [ ] **Replace Rama as default Clojure dependency**
  - Rama adds ~108MB+ to the plugin; mind-map scripting does not need it by default.
  - Use explicit `org.clojure:clojure` (pick version: 1.11.x conservative or 1.12.x current).
  - Move `rama_hello.clj` to examples or a separate optional `freeplane_plugin_rama` module.

- [ ] **Fix stale `bundleExports`**
  - Remove export of deleted package `org.freeplane.plugin.clojure.proxy`.
  - File: `freeplane_plugin_clojure/build.gradle`

- [ ] **Fix broken javadoc task sources**
  - References deleted `ClojureScriptBaseClass.java` and `ClojureStaticImports.java`.
  - Point at existing API surface or disable until proxy/helpers exist.

- [ ] **Align reported Clojure version**
  - `ClojureScriptEngineFactory.getLanguageVersion()` says `1.11.1`; runtime may be `1.12.2` via Rama.
  - Derive from dependency or constant shared with tests.

- [ ] **Remove committed `.class` files if any reappear**
  - Feature branch briefly had `.class` files under `src/main/java/`; keep source-only.

---

## P2 — Security, caching, and configuration

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

---

## P3 — API, examples, and developer experience

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

---

## P4 — Testing

- [ ] **Keep `ClojureTestRunner` + `clojure.test`**
  - Currently verifies Rama load and Clojure version; update assertions when dependency strategy changes.

- [ ] **Add Java integration test via `GenericScript`**
  - Execute minimal `(+ 1 2)` on `.clj` with mock node; no full Swing/OSGi if avoidable.

- [ ] **Decouple Rama from core plugin tests**
  - If Rama moves to optional module, move Rama-specific tests there.

---

## P5 — Optional / future

- [ ] **Rama as separate plugin** (`freeplane_plugin_rama`) if stream processing in maps is a goal
- [ ] **nREPL server** for live development (dev-only, preference-gated)
- [ ] **Clojure add-on JAR support** via existing script classpath machinery
- [ ] **Restore dedicated Clojure script editor actions** only if JSR-223 + generic script editor is insufficient

---

## Target module shape (reference)

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
should be a **language runtime + configuration**, not a second scripting framework.

---

## Done (already in tree)

- [x] `freeplane_plugin_clojure` module in `settings.gradle`
- [x] OSGi plugin build (`pluginJar`, `copyOSGiJars`, bundle activator)
- [x] `ScriptingConfiguration.addClasspathForOsgiPlugins()` for SPI discovery
- [x] JSR-223 factory + SPI registration
- [x] `copyClojureScripts` → `BIN/scripts/`
- [x] `ClojureTestRunner` bridging JUnit to `clojure.test`
