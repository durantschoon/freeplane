# Stage 06: Pure version-fence namespace

Read `docs/stages/README.md` first (gates, guardrails, worktree setup). Read the "Version fences" section of
`TODO_clojure.md`. This stage builds its pure core: data in, data out, no Freeplane or I/O.

## Motivation (measured)

- Nothing in the plugin checks the Clojure or Freeplane version it runs on. `ClojureScriptEngineFactory.CLOJURE_VERSION`
  is checked only by a test.
- `org.freeplane.core.util.FreeplaneVersion.compareTo` (`freeplane/src/main/java/org/freeplane/core/util/FreeplaneVersion.java:147-171`)
  compares the four numbers only, so `1.13.0 rc` equals `1.13.0`. `org.freeplane.api.FreeplaneVersion` exposes `isFinal()`.
- A design review (Fable, 2026-11-21) recommended:
  - lower-bound checks plus version *ranges* for quirks;
  - three numbers per dependency (floor, built-against, tested-up-to);
  - pre-releases ordered before their release;
  - structured `ex-info` errors;
  - a removal trigger for stale quirks.

## The change

New Clojure namespace `freeplane.script.fences` in
`freeplane_plugin_clojure/src/main/resources/freeplane/script/fences.clj`. It is pure; give every public var a
docstring. It contains:

- `supported`: `{:clojure {:floor "1.12.0" :built-against "1.12.2" :tested-up-to "1.12.2"}
  :freeplane {:floor "1.13.0" :built-against "1.13.4" :tested-up-to "1.13.4"}}`.
- `quirks`: `[]` (empty in v1). The docstring defines the entry shape:
  `{:id keyword :dependency :clojure|:freeplane :introduced "v" :fixed-in "v"-or-nil :disables #{op-keyword} :evidence "test or issue"}`.
- `(parse-version s)`:
  - Returns `{:numbers [maj min patch build] :qualifier "rc"-or-nil}`, with missing numbers as 0.
  - Accepts `"1.12.2"`, `"1.13"`, `"1.13.0-alpha1"`, `"1.13.0 rc"`, `"1.12.2-SNAPSHOT"` and `"1.13.4 nightly_build"`.
  - Returns nil for anything without a leading number.
  - Numbers are parsed numerically, never compared as strings.
- `(compare-versions a b)`:
  - Takes strings or parsed maps.
  - Compares numbers first. With equal numbers, any qualifier sorts *before* no qualifier.
- `(at-least? found needed)`.
- `(floor-violation dependency found)`: returns nil, or an `ex-info` (returned, not thrown) with message
  `"<dependency> <found> is older than the supported minimum <floor>"` and data
  `{:fence :floor :dependency .. :found .. :needed <floor>}`.
- `(requirement-violation dependency found needed)`: the same shape with `:fence :requirement`. A caller's declared
  minimum, checked with `at-least?`.
- `(newer-than-tested dependency found)`: a warning string or nil.
- `(active-quirks quirks dependency version)`: entries for that dependency whose range covers version. The range is
  inclusive `:introduced` and exclusive `:fixed-in`; a nil `:fixed-in` is open-ended.
- `(disabled-violation quirks dependency version op)`: nil, or an `ex-info` with `:fence :quirk`, `:evidence` and the
  quirk `:id`.
- `(stale-quirks quirks supported)`: entries whose `:fixed-in` is at or below their dependency's `:floor`.

`ClojureTestRunner` runs a list of test namespaces: `org.freeplane.plugin.clojure.test-main` and
`freeplane.script.fences-test`. It fails when any test fails or errors, reporting per-namespace counts in the message.

## Allowed files

- `freeplane_plugin_clojure/src/main/resources/freeplane/script/fences.clj` (new)
- `freeplane_plugin_clojure/src/test/resources/freeplane/script/fences_test.clj` (new)
- `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/clojure/ClojureTestRunner.java`
- `freeplane_plugin_clojure/src/test/resources/org/freeplane/plugin/clojure/test_main.clj` (only to add the check in test 10)
- `docs/stages/stage-06-REPORT.md` (new)

Stage 08 runs in parallel and owns `GenericScript.java` and `freeplane_plugin_clojure/src/test/java/org/freeplane/plugin/script/`.

## Tests (`clojure.test`, in `fences_test.clj`)

1. `parse-version` handles each listed input as specified; `"x1"` and `""` give nil.
2. `compare-versions`:
   - `"1.9"` < `"1.12"` (numeric);
   - `"1.13.0 rc"` < `"1.13.0"`;
   - `"1.12.2-SNAPSHOT"` < `"1.12.2"`;
   - `"1.13"` = `"1.13.0.0"`.
3. `at-least?` at, above and below the bound.
4. `floor-violation` gives nil for `"1.12.2"`. For `"1.11.4"` it gives an `ex-info` with the exact message and data map.
5. `requirement-violation` behaves the same way for a declared minimum.
6. `newer-than-tested` gives nil at `"1.12.2"` and a string at `"1.12.3"`.
7. `active-quirks` with a sample quirk:
   - covers its `:introduced`;
   - excludes its `:fixed-in`;
   - is open-ended when `:fixed-in` is nil;
   - ignores other dependencies.
8. `disabled-violation` gives nil for an op the quirk doesn't disable, and an `ex-info` with `:fence :quirk` and
   `:evidence` for one it does.
9. `stale-quirks` flags a quirk fixed at or below the floor. `(stale-quirks quirks supported)` on the real table is
   empty: this is the removal trigger.
10. `(get-in supported [:clojure :built-against])` equals the factory's `getLanguageVersion()`. Add this to `test_main.clj`.

## Definition of Done

- The Compile and Tests gates pass. The script plugin is still 99/99. Report the Clojure plugin count from the XML,
  plus the `clojure.test` counts per namespace from the runner output.
- Java 8 API gate is clean.
- `fences.clj` requires only `clojure.core` and `clojure.string`.

## Commit

One commit on branch `stage-06`, with the message exactly:

`Add pure version-fence namespace for Clojure and Freeplane compatibility checks`

The body ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push.

## Report (`docs/stages/stage-06-REPORT.md`, same commit)

Sections: Summary; Files changed; Gate results; Deviations; Open questions.

## Blocked protocol

If test resources under `freeplane/` are not on the test classpath, or the runner cannot load them without leaving
the allow-list: stop, commit nothing, and write the report with a `BLOCKED` section (what you tried, exact output,
the decision you need). Return that text as your final message.
