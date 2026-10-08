# Make the build configuration-cache compatible and turn the configuration cache on

**Challenged:** amended — the recommended Decision is no longer `problems=warn`: with warn, Gradle still stores and reuses an entry whose problems it only reported, so a task action that captured a script object can misbehave on reuse — unacceptable on the release builds; the recommendation is now option 3 (`fail`, with `notCompatibleWithConfigurationCache` on the offending tasks, which makes Gradle skip storing for builds that run them). Step 1 also covers the Xcode build phase's environment-driven invocation.

**Kind:** build  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** medium  ·  **Platforms:** all (build only)
**Files:** `gradle.properties`; `app/desktop/build.gradle.kts`; `app/web/build.gradle.kts` (unless plan 64 step 4 moved
`finishWebDistribution`); `tools/screenshots/build.gradle.kts`; possibly `settings.gradle.kts`; root `CLAUDE.md`
(Build); `app/desktop/CLAUDE.md`
**Depends on:** 65 (land caching/parallel first, so a regression is attributable); 64 step 4 is recommended first for
the web part

## Problem

Gradle 9.7.1 (`gradle/wrapper/gradle-wrapper.properties`) re-runs every build script on every invocation unless the
configuration cache is on; with 20 projects, the Compose, AGP 9.4.1, Kotlin 2.4.20 (incl. Native and Wasm) and Koin
compiler plugins, configuration is a noticeable part of each incremental `desktopTest` or `:app:desktop:run`. It is
off (`gradle.properties` has no `org.gradle.configuration-cache`), and the scripts contain known blockers — task
actions that capture the build script object, which the cache cannot serialise:

- `app/desktop/build.gradle.kts`:
  ```kotlin
  tasks.matching { it.name.startsWith("createRelease") || … }.configureEach {
      doFirst {
          if (isJetBrainsRuntimeMissing) {   // script-level val → captures the script
  ```
  `onlyIf { isWindowsHost || isLinuxHost }` (on `recordClassDataArchive`), `onlyIf { isLinuxHost }`
  (`addStartupWmClassToDeb`), `onlyIf { isWindowsHost }` (`addLaunchAfterInstallToMsi`) — `isLinuxHost`/`isWindowsHost`
  are script-level getters (`val isLinuxHost get() = System.getProperty("os.name")…`).
  The ANGLE copy, `tasks.matching { isWindowsHost && it.name == "createReleaseDistributable" }.configureEach { val
  libraries = angleRuntime; … doLast { libraries.files… } }`, captures a `Configuration` in a task action.
- `app/web/build.gradle.kts`: `finishWebDistribution`'s `doLast` calls the script-level functions `sha256()`,
  `toJsonString()`, `gzipTo()`, `brotliTo()`.

## Fix

1. **Find every problem.** Run with `--configuration-cache --configuration-cache-problems=warn` and read
   `build/reports/configuration-cache/**/configuration-cache-report.html` for each entry point:
   `desktopTest`, `:app:android:assembleDebug`, `:app:android:assembleRelease`, `:app:desktop:run`,
   `:app:desktop:createReleaseDistributable` (macOS) and `packageReleaseDeb` (Linux, via plan 61's CI job or a
   container), `:app:web:wasmJsBrowserDistribution`, `:app:web:wasmJsBrowserDevelopmentRun`,
   `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:tools:screenshots:run`, and an Xcode build of the iOS app (its
   build phase runs `./gradlew :app:ios:embedAndSignAppleFrameworkForXcode` with no properties, configured by the
   Xcode environment — `CONFIGURATION`, `SDK_NAME`, `ARCHS`, `BUILT_PRODUCTS_DIR`, … — which the cache records as
   inputs, so a simulator build after a device build reconfigures, as it should; check a Debug simulator build and a
   Release archive). Run each twice: the second run must say "Reusing configuration cache." List problems from third-party plugins (`com.hyperether.localization`,
   `io.insert-koin.compiler.plugin`, `androidx.baselineprofile`) separately — those decide the Decision.
2. **Fix the desktop script.** Copy each script-level value into a local `val` *outside* the action, e.g.
   ```kotlin
   tasks.matching { … }.configureEach {
       val runtimeMissing = isJetBrainsRuntimeMissing
       doFirst { if (runtimeMissing) throw GradleException("$path is a release build, …") }
   }
   ```
   (`path` inside `doFirst` is the task's own property — fine), `val linux = isLinuxHost; onlyIf { linux }` likewise,
   and for ANGLE `val libraries = files(angleRuntime)` (a `ConfigurableFileCollection`, which the cache serialises) and
   keep `inputs.files(libraries)`. Keep every comment.
3. **Fix the web script** — preferably by landing plan 64 step 4 (the typed `FinishWebDistribution`). Otherwise move
   the four helpers inside the `doLast` as local functions.
4. Fix whatever else step 1 found in the project's scripts the same way (task actions may capture only locals,
   providers, file collections and serialisable values; `project` must not be reached from an action).
5. Add `org.gradle.configuration-cache=true` to `gradle.properties` (with a comment), and, while third-party problems
   remain, handle them per the Decision — never by leaving `org.gradle.configuration-cache.problems=warn` in the
   committed file. CI needs no change; setup-gradle (plan 65)
   can cache the configuration cache only with an encryption key — leave that off.
6. Docs: root `CLAUDE.md` Build gets one sentence ("the configuration cache is on: a task action may capture only
   locals and providers, never a script-level `val` or function"); `app/desktop/CLAUDE.md` notes it where it describes
   these tasks.

## Tests

No new unit tests. Guarding: `./gradlew desktopTest --continue` twice (second run reuses the cache and passes);
plan 61's compile jobs in CI; plan 64's build-logic tests if landed.

## Manual check

- Run the desktop app, the Android debug app, the web dev server and the iOS app from Xcode with the cache on.
- Change `campfire.versionName` in `local.properties` and run `:app:desktop:run` — the cache must be invalidated
  (the root `settings.gradle.kts` reads `local.properties`, which Gradle tracks as a configuration input); the About
  screen shows the new version.
- The next release's publish runs (all six) succeed; `packageReleaseDeb` still adds `StartupWMClass`, the `.msi` still
  gets its launch checkbox, `recordClassDataArchive` still runs on Linux/Windows (its `onlyIf` change).

## Decision

If a third-party plugin (most likely `com.hyperether.localization` or the Koin compiler plugin) reports problems:

1. **Recommended — turn it on as `fail` and mark the offending third-party tasks
   `notCompatibleWithConfigurationCache("<plugin> <issue link>")`** from the module scripts (or once in a convention
   plugin, by task type). Gradle then neither stores nor reuses an entry for a build that schedules one of them, so
   those builds run as today and every other build uses the cache; file an issue upstream and drop the marks when it
   is fixed. Works only for problems raised by *tasks*; a plugin that breaks the cache at configuration time leaves
   option 2.
2. Leave the cache off but land steps 2–4 (scripts ready, flag later).
3. Turn it on with `org.gradle.configuration-cache.problems=warn`. Not recommended: with `warn` the entry is stored and
   reused despite the problems it lists, and a task action that captured a script object or `project` can then fail or
   behave differently on the reused run — the desktop release checks and packaging tasks are exactly such actions
   until step 2 lands, and they run only in the publish workflows.
