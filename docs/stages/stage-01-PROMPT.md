# Stage 01: Plain Clojure dependency and clean build metadata

Read `docs/stages/README.md` first (gates, guardrails). Roadmap items: `TODO_clojure.md` A0
"Clean stale JARs", A1 (all items).

## Motivation (measured)

- `freeplane_plugin_clojure/build.gradle` gets Clojure only transitively through
  `lib 'com.rpl:rama:1.3.0'` (100MB+, two extra Maven repos). The commented-out
  `org.clojure:clojure` line confirms this was a stopgap.
- `test_main.clj` asserts that `com.rpl.rama` loads and that `(clojure-version)` is `"1.12.2"`,
  while `ClojureScriptEngineFactory.getLanguageVersion()` returns `"1.11.1"`: the reported version is wrong.
- `bundleExports` exports `org.freeplane.plugin.clojure.proxy`, a package that does not exist.
- The `javadoc` block includes `ClojureScriptBaseClass.java` and `ClojureStaticImports.java`, which
  do not exist, and `build.dependsOn javadoc` runs it on every build.
- The root `copyOSGiJars` is a `Copy` into `BIN/plugins/org.freeplane.plugin.clojure/lib/`, so jars
  from earlier dependency sets (Rama's ~100 jars, `clojure-1.10.3.jar`) stay there and can sit
  on the classpath next to the current Clojure jar.
- `rama_hello.clj` ships as a menu script and needs Rama. It will be redone in a separate Rama
  plugin (Part B); a copy is kept in the private repo.

## The change

1. In `freeplane_plugin_clojure/build.gradle`:
   - Replace all `lib` dependencies and the `testImplementation` Rama line with exactly
     `lib 'org.clojure:clojure:1.12.2'`. Keep the three `implementation project(...)` lines.
     Remove `tools.namespace`, `java.classpath`, `tools.logging` and `data.json`: nothing in
     the module requires them (`grep -rn require freeplane_plugin_clojure/src`).
   - Remove the `repositories` block (Maven Central is already configured by the root build).
   - `bundleExports`: only `org.freeplane.plugin.clojure`.
   - Delete the `javadoc` block and `build.dependsOn javadoc`.
   - Make the plugin's lib directory start empty on each deploy: add a `doFirst` to
     `copyOSGiJars` in this file that deletes `globalBin + '/plugins/' + pluginid + '/lib'`.
     Do not change the root `build.gradle`.
   - Make sure the Clojure jar is on the test classpath. If `lib` is not already visible to tests,
     add the smallest wiring that fixes it in this file, and record it under Deviations.
2. `ClojureScriptEngineFactory`: add `static final String CLOJURE_VERSION = "1.12.2";` (package-private)
   and return it from `getLanguageVersion()`.
3. `test_main.clj`: remove `rama-dependencies-test`. Change `clojure-version-test` to assert that
   `(clojure-version)` equals `org.freeplane.plugin.clojure.ClojureScriptEngineFactory/CLOJURE_VERSION`
   (via Java interop; a static field read is fine). Add a test that `com.rpl.rama` is **not** loadable.
4. Delete `src/main/resources/org/freeplane/plugin/clojure/clojure/rama_hello.clj`.

## Allowed files

- `freeplane_plugin_clojure/build.gradle`
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/ClojureScriptEngineFactory.java`
- `freeplane_plugin_clojure/src/test/resources/org/freeplane/plugin/clojure/test_main.clj`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureTestRunner.java` (only if needed)
- `freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/clojure/rama_hello.clj` (delete)
- `docs/stages/stage-01-REPORT.md` (new)

Stage 02 is running in parallel and owns `ClojureScriptEngine.java`, `ClojureScript.java` and new
test classes. Do not touch them.

## Tests

1. `clojure-version-test`: `(clojure-version)` equals `ClojureScriptEngineFactory/CLOJURE_VERSION`.
2. `rama-absent-test`: `(require 'com.rpl.rama)` throws.
3. `ClojureTestRunner` still fails the JUnit run when any `clojure.test` test fails or errors
   (check this once by temporarily breaking an assertion; describe it in the report, don't commit it).

## Definition of Done

- Compile and Tests gates pass; script plugin still 99/99.
- Deploy gate: after deploying once with the old dependency set still in `BIN` (deploy first at the
  base commit, or copy a dummy `stale.jar` into that lib dir), then deploying with your change,
  the lib dir holds only `clojure-1.12.2.jar` plus its transitive dependencies (expect
  `spec.alpha` and `core.specs.alpha`), with no `rama`, no `stale.jar` and no other Clojure
  version. Paste the listing into the report.
- `git grep -n -i rama -- freeplane_plugin_clojure` returns nothing.
- Java 8 API gate clean.

## Commit

One commit on your stage branch, message exactly:

`Use plain org.clojure:clojure 1.12.2 in Clojure plugin and clean its build metadata`

End the message body with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
Try `git push`; credential failure is expected and fine.

## Report (`docs/stages/stage-01-REPORT.md`, in the same commit)

Sections: Summary; Files changed; Gate results (commands + pass counts); Deploy listing;
Deviations (every departure from this prompt, with the reason); Open questions.

## Blocked protocol

If `org.clojure:clojure:1.12.2` cannot be resolved, tests cannot see Clojure without touching a
file outside the allow-list, or a gate fails for a reason you cannot fix inside the allow-list:
stop, commit nothing, and write `docs/stages/stage-01-REPORT.md` with a `BLOCKED` section
(what you tried, exact error output, what decision you need). Return that text as your final message.
