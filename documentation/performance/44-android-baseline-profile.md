<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 44 — Generate and ship a Baseline Profile for the Android app

| | |
|---|---|
| Lane | F |
| Impact | medium |
| Confidence | high (that it is missing); medium (on the size of the win, which the verification step measures) |
| Platforms | Android |
| Files | `settings.gradle.kts`, `gradle/libs.versions.toml`, `app/android/build.gradle.kts`, `app/baselineprofile/build.gradle.kts` (new), `app/baselineprofile/src/main/kotlin/com/pandulapeter/campfire/baselineprofile/BaselineProfileGenerator.kt` (new), `app/baselineprofile/CLAUDE.md` (new), `app/android/src/main/generated/baselineProfiles/baseline-prof.txt` (generated, committed), `app/android/src/main/generated/baselineProfiles/startup-prof.txt` (generated, committed), `app/android/CLAUDE.md`, `CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 45 (`app/android/build.gradle.kts`, `gradle/libs.versions.toml`, `app/android/CLAUDE.md`) and 48 (`app/android/CLAUDE.md`). Land it **after** 45, 48 and 32, because the profile it records should describe the startup path those plans leave behind. |
| Commit message | `Generate a Baseline Profile for the Android app and ship it with the release build.` |

## Problem
The release APK carries only the profiles its libraries ship. None of them cover the app's own code.

- **No profile tooling of the app's own.** `app/android/build.gradle.kts` applies only `android.application` and `compose.compiler` (lines 12–15). There is no `androidx.baselineprofile` plugin, no generator module (`settings.gradle.kts:87–104` lists none) and no `baseline-prof.txt` anywhere in the repository (checked with grep).
- **The libraries' profiles do ship.** profileinstaller comes in transitively: the merged release manifest has `androidx.profileinstaller.ProfileInstallerInitializer` and `ProfileInstallReceiver`. The APK `app/android/build/intermediates/apk/release/android-release.apk` contains `assets/dexopt/baseline.prof` (11,956 bytes) and `baseline.profm`.
- **What that merged profile holds.** `app/android/build/intermediates/merged_art_profile/release/mergeReleaseArtProfile/baseline-prof.txt` has 6,377 lines:
  - 1,665 for `androidx/compose`, and wildcard rules for Material3.
  - Hundreds for view-system libraries the app never uses (fragment 451, appcompat 500+, constraintlayout 320+).
  - **0** for `com/pandulapeter`, **0** for `androidx/navigation3`, and none for Koin, kotlinx.serialization, compose-resources or `sh.calvin.reorderable`.
- **What that costs.** Everything a cold start executes that belongs to the app runs interpreted or JIT-compiled on early launches, until background dexopt catches up (or cloud profiles arrive; the Play listing takes an APK, see the root `CLAUDE.md`). That covers:
  - `CampfireViewModel` and the Koin graph.
  - ChordPro parsing and the library scan in `SongLocalSourceImpl`.
  - `CampfireApp`, `NavDisplay`, the Songs list and the song details layout.

  The result is slower time to first frame and jank on the first scroll and the first navigation. It is also the case after every update, since an update throws the previous compilation away.
- **No startup profile either.** Without one, R8 cannot lay out the DEX file so that startup classes sit together.

## Fix
1. **Version catalog** (`gradle/libs.versions.toml`), following the file's style of one release-notes link per version:
   - `androidx-benchmark`: the newest stable `androidx.benchmark` whose release notes state support for AGP 9.x. The project is on AGP 9.4.1. The `androidx.baselineprofile` Gradle plugin shares this version.
   - `androidx-profileinstaller`: newest stable.
   - `androidx-test-junit` (`androidx.test.ext:junit`) and `androidx-test-uiautomator` (`androidx.test.uiautomator:uiautomator`).
   - Libraries: `androidx-benchmark-macro-junit4`, `androidx-profileinstaller`, `androidx-test-junit`, `androidx-test-uiautomator`.
   - Plugins: `android-test = { id = "com.android.test", version.ref = "gradle" }` and `androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "androidx-benchmark" }`.
   - If no stable `androidx.baselineprofile` supports AGP 9.4, stop and use the fallback in step 6 instead.
2. **New module `:app:baselineprofile`.** Add it to `include(...)` in `settings.gradle.kts`, keeping the list alphabetical. It is a plain `com.android.test` module, like `:app:android` a plain Android module, so no convention plugin applies to it.

   ```kotlin
   plugins {
       alias(libs.plugins.android.test)
       alias(libs.plugins.androidx.baselineprofile)
   }

   android {
       namespace = "com.pandulapeter.campfire.baselineprofile"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       defaultConfig {
           minSdk = libs.versions.android.minSdk.get().toInt()
           targetSdk = compileSdk
           testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
       targetProjectPath = ":app:android"
   }

   // The profile is recorded on whatever device is connected (the local emulator, see app/baselineprofile/CLAUDE.md),
   // never on CI.
   baselineProfile { useConnectedDevices = true }

   dependencies {
       implementation(libs.androidx.benchmark.macro.junit4)
       implementation(libs.androidx.test.junit)
       implementation(libs.androidx.test.uiautomator)
   }

   kotlin { jvmToolchain(libs.versions.jvmTarget.get().toInt()) }
   ```

   AGP 9 compiles Kotlin itself, which is why `:app:android` applies no Kotlin plugin; do the same here. Give every new file the MPL header (the `code-style` skill).
3. **The generator**, `BaselineProfileGenerator.kt`. It is one `@Test` with `BaselineProfileRule.collect(packageName = "com.pandulapeter.campfire", includeInStartupProfile = true)`, walking the paths a user takes on every launch:
   1. `pressHome()`, then `startActivityAndWait()`. A fresh install is a first run, so the demo library is planted and the welcome sheet opens over it (see the root `CLAUDE.md`). Dismiss it with `device.pressBack()`.
   2. Wait for the demo song "House of the Rising Sun" by text (`device.wait(Until.hasObject(By.textContains("Rising Sun")), …)`). Fling the list down and back up.
   3. Open that song, wait, fling the details screen, then press back.
   4. Open the **Setlists** tab (`By.text("Setlists")`) and open the demo setlist ("Getting started").
   5. Go back, then open **Settings** (`By.text("Settings")`).

   Select by visible English text: Compose exposes text to UI Automator, so no test tags are needed, and the emulator runs in English. Keep each step tolerant (`device.wait(..., 5_000)` and skip the step when the text is missing), so that a renamed string costs a step of the profile rather than the run.
4. **Consumer side**, `app/android/build.gradle.kts`:
   - `alias(libs.plugins.androidx.baselineprofile)` in `plugins`.
   - `implementation(libs.androidx.profileinstaller)`, stated explicitly rather than relied on transitively.
   - `baselineProfile(project(":app:baselineprofile"))`.
   - And:

   ```kotlin
   baselineProfile {
       // Recorded by hand on an emulator and committed; a release build, CI's included, only reads the files.
       automaticGenerationDuringBuild = false
       saveInSrc = true
       mergeIntoMain = true
       dexLayoutOptimization = true
   }
   ```

   The plugin adds `nonMinifiedRelease` and `benchmarkRelease` build types to the app. They copy the `release` signing config, which `gradle.properties` defaults to the committed debug keystore, so a fresh clone still builds every variant.
5. **Record the profile and commit it.** Follow the memory note `android-emulator-verification`.
   1. Boot `Resizable_Experimental` with `~/Library/Android/sdk/emulator/emulator -avd Resizable_Experimental -no-snapshot-load -no-boot-anim` and wait for `sys.boot_completed`. If the user's own `Pixel_10_Pro_XL` is running too, set `ANDROID_SERIAL` to ours.
   2. **Uninstall `com.pandulapeter.campfire` first.** The note says that emulator carries an old release build under that id, which is the one the generator installs over, with a different signature.
   3. Run `./gradlew :app:android:generateBaselineProfile`. The exact task name is whatever `./gradlew :app:android:tasks --all | grep -i baselineprofile` lists.
   4. Commit the two files it writes: `app/android/src/main/generated/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`.
   5. Check the result holds `Lcom/pandulapeter/campfire/` rules, `androidx/navigation3` rules and Koin rules.
6. **Fallback, only if step 1 finds no compatible plugin.** Hand-write `app/android/src/main/baseline-prof.txt` with wildcard rules:

   ```
   HSPLcom/pandulapeter/campfire/**->**(**)**
   HSPLandroidx/navigation3/**->**(**)**
   HSPLorg/koin/**->**(**)**
   HSPLkotlinx/serialization/**->**(**)**
   ```

   AGP already expands wildcards before R8 renames anything (`expandReleaseArtProfileWildcards` is in the build today). Say in the commit that this is the stopgap.
7. **Documentation, in the same commit:**
   - **Root `CLAUDE.md`, architecture tree:** add `app:baselineprofile`, the profile generator for `:app:android`.
   - **Root `CLAUDE.md`, Conventions:** the bullet "`:app:android` is a plain Android module, …; every other module … is a multiplatform library" must name `:app:baselineprofile` as the second plain Android module.
   - **Root `CLAUDE.md`, "Only pure logic is tested":** say that the generator drives the UI but is not a test and is not run by CI.
   - **Root `CLAUDE.md`, Build section:** add the generate command, and "regenerate when the startup path or the main screens change noticeably; a stale profile is only less useful, never wrong".
   - **`app/android/CLAUDE.md`:** the build-types paragraph gains the two plugin-made build types and the committed profile.
   - **`app/baselineprofile/CLAUDE.md` (new, as every module has one):** what the journey does, the emulator recipe, and the uninstall pitfall.
   - **CI:** no workflow changes. `publish-android.yml:164` runs `:app:android:assembleRelease`, which with `automaticGenerationDuringBuild = false` needs no device and packages the committed files. Confirm this in verification rather than assuming it. `release.yml` calls nothing new.

   Must not change: the app's behavior, the `release` signing setup, the committed-defaults story of `gradle.properties`, or anything CI does beyond packaging the profile.

## Verification
- `./gradlew :app:android:assembleRelease` with no device connected must succeed. `unzip -l` of the APK must show `assets/dexopt/baseline.prof` larger than today's 11,956 bytes. `intermediates/merged_art_profile/release/.../baseline-prof.txt` must contain `com/pandulapeter` lines, renamed consistently with `mapping.txt`.
- `./gradlew :app:android:assembleDebug` still builds, and the Android run configuration still starts the app.
- **Measure the win** on the emulator, for the APK before this commit and after:
  1. `adb install -r <apk>`.
  2. Install the profile: `adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_BASELINE_PROFILE -n com.pandulapeter.campfire/androidx.profileinstaller.ProfileInstallReceiver`.
  3. Compile against it: `adb shell cmd package compile -f -m speed-profile com.pandulapeter.campfire`.
  4. Ten times: `adb shell am force-stop com.pandulapeter.campfire; adb shell am start -W -n com.pandulapeter.campfire/.CampfireMainActivity`, and average `TotalTime`.
  5. Expect a clear drop with the new profile (typically 15–30% for Compose apps). Record the numbers in the commit's PR or notes, not in code.
- Optional: add a `StartupBenchmark` (`MacrobenchmarkRule`, `StartupTimingMetric`, comparing `CompilationMode.None()` with `CompilationMode.Partial(BaselineProfileMode.Require)`) to the same module for a repeatable number.
