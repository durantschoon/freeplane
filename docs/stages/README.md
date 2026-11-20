# Stages

Delegated implementation for the `clojure-plugin` branch. Roadmap: `TODO_clojure.md`.
This directory is process material: remove it from the branch before opening the upstream PR.

## Numbering

One global sequence. Each stage has `stage-NN-PROMPT.md` (committed to `clojure-plugin`
before launch; the committed text is canonical) and `stage-NN-REPORT.md` (written by the
executor, committed with the change). Each stage is one branch `stage-NN` and one commit.

## Gates

This machine's default Java is a JRE, and `gradle` is not on PATH. Every Gradle command
uses this preamble:

```bash
J=$(ls -d /gnu/store/*openjdk-25*-jdk | head -1)
export JAVA_HOME=$J PATH=$J/bin:$PATH
G="./gradlew -Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=$J"
```

| Gate | Command |
|---|---|
| Compile | `$G :freeplane_plugin_clojure:compileJava :freeplane_plugin_clojure:compileTestJava` |
| Tests | `$G :freeplane_plugin_clojure:test :freeplane_plugin_script:test` |
| Deploy | `$G :freeplane_plugin_clojure:copyOSGiJars` then list `BIN/plugins/org.freeplane.plugin.clojure/lib/` |
| Java 8 API | `git diff` shows no Java 9+ API or syntax (`Files.readString`, `List.of`, `var`, `String.isBlank`, ...). `-source 8` on JDK 25 does not catch these. |

Baseline (2026-11-20, `350a4a726d`): Clojure plugin 1 JUnit test (wrapping 2 `clojure.test`
tests) passing; script plugin 99/99 passing. After stages 01-02 (`6442b71fde`): Clojure plugin 16, script plugin 99.

If a Gradle daemon was started with another JDK, run `$G --stop` first.

`gradlew` and `gradle/wrapper/` are gitignored, so a fresh worktree has neither. Copy them in
from the main checkout (`cp gradlew <wt>/ && mkdir -p <wt>/gradle && cp -r gradle/wrapper <wt>/gradle/`).
zsh does not word-split `$G`; use `${=G}` or write the command out.

## Guardrails

Principle: **information, once obtained, is never silently discarded or degraded.**

1. **Stay in the allow-list.** A change needed outside it ⇒ STOP and ask.
2. **Follow existing Freeplane patterns** (`freeplane_plugin_script` is the reference). Inventing
   a new framework or public API ⇒ STOP and ask.
3. **Functional core, imperative shell.** Pure logic (source building, name filtering) lives in
   small package-private classes with no I/O and no Clojure runtime calls; the engine is thin wiring.
4. **Errors are explicit.** Script failures surface as `javax.script.ScriptException` carrying the
   original cause; never swallow or replace with a generic message.
5. **Java 8 compatible** production code. No comments or Javadoc in new production Java
   (self-documenting names, per the repo `CLAUDE.md`); tests may have short comments.
6. **No speculative generality.** Extract only what this stage uses.
7. **Know what you don't know.** If Clojure or Freeplane behaviour differs from what the prompt
   assumes, do not guess or weaken a test to pass ⇒ STOP and report under Blocked.
8. **Never touch** `1.12.x`, `1.13.x`, or any remote. Do not force-push.

## Coordinator practices

- Stage prompts land on `clojure-plugin` before launch.
- Agent worktrees start from the fork's default branch (`1.12.x`), not `clojure-plugin`: the launch
  message must name the base commit and tell the executor to branch from it.
- Don't make a constant package-private when Clojure test code must read it; Clojure interop
  only sees public members (stage 01 needed a follow-up commit for this).
- At most one in-flight stage touches any shared file (`settings.gradle`, root `build.gradle`,
  `freeplane_plugin_clojure/build.gradle`, `TODO_clojure.md`).
- Executors commit locally; the coordinator pushes and merges.
- Executors run on Sonnet; a stage that blocks or fails review twice is re-run with Fable.
- Review = fresh `stage-reviewer` mechanical pass + coordinator diff read + independent gate rerun
  on the merged union. Report-reading alone is not review.
- Anchor estimates on the conservative case; preflight risky stages early.
- Retro every 5 stages: re-read the last five REPORTs' Deviations and Open questions, update this section.
- The coordinator ticks `TODO_clojure.md` boxes after merge; executors do not edit it.
