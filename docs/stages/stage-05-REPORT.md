# Stage 05 Report

## Summary
Deleted the unused `defaults.properties` and `preferences.xml`; reduced `Activator` to an empty `BundleActivator` (license header kept, comments/imports/System.out removed); added `ActivatorTest`. Mockito is on the test classpath (root build.gradle), so the test uses a mocked `BundleContext` and asserts `verifyNoInteractions`.

## Files changed
- D freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/defaults.properties
- D freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/preferences.xml
- M freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/Activator.java
- A freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ActivatorTest.java
- A docs/stages/stage-05-REPORT.md

## Gate results
Base 1a17d9504f: compile OK; clojure plugin tests 16 passing (BUILD SUCCESSFUL).
Final (compileJava, compileTestJava, clojure:test, script:test): BUILD SUCCESSFUL; clojure 17/17 (16 + ActivatorTest), script 99/99, 0 failures.
Deploy: `copyOSGiJars` + `:jar` BUILD SUCCESSFUL; BIN/plugins/org.freeplane.plugin.clojure/lib/ = clojure-1.12.2.jar, core.specs.alpha-0.4.74.jar, plugin-1.13.4.jar, spec.alpha-0.5.238.jar. Manifest `freeplane_plugin_clojure/build/manifest/MANIFEST.MF` contains `Bundle-Activator: org.freeplane.plugin.clojure.Activator`.
`git grep "clojure_script\|clojure_default\|plugins/Clojure" -- freeplane_plugin_clojure`: no output. `git grep "System.out" -- freeplane_plugin_clojure/src/main`: no output.
Java 8 API gate: diff uses only Mockito/JUnit4 APIs; clean.

## Deviations
1. Branch was created via `git checkout -B stage-05 1a17d9504f` as instructed.
2. gradlew and gradle/wrapper copied in (gitignored, not committed).
3. Baseline measured on base with only gradlew copied; `.claude`-level tooling refused compound git commands, so steps were split (no effect on result).

## Open questions
None.
