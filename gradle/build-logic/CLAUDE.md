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

The two convention plugins every library module applies, in an **included build** of their own
(`includeBuild("gradle")` in the root `settings.gradle.kts`). It is a separate build rather than `buildSrc` so that
editing a plugin does not invalidate the whole main build, and `gradle/settings.gradle.kts` gives it the same
`libs.versions.toml` the app uses — one catalog, read from both sides.

- `campfire-library` (`LibraryPlugin`) — Kotlin Multiplatform plus the **new** Android KMP target
  (`com.android.kotlin.multiplatform.library`, not `com.android.library`), then `configureKotlinMultiplatform`.
- `campfire-compose-library` (`ComposeLibraryPlugin`) — the same, plus the Compose and Compose compiler plugins, and
  `androidResources.enable = true`, which the KMP Android target leaves off by default.

`extensions/KotlinMultiplatform.kt` is where the shared configuration lives, and where a new target or a new
platform-wide compiler setting belongs — never in a module's own `build.gradle.kts`:

- The targets: Android, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64` and `wasmJs { browser() }`. A module gets
  all five or none; the three `:app:*` modules that are single-platform therefore do **not** apply these plugins.
- `archivesName` is derived from the Gradle path (`:data:source:local:api` → `data-source-local-api`). A klib carries
  the name of the artifact it is built into, and half the modules here are called `api` or `implementation`, so the
  default would have several of them claiming the same identity.
- The Android namespace is derived from the same path, so a module only declares one when it wants something else.
- Every version number comes from the catalog through `extensions/VersionCatalog.kt` (`jvmTarget` for the toolchain,
  `android-minSdk` / `android-compileSdk` for the Android target). Nothing here hardcodes a version; bumping one is
  an edit to `gradle/libs.versions.toml` alone.

The plugin ids are registered in `build-logic/build.gradle.kts`; adding a third convention plugin means a class, a
`register` block there, and nothing else.
