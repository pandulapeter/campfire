# Turn on Gradle's build cache and parallel project execution, and cache Gradle in CI with gradle/actions/setup-gradle

**Kind:** build  ·  **Severity:** medium  ·  **Effort:** S  ·  **Risk:** medium  ·  **Platforms:** all (build only)
**Files:** `gradle.properties` (the "Gradle and Kotlin" block); `.github/workflows/tests.yml` and the six
`publish-*.yml` (or `.github/actions/setup-jdk/action.yml` if plan 62 landed); root `CLAUDE.md` (Build) or
`.github/CLAUDE.md`
**Depends on:** none

## Problem

`gradle.properties` ends with

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx4096M
kotlin.code.style=official
kotlin.native.binary.gc=cms
kotlin.native.ignoreDisabledTargets=true
android.enableR8.fullMode=true
android.useAndroidX=true
android.nonTransitiveRClass=true
```

— no `org.gradle.caching`, no `org.gradle.parallel`. With 20 projects in a strict `api`/`implementation` graph, the
independent modules (`:chordpro`, `:metronome:*`, the four data modules) compile one after another, and switching
branches or worktrees (the review lanes run in several worktrees at once) recompiles everything a cache would have
restored. CI uses `actions/setup-java`'s `cache: 'gradle'`, which caches the dependency jars and wrapper only, not
task outputs, and is the same key for every workflow.

Memory is the known constraint: `publish-ios.yml` already raises the daemon to `-Xmx6g` because "The release framework
is linked inside the Gradle daemon, which runs out of the 4 GB gradle.properties gives it" (commit 93089fdad). Parallel
execution can run a Kotlin/Native link beside other compilations in the same daemon.

## Fix

1. Add `org.gradle.caching=true` to `gradle.properties` with a one-line comment (the local build cache under
   `~/.gradle/caches/build-cache-1`; no remote cache). The project's own custom tasks are not `@CacheableTask`, so
   only Gradle/Kotlin/AGP's cacheable tasks are affected.
2. Add `org.gradle.parallel=true`. Then measure on the author's Mac: `./gradlew clean :app:ios:linkReleaseFrameworkIosArm64
   :app:android:assembleRelease --scan` (or `--profile`) with and without; and run the iOS publish build path locally
   (`xcodebuild … build` as in the root `CLAUDE.md`) to make sure the daemon does not run out of memory. If it does,
   add `org.gradle.workers.max=4` (or lower) rather than dropping parallel, and note why in the comment.
3. CI: replace `cache: 'gradle'` on `actions/setup-java` with a `gradle/actions/setup-gradle@v5` step right after it in
   `tests.yml` and every `publish-*.yml` (or once in plan 62's `setup-jdk` action). Default settings: the cache is
   written from the default branch (the nightly run) and read elsewhere, which is what we want; tag builds read it.
   Keep `GITHUB_TOKEN` on setup-java (the JetBrains runtime lookup still needs it).
4. Docs: one sentence in the root `CLAUDE.md` Build section about the two properties and the memory caveat.

Steps 1, 2 and 3 are separate commits; 2 can be reverted alone if a store build runs out of memory.

## Tests

No unit tests. All existing suites must pass with the flags on: `./gradlew desktopTest --continue` twice in a row (the
second run should be mostly `FROM-CACHE`/`UP-TO-DATE`), and after `./gradlew clean` (should restore from cache).

## Manual check

- Locally: build and run the Android debug app, the desktop app (`:app:desktop:run`), the web dev server and the iOS
  app from Xcode once each with the new properties; all start normally.
- CI: the next nightly `tests.yml` run shows setup-gradle's cache summary; the run after it is faster.
- The next release's publish runs (iOS and macOS above all) complete without an out-of-memory failure; if one fails,
  revert step 2 or lower `org.gradle.workers.max`.
