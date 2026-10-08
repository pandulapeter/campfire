<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :build-logic

The convention plugins the library modules apply, in an **included build** of their own
(`includeBuild("gradle")` in the root `settings.gradle.kts`). It is a separate build rather than `buildSrc` so that
editing a plugin does not invalidate the whole main build, and `gradle/settings.gradle.kts` gives it the same
`libs.versions.toml` the app uses — one catalog, read from both sides.

- `campfire-library` (`LibraryPlugin`) — Kotlin Multiplatform plus the **new** Android KMP target
  (`com.android.kotlin.multiplatform.library`, not `com.android.library`), then `configureKotlinMultiplatform`.
- `campfire-compose-library` (`ComposeLibraryPlugin`) — the same, plus the Compose and Compose compiler plugins, and
  `androidResources.enable = true`, which the KMP Android target leaves off by default. It also hands the Compose
  compiler `gradle/compose-stability.conf`, which names the classes of the `:chordpro` model as stable: that module is
  not compiled with the Compose compiler, so without it every parameter typed with the model would be compared by
  instance and a new parse would recompose every line of a song. The file names each class on its own, never a
  wildcard, and leaves `kotlin.collections` out. Reaching the typed extension is why `compose-compiler-gradle-plugin`
  (`kotlin-composeCompiler`, versioned with Kotlin) is on this build's classpath.
- `campfire-koin` (`KoinPlugin`) — the Koin compiler plugin (`io.insert-koin.compiler.plugin`) with `aiAssist` off,
  and `koin-annotations` and `koin-core` in `commonMain`, for every module that declares a definition. It is applied
  next to one of the two above, in either order: it waits for Kotlin Multiplatform with `pluginManager.withPlugin`. The
  plugin's Gradle artifact (`koin-compilerPlugin` in the catalog) is on this build's classpath for its typed
  `koinCompiler` extension, and the root `build.gradle.kts` keeps its `apply false` so that the extension type comes
  from one classloader, as it does for KGP and AGP. A module adds only its other Koin libraries itself
  (`:presentation`'s Compose ones, `:app:di`'s `api` of `koin-core`).
- `campfire-style` (`StylePlugin`) — ktlint through Spotless (`spotlessCheck`, `spotlessApply`), for `src/**/*.kt` and
  the module's `*.gradle.kts`, found by path so that every kind of module is covered alike. The two library plugins
  apply it; the plain `:app:*` modules and `:tools:screenshots` name it in their own `plugins {}`, never a root
  `subprojects {}` block, which would be cross-project configuration. Which ktlint rules run is the root
  `.editorconfig`'s business (`ktlint_standard_<rule> = disabled`), the ktlint version the catalog's `ktlint`.

`extensions/KotlinMultiplatform.kt` is where the shared configuration lives, and where a new target or a new
platform-wide compiler setting belongs — never in a module's own `build.gradle.kts`:

- The targets: Android, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64` and `wasmJs { browser() }`. A module gets
  all five or none; the four platform `:app:*` modules are single-platform and therefore do **not** apply these
  plugins, while `:app:di`, which every one of them starts Koin through, does.
- `archivesName` is derived from the Gradle path (`:data:source:local:api` → `data-source-local-api`). A klib carries
  the name of the artifact it is built into, and half the modules here are called `api` or `implementation`, so the
  default would have several of them claiming the same identity.
- The Android namespace is derived from the same path, so a module only declares one when it wants something else.
- The Android target has host tests enabled (`withHostTest {}`), which is what makes it compile `commonTest` rather
  than warn about every module that has one. The tests are still run on the desktop target (root `CLAUDE.md`).
- `commonTest` depends on `kotlin("test")` in every module, so a module adds only what its tests need beyond it
  (`kotlin-coroutines-test`, `ktor-client-mock`); a module with no tests carries a dependency nothing compiles.
- Every version number comes from the catalog through `extensions/VersionCatalog.kt` (`jvmTarget` for the toolchain,
  `android-minSdk` / `android-compileSdk` for the Android target). Nothing here hardcodes a version; bumping one is
  an edit to `gradle/libs.versions.toml` alone.

The plugin ids are registered in `build-logic/build.gradle.kts`; adding another convention plugin means a class, a
`register` block there, and nothing else.
