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

Convention plugins in an **included build** (`includeBuild("gradle")`), not `buildSrc`, so editing a plugin does not
invalidate the main build. `gradle/settings.gradle.kts` shares the app's `libs.versions.toml`. Plugin ids are
registered in `build-logic/build.gradle.kts`; a new convention plugin is a class plus a `register` block.

- `campfire-library` (`LibraryPlugin`): Kotlin Multiplatform plus the **new** Android KMP target
  (`com.android.kotlin.multiplatform.library`, not `com.android.library`).
- `campfire-compose-library` (`ComposeLibraryPlugin`): adds Compose and `androidResources.enable = true` (off by default
  on the KMP Android target). Passes `gradle/compose-stability.conf`, marking the `:chordpro` model stable: that module
  is not built with the Compose compiler, so otherwise a new parse would recompose every line of a song. Name each
  class individually, never a wildcard, and leave `kotlin.collections` out.
- `campfire-koin` (`KoinPlugin`): the Koin compiler plugin (`aiAssist` off, its notes at info severity) plus `koin-annotations` and `koin-core`.
  Applies in either order with the above (waits via `pluginManager.withPlugin`). The root `build.gradle.kts` keeps its
  `apply false` so the extension type comes from one classloader.
- `campfire-style` (`StylePlugin`): ktlint via Spotless for `src/**/*.kt` and the module's `*.gradle.kts`. The library
  plugins apply it; plain `:app:*` modules and `:tools:screenshots` name it in their own `plugins {}` — never a root
  `subprojects {}` (cross-project configuration). Rules are toggled in the root `.editorconfig`.

## Shared configuration (`extensions/KotlinMultiplatform.kt`)

A new target or platform-wide compiler setting goes here, never in a module's build file.

- Targets: Android, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, and `wasmJs { browser() }` — all five or none.
  The four platform `:app:*` modules do not apply these plugins; `:app:di` does.
- `archivesName` and the Android namespace derive from the Gradle path (klibs carry the artifact name, and many modules
  are called `api` or `implementation`).
- Android host tests are on (`withHostTest {}`) so `commonTest` compiles there; tests still run on desktop.
- `commonTest` gets `kotlin("test")` and kotlinx-coroutines-test everywhere, and every test source set opts in to
  `ExperimentalCoroutinesApi` (coroutines-test's virtual time); no test file or module build file does.
- Every version comes from the catalog via `extensions/VersionCatalog.kt`; nothing here hardcodes one.

## Tasks

`tasks/` holds the task classes `:app:desktop` and `:app:web` register, kept here so packaging decisions are testable;
registration and wiring stay in the consuming build file, every project value is a task input set there (so no task
action reaches a script object), and pure parts are tested with `./gradlew -p gradle :build-logic:test`. The apps see
these classes because they apply `campfire-style`.
