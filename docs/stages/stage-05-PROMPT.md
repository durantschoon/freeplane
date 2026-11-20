# Stage 05: Remove unused Clojure preferences and stub activator code

Read `docs/stages/README.md` first (gates, guardrails, worktree setup). Roadmap items:
`TODO_clojure.md` A2 "Wire preferences and defaults" (decision: remove) and "Add translation keys"
(no longer needed).

Retro note (stage 05 is a multiple of 5): reports 01 and 02 are the only ones so far. Their four
deviations (wrong worktree base, missing gradlew, zsh `$G` splitting, a package-private constant
read from Clojure) are already in the README's practices and gates sections.

## Motivation (measured)

- `git grep -n "clojure_script\|clojure_default"` finds the six keys only in `defaults.properties` and
  `preferences.xml`. No Java code loads either file or reads any key.
- None of the keys can be honoured sensibly:
  - `clojure_script_cache_size` and `clojure_script_security_enabled` duplicate `GenericScript`,
    which already caches compiled scripts and applies `ScriptingPermissions` to every language.
  - `clojure_script_timeout` has no counterpart for Groovy either.
  - `clojure_script_enabled` and `.cljs` in `clojure_script_extensions` refer to ClojureScript,
    which this plugin does not support.
  - `clojure_default_namespace` (`freeplane.clojure.user`) contradicts the engine, which evaluates
    in `user`.
- `preferences.xml` names translation keys (`plugins/Clojure/...`) that exist in no resource file.
- `Activator` prints to `System.out` and registers an `IModeControllerExtensionProvider` whose
  `installExtension` only prints and has TODO comments. The plugin's only real integration is the
  JSR-223 SPI file, which needs no activator code.

## The change

1. Delete `freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/defaults.properties`
   and `.../preferences.xml`.
2. Reduce `Activator` to a `BundleActivator` with empty `start` and `stop`. The build sets
   `Bundle-Activator` to this class, so it must stay. Keep the license header; drop the other comments
   and the unused imports.
3. Add a test `ActivatorTest`: `start(null)` and `stop(null)` complete without throwing. Use a
   Mockito `BundleContext` mock instead of `null` if Mockito is on the test classpath; say which in the report.

## Allowed files

- `freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/defaults.properties` (delete)
- `freeplane_plugin_clojure/src/main/resources/org/freeplane/plugin/clojure/preferences.xml` (delete)
- `freeplane_plugin_clojure/src/main/java/org/freeplane/plugin/clojure/Activator.java`
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ActivatorTest.java` (new)
- `docs/stages/stage-05-REPORT.md` (new)

Stage 03 runs in parallel and owns `ClojureScriptEngine.java`, `ClojureScriptSource.java`, the new
reader and form classes, and their tests.

## Definition of Done

- Compile and Tests gates pass; script plugin still 99/99.
- `git grep -n "clojure_script\|clojure_default\|plugins/Clojure" -- freeplane_plugin_clojure` returns nothing.
- `git grep -n "System.out" -- freeplane_plugin_clojure/src/main` returns nothing.
- Deploy gate: after `copyOSGiJars` and `:freeplane_plugin_clojure:jar`, the bundle manifest
  (`freeplane_plugin_clojure/build/manifest/MANIFEST.MF`, or wherever the build writes it; say where)
  still has `Bundle-Activator: org.freeplane.plugin.clojure.Activator`.
- Java 8 API gate clean.

## Commit

One commit on branch `stage-05`, message exactly:

`Remove unused Clojure plugin preferences and empty the activator`

Body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-05-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results (commands + pass counts); Deviations; Open questions.

## Blocked protocol

If anything outside the allow-list turns out to read these files or keys, or the bundle needs
activator behaviour to load: stop, commit nothing, and write `docs/stages/stage-05-REPORT.md` with a
`BLOCKED` section (evidence, exact output, the decision you need). Return that text as your final message.
