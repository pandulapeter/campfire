# Compile every platform's code — app shells, androidMain, iosMain, wasmJsMain and the screenshot tool — in the tests workflow

**Kind:** ci  ·  **Severity:** high  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `.github/workflows/tests.yml`; root `CLAUDE.md` (the "Only pure logic is tested" bullet, which describes what
`tests.yml` runs, and the release-pipeline text about `publish-all.yml` calling it); `.github/CLAUDE.md` if plan 60 has landed
**Depends on:** none (if plan 62 has landed, use its `setup-jdk` composite action; if plan 65 has landed, use
`gradle/actions/setup-gradle`)

## Problem

`.github/workflows/tests.yml` has one job on `ubuntu-latest` whose only Gradle step is

```yaml
      - name: Run the unit tests
        run: ./gradlew desktopTest --continue
```

`desktopTest` compiles `commonMain` + `desktopMain` (+ tests) of the multiplatform libraries. Nothing else is compiled
before a release: not `androidMain`, `iosMain` or `wasmJsMain` of any library, not `:app:android`, `:app:desktop`
(a plain JVM module with no `desktopTest`), `:app:ios`, `:app:web`, `:app:baselineprofile`, nor `:tools:screenshots`.
The first time those compile in CI is inside the `publish-*.yml` runs started by a published GitHub release — by which
time `publish-all.yml` has already started six store builds side by side, so an `iosMain` break ships Android, web,
Linux and Windows and fails iOS and macOS alone, leaving the stores on different versions. The nightly schedule
exists precisely because the author pushes to `master` directly (see the workflow's comment), so the same gap exists
every night.

## Fix

1. Add a second job `compile` to `tests.yml` on `ubuntu-latest`, with the same checkout and JDK step as `test`, running

   ```
   ./gradlew --continue \
     :app:android:assembleDebug \
     :app:desktop:compileKotlin \
     :tools:screenshots:compileKotlin \
     :app:web:compileProductionExecutableKotlinWasmJs
   ```

   `:app:android:assembleDebug` compiles every library's `androidMain` (the runner image ships the Android SDK). Add
   `:app:baselineprofile`'s compile task too — find its exact name with
   `./gradlew :app:baselineprofile:tasks --all | grep -i compile.*Kotlin` (it is a `com.android.test` module, so its
   variant names come from the baseline-profile plugin). Leave `wasmJsBrowserDistribution` out: its Binaryen pass is
   slow and `publish-web.yml` runs it anyway; `compileProductionExecutableKotlinWasmJs` catches the compile errors.
2. Add a job `compile-ios` on `macos-latest` running `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`, which
   compiles every library's `iosMain` and the `:app:ios` framework (the same check the root `CLAUDE.md` Build section
   names). Kotlin/Native needs memory: give it the `-Xmx6g` the iOS publish job uses
   (`echo 'org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8' >> ~/.gradle/gradle.properties`, as `publish-ios.yml` does).
   Run it on the triggers chosen in the Decision.
3. Because `publish-all.yml` calls `tests.yml` with `workflow_call` before any store build (`needs: [prepare, test]`),
   both new jobs become part of the release gate automatically: a platform that does not compile now stops every store
   build rather than one of them. Say so in the workflow's header comment and in the root `CLAUDE.md` sentence
   "`.github/workflows/tests.yml` runs all three — every module's `desktopTest`, the Node tests and the Python one".
4. Upload nothing on success; on failure the Gradle log is enough (no reports exist for compile tasks).

## Tests

No new unit tests. The change is verified by the workflow itself: open a PR with the change and confirm both jobs run
green; then, on a throwaway branch, introduce a deliberate compile error in one `iosMain` file and one `androidMain`
file and confirm the matching job fails (do not merge that branch).

## Manual check

None — covered by the workflow run. After merging, check the next nightly run on `master` shows the new jobs.

## Decision

When the macOS job runs (the repository is public, so macOS minutes are free, but macOS runners queue longer and the
job takes ~10 minutes):

1. **Recommended — on every trigger** (pull requests, the nightly schedule and `workflow_call` from `publish-all.yml`):
   the release gate is where it matters most, and PRs are rare in this repo.
2. Nightly and `workflow_call` only (`if: github.event_name != 'pull_request'`): PRs stay fast; a PR that breaks
   `iosMain` is caught the following night or at release.
3. On pull requests only when a path that iOS compiles changed (`paths` filter on `**/iosMain/**`, `**/commonMain/**`,
   `app/ios/**`, `gradle/**`, `*.gradle.kts`), plus nightly and `workflow_call` — needs the job split into its own
   workflow file, since `paths` filters apply per workflow.
