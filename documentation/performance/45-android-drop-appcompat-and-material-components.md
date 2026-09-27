<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 45 — Drop AppCompat and Material Components from the Android shell

| | |
|---|---|
| Lane | F |
| Impact | low |
| Confidence | high (that nothing needs them); medium (on the size of the startup win) |
| Platforms | Android |
| Files | `app/android/build.gradle.kts`, `gradle/libs.versions.toml`, `app/android/src/main/java/com/pandulapeter/campfire/CampfireMainActivity.kt`, `app/android/src/main/res/values/themes.xml`, `app/android/src/main/res/values-night/themes.xml`, `app/android/src/main/res/values/colors.xml`, `app/android/src/main/res/values-night/colors.xml` (new), `app/android/src/main/res/drawable/ic_notification_sync.xml`, `app/android/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 44 (`app/android/build.gradle.kts`, `gradle/libs.versions.toml`, `app/android/CLAUDE.md`) and 48 (`app/android/CLAUDE.md`). Land before 44, so its profile records the lighter activity. |
| Commit message | `Replace AppCompat and Material Components in the Android shell with the platform's own activity and theme.` |

## Problem
The Android shell uses AppCompat and Material Components for exactly two things, and both are view-system extras that a Compose-only app pays for on every start.

- **Where they are used.** `app/android/build.gradle.kts:23` has `implementation(libs.androidx.appCompat)` and `:25` has `implementation(libs.google.material)`. They serve only:
  - `CampfireMainActivity.kt:33`: `class CampfireMainActivity : AppCompatActivity()`.
  - `res/values/themes.xml:13` and `res/values-night/themes.xml:13`: `<style name="Campfire" parent="Theme.Material3.DayNight.NoActionBar">`.
- **Nothing else uses them.** Grep finds no other use in any `.kt` or `.xml` of the project: no `AppCompatDelegate`, `setApplicationLocales`, `FragmentActivity`, `supportFragmentManager` or Material Components widget. `CampfireAndroidApp` casts `LocalActivity.current as? ComponentActivity`, and the file picker uses Activity Result APIs, both of which `ComponentActivity` provides.
- **What the activity costs.** Every activity creation, whether a cold start or a recreation (see 48), makes `AppCompatActivity`:
  - install the `AppCompatDelegate` LayoutInflater factory;
  - apply its own night-mode handling;
  - inflate its sub-decor (`abc_screen_simple` plus `ContentFrameLayout`) when `setContent` calls `setContentView`.
- **What the theme costs.** The Material Components theme resolves a much larger attribute set than a platform theme.
- **Not a big APK win.** `koin-android` 4.2.2 depends on `androidx.appcompat:appcompat` itself (`:app:android:dependencies --configuration releaseRuntimeClasspath`, line "io.insert-koin:koin-android:4.2.2 → androidx.appcompat:appcompat:1.7.1 -> 1.8.0"), and play-services-basement brings `fragment`. So AppCompat stays on the classpath and R8 decides what survives. The win is the per-start work above, plus Material Components and `constraintlayout` (which only Material brings) leaving the build.

## Fix
1. **`CampfireMainActivity.kt`.** Change `import androidx.appcompat.app.AppCompatActivity` to `import androidx.activity.ComponentActivity`, and extend `ComponentActivity()`. `setContent`, `enableEdgeToEdge`, `onNewIntent(Intent)`, `isChangingConfigurations` and `getSystemService` all exist on it unchanged.
2. **Themes.** Parent `Campfire` on `android:Theme.Material.Light.NoActionBar` in `values/themes.xml` and on `android:Theme.Material.NoActionBar` in `values-night/themes.xml`. That keeps the day/night split the system resolves today (the `values-night` qualifier, not AppCompat's DayNight). Then, item by item:
   - `colorPrimary` (a Material Components attribute) becomes `android:colorPrimary` with `@color/campfire`. It is what the platform tints the Recents header with, which AppCompat's base theme mapped `colorPrimary` onto until now.
   - `android:statusBarColor`, `android:navigationBarColor`, `android:windowLightStatusBar` and `android:windowLightNavigationBar` are platform attributes. Keep them as they are (all within minSdk 28).
   - `android:windowSplashScreenIconBackgroundColor` (`tools:targetApi="s"`) is a platform attribute. Keep it and its comment.
   - **Pin the window background.** Today it comes from Material3's `android:colorBackground`, which resolves to `m3_ref_palette_neutral98` = `#FEF7FF` (light) and `m3_ref_palette_neutral6` = `#141218` (dark), checked in the merged release resources. It is what API 28–30 show as the starting window, what the Android 12+ splash screen draws behind the icon (`windowSplashScreenBackground` defaults to the window background), and what the frame before the preferences are read is painted in (`CampfireApp.kt` "the window is already painted in the theme's background"). The platform themes would change it to `#FAFAFA` and `#303030`. So:
     - add `campfire_window_background` to `colors.xml` (`#FEF7FF`), with a `values-night/colors.xml` (`#141218`);
     - set both `android:windowBackground` and `android:colorBackground` to it in each theme.

     Keep today's colors. Moving them to the app's own palette (`#FAF8FE` / `#15121C`, as `app/web/.../index.html` uses) is a separate, visible change and not part of this commit.
3. **`res/drawable/ic_notification_sync.xml:15`.** Change `android:tint="?attr/colorControlNormal"` to `?android:attr/colorControlNormal`. The unprefixed attribute is AppCompat's. It still links today only through the transitive AppCompat resources, and it should not depend on a library the app does not ask for. The system draws a notification icon from its alpha alone, so nothing visible changes.
4. **Dependencies.**
   - `app/android/build.gradle.kts`: remove `implementation(libs.androidx.appCompat)` and `implementation(libs.google.material)`.
   - `gradle/libs.versions.toml`: remove the `androidx-appCompat` and `google-material` version and library entries. Check with grep that no other module references them first; none does today.
   - Do **not** try to exclude AppCompat from `koin-android`. Its classes reference AppCompat, and R8's missing-class check would fail the release build.
5. **`app/android/CLAUDE.md`.** "single `AppCompatActivity`" becomes "single `ComponentActivity`". The themes paragraph says the theme is the platform's `Theme.Material`, and why the window background is pinned.

Must not change: the edge-to-edge behavior, the splash screen's icon handling (the `windowSplashScreenIconBackgroundColor` comment's reasoning), the pre-draw hold, intent handling, the launcher aliases, and the colors the system shows before the first frame.

## Verification
- `./gradlew :app:android:assembleDebug :app:android:assembleRelease`. Resource linking must pass with no reference to `Theme.Material3` or an unprefixed AppCompat attribute left. `unzip -l` of the release APK should show no `res/*mtrl*` Material Components resources.
- **Screenshots before and after, on the emulator** (recipe in the memory note `android-emulator-verification`):
  - The cold-start splash on API 37 in light and dark system themes: same background and icon.
  - The app in both themes: same system-bar icons.
  - A Custom Tab opened from Settings → About.
  - The sync notification's icon.
  - If an API 28–30 image is available, the starting window's color there too.
- Cold start with `adb shell am start -W -n com.pandulapeter.campfire.debug/com.pandulapeter.campfire.CampfireMainActivity`, averaged over ten runs, before and after. A release build measures more fairly: `…/com.pandulapeter.campfire/.CampfireMainActivity`. Expect a small drop.
- Rotate the device while in the app, and open a file with "Open with". Both must behave as before.
