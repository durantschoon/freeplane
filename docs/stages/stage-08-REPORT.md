# Stage 08 report

## Summary
Commit A passes ScriptContext bound variables into GenericScript bindings (c, node and static properties still win).
Commit B adds ClojureGenericScriptTest (6 tests) running Clojure through GenericScript on a real NodeModel.
No BLOCKED condition from the prompt occurred (engine found headless; no Controller needed).

## Files changed
- Commit A (8600b9b0758b6026fa3b14a67821fbdd1e7aeec1): `freeplane_plugin_script/.../GenericScript.java` (+3 lines).
- Commit B: `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/script/ClojureGenericScriptTest.java`, this report. `build.gradle` untouched (Mockito/AssertJ/JUnit already available).

## Gate results (counts from test-result XML after cleanTest)
- Base (README): Clojure 38, script 99.
- After commit A: compile OK; Clojure 38/38, script 99/99, 0 failures.
- After commit B: compile OK; Clojure 44/44 (38 + 6 new), script 99/99, 0 failures, 0 skipped.
- Java 8 API: no Java 9+ API used (`Files.write`, lambdas, method refs only).

## Runtime-sharing observation (test 6)
Two separate GenericScript instances (each with its own ScriptClassLoader) returned the same
`(System/identityHashCode (class clojure.lang.RT))`, and a `def` in the first was visible in the second (42L).
So they share one Clojure runtime and one `user` namespace. Likely cause: clojure.jar is on the parent
classpath, so each ScriptClassLoader delegates to the same RT class; additionally ClojureUserNamespace.get()
is a single static namespace the engine binds as *ns*. In the OSGi app the classloader topology may differ; this
only measures the test environment. The test asserts the observed sharing (the prompt's "not visible" expectation was false).

In the OSGi app, `ScriptClassLoader`'s parent is the script plugin's class loader (`ScriptClassLoader.java:31`), which
cannot see Clojure. So each loaded script gets its own Clojure runtime and `user` namespace there. The test (named
`scriptsShareOneClojureRuntimeWhenClojureIsOnTheTestClasspath`) cannot observe that.

## Commits C and D (coordinator follow-up)
- Commit C: `ScriptSource.rereadFile()` returns `script` when not backed by a file (fixes Deviation 1's NPE). Gates after C: Clojure 44/44, script 99/99.
- Commit D: added `inMemorySourceExecutesThroughStringConstructor` (`new GenericScript("(+ 1 2)", "clj", permissive)` returns 3L; the
  `c.script(source, "clj")` path); renamed test 6. Checked once with commit C's change reverted: the new test failed (NPE wrapped in
  ExecuteScriptException, as in Deviation 1); passes with C. Gates after D: Clojure 45/45, script 99/99.
- Deviation 1's File-constructor workaround in `run()` is left in place for the other tests.

## Deviations
1. Scripts are created with `new GenericScript(File, permissions)` on a temp `.clj` file, not the String constructor
   the prompt specified. The String constructor cannot execute any Compilable engine: `compileAndCache` calls
   `scriptSource.rereadFile()` -> `slurpFile(null)` -> NPE (GenericScript.java:75/294), wrapped in ExecuteScriptException.
   Fixing it is outside the allow-list (commit A is already made).
2. Test 6 asserts shared runtime/namespace (observed) rather than isolation; the identity check is folded into that test.
3. Test 5 asserts ExecuteScriptException and gotoLine == [3].

## Open questions
- Upstream bug: String-source GenericScript with a Compilable engine always NPEs (see Deviation 1). Does the human want a
  fix (e.g. skip rereadFile when `!scriptSource.isFile()`) as a further upstream commit? Does the app ever use the String constructor with Clojure?
- Shared `user` namespace means scripts can leak defs into each other; decide whether per-script namespaces are wanted.
