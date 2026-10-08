# Keep JvmFileStorage once, in a source set shared by androidMain and desktopMain, declared by the campfire-library convention plugin

**Challenged:** amended — match the Android target by `KotlinMultiplatformAndroidLibraryTarget` (already imported by the convention code) rather than by an assumed `androidJvm` platform type, and say where the call goes so both convention plugins get it.

**Kind:** build  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** medium  ·  **Platforms:** android, desktop
**Files:** `gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/extensions/KotlinMultiplatform.kt` (`configureKotlinMultiplatform`); `data/source/local/implementation/src/androidMain/.../storage/file/JvmFileStorage.kt` and `.../src/desktopMain/.../storage/file/JvmFileStorage.kt` (one deleted, one moved to `src/jvmSharedMain/…`); optionally the identical actuals listed under Fix step 4; root `CLAUDE.md` (Conventions bullet on source sets), `data/source/local/implementation/CLAUDE.md`
**Depends on:** none

## Problem

`JvmFileStorage` (307 lines — the atomic temp-file-then-move writes, the Windows name rules, the backup exclusion
no-op) exists twice, byte for byte apart from one KDoc word, and its KDoc says why:

```kotlin
 * The Android copy of this class is identical: the `campfire-library` convention plugin declares no source set shared
 * by `androidMain` and `desktopMain`, so a JVM class both need is kept twice, and a change to one is made to the other.
```

`diff` at 2940b0e0a shows only that word differs. `JvmFileStorageTest` runs in `desktopTest` only, so a fix made to the
desktop copy and forgotten in the Android one is untested on the platform with most users. The same convention gap
duplicates a few tiny actuals byte for byte too: `:data:model`'s `Normalization.android.kt`/`.desktop.kt`, and
`:presentation`'s `CalendarLocale`, `NumericImeOptions`, `LanguageNames` `.android.kt`/`.desktop.kt` pairs.

## Fix

The Kotlin default hierarchy template has no group for "Android + JVM", so the shared source set has to be declared.
`configureKotlinMultiplatform` uses the AGP KMP library target (`KotlinMultiplatformAndroidLibraryTarget`, plugin
`com.android.kotlin.multiplatform.library`), which the hierarchy DSL's `withAndroidTarget()` may not match.

1. In `configureKotlinMultiplatform`, after the targets are declared, extend the default template rather than replace it
   (writing `dependsOn` by hand switches the default template off for the whole module):

   ```kotlin
   @OptIn(ExperimentalKotlinGradlePluginApi::class)
   applyDefaultHierarchyTemplate {
       common {
           group("jvmShared") {
               withJvm()
               // The AGP KMP library target, matched by its type - the class configureKotlinMultiplatform already
               // imports - rather than by its compilations' platform type, which the plan cannot vouch for.
               withCompilations { it.target is KotlinMultiplatformAndroidLibraryTarget }
           }
       }
   }
   ```

   `applyDefaultHierarchyTemplate` is called on `extension` inside `configureKotlinMultiplatform`, after `jvm("desktop")`,
   so `campfire-compose-library` (which goes through the same function) gets the set as well. Verify with `./gradlew :data:source:local:implementation:sourceSets` (or the IDE) that `jvmSharedMain` exists and
   that `androidMain` and `desktopMain` depend on it, and that `iosMain`, `nativeMain` and `wasmJsMain` are unchanged.
   If `withCompilations` does not catch the Android target in this AGP version, fall back to the per-module form in
   `data/source/local/implementation/build.gradle.kts` only:
   `val jvmSharedMain by creating { dependsOn(commonMain.get()) }; androidMain.get().dependsOn(jvmSharedMain); desktopMain.get().dependsOn(jvmSharedMain)`
   — and check that the iOS and web source sets still resolve (the manual `dependsOn` turns the template off for that
   module, so `iosMain` may need declaring too). The convention-plugin form is recommended: every module gets the set
   for free, and nothing else changes for modules that leave it empty.
2. Move `desktopMain/.../storage/file/JvmFileStorage.kt` to `src/jvmSharedMain/kotlin/.../storage/file/JvmFileStorage.kt`,
   delete the Android copy, and replace the KDoc paragraph above with "Shared by Android and desktop through the
   `jvmShared` source set". `java.io.File` is available there (both targets are JVM). `FileStorage.android.kt` and
   `FileStorage.desktop.kt` (different roots) stay where they are.
3. Build and test: `./gradlew :data:source:local:implementation:desktopTest :app:android:assembleDebug :app:desktop:run`
   (smoke), `:app:ios:linkDebugFrameworkIosSimulatorArm64`, and the web compile, to see no other target noticed.
4. Optional follow-up commit: move the byte-identical actuals listed in Problem into `jvmSharedMain` of their modules
   (an `actual` in an intermediate source set serves both targets). Only those that `diff` reports identical.
5. Update the root CLAUDE.md Conventions bullet ("platform code goes in `androidMain` / `desktopMain` / `iosMain` /
   `wasmJsMain`…") to mention `jvmSharedMain` for code both JVM targets share.

Steps 1–3 are one commit (the build change has no observable effect until something lives in the set).

## Tests

None new. Guard: `JvmFileStorageTest` (desktopTest) now covers the one copy Android also runs, plus the four app
builds.

## Manual check

Android (debug build on the emulator): create, edit, rename and delete a song; import a zip; kill and relaunch — the
library is intact. Desktop: the same quick pass.
