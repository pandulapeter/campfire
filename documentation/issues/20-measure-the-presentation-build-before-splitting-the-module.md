# Measure the `:presentation` build before anyone splits the module

**Kind:** build · **Severity:** low · **Priority:** P3 · **Platforms:** all · **Lane:** P (last) · **Effort:** S
**Files:** none committed (results go into the plans `README.md`'s measurements).

**Challenged:** amended — the split recipe is cut: under the four-layer decision `:presentation:song` cannot exist, and
the gate's second half (which files the recompile time went to) is not something a Kotlin build report measures. Past
the 30 s gate the numbers go to the owner as a decision for a plan of its own.

## Problem

`:presentation` is one module of about 137,000 lines of Kotlin in five source sets plus 2.2 MB of resources, compiled
for four targets. The review suggested splitting it for compile time. Nobody has measured whether compile time is a
problem, and a split has real costs: every `internal` declaration used across the cut becomes `public` (nearly
everything in `:presentation` is `internal`), the localization plugin (`com.hyperether.localization`) and every
`strings.xml` must stay in one module, `tools/screenshots/build.gradle.kts` finds its friend module by
`it.absolutePath.contains("/presentation/build/")` (which `presentation/core/build/` does not match), and Kotlin's
incremental compilation already recompiles only the dirty files and their ABI dependents inside one module. Plan 11
already gives the boundaries a split would enforce.

## Fix

### Step 1 — measure (no commit)

On a machine running no other Gradle build (others share this one; check with `pgrep -fl GradleDaemon`), with the
Kotlin build reports on for the run only (`-Pkotlin.build.report.output=file`, reports under
`build/reports/kotlin-build/`), record wall time and the report's compile time for each of
`:presentation:compileKotlinDesktop` and the Android, iOS simulator, and wasmJs Kotlin compile tasks (names from
`./gradlew :presentation:tasks --all | grep -i 'compile.*kotlin'`):

1. cold: after `./gradlew :presentation:clean`, with the configuration cache warm;
2. a body-only edit in one screen (a string literal inside `screens/songs/SongsScreen.kt`);
3. an ABI edit in a shared component (a defaulted parameter added to `components/songCard/SongListItem.kt`);
4. the same as 2 in the `ui` package (`ui/CampfireViewModel.kt`).

Three runs each, the median kept. Write the table into the README's measurements.

### Step 2 — the gate

If case 2 or 3 on the desktop target takes over 30 s of Kotlin compile time, report the table to the owner as a
decision: a split plan written then, against the code as it is. Otherwise record "measured, not split" with the
numbers and close the plan.

Why no split recipe here: with plan 11's four layers (no music layer, by decision) the only cut that exists is
foundation + components below the rest, and an ABI edit there still recompiles every dependent in the module above;
which files a Kotlin build report recompiled is not something it attributes time to, so a finer gate would be guessed.
Costs any split would meet are noted for that plan: `internal` across the cut becomes `public`, the strings and the
localization plugin stay in one module (`publicResClass`), and `tools/screenshots/build.gradle.kts` finds its friend
module by `"/presentation/build/"`.

## Tests

None: nothing is committed.
