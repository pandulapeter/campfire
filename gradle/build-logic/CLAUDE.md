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
invalidate the main build.

- `campfire-library` uses the **new** Android KMP target (`com.android.kotlin.multiplatform.library`, not
  `com.android.library`).
- `campfire-compose-library` passes `gradle/compose-stability.conf`, marking the `:chordpro` model stable: that module
  is not built with the Compose compiler, so otherwise a new parse would recompose every line of a song. Name each
  class individually, never a wildcard, and leave `kotlin.collections` out; `:chordpro`'s `ComposeStabilityListTest`
  checks the names against its public classes.
- `campfire-koin` applies in either order with the above (`pluginManager.withPlugin`). The root `build.gradle.kts`
  keeps its `apply false` so the extension type comes from one classloader.
- `campfire-style`: the library plugins apply it; plain `:app:*` modules and `:tools:screenshots` name it in their own
  `plugins {}` — never a root `subprojects {}` (cross-project configuration). It makes every Kotlin compilation's
  warnings errors: with most compilations restored from the build cache, a warning is printed once and never again.
  Fix the warning; a `@Suppress` only where the code cannot avoid it, saying why.

## Shared configuration (`extensions/KotlinMultiplatform.kt`)

- A new target or platform-wide compiler setting goes here, never in a module's build file.
- Targets: Android, `jvm("desktop")`, `iosArm64`, `iosSimulatorArm64`, and `wasmJs { browser() }` — all five or none.
- `archivesName` and the Android namespace derive from the Gradle path (klibs carry the artifact name, and many modules
  are called `api` or `implementation`).
- Every test source set opts in to `ExperimentalCoroutinesApi` here; no test file or module build file does.
- Every version comes from the catalog; nothing here hardcodes one.

## Tasks

`tasks/` holds the task classes `:app:desktop` and `:app:web` register, kept here so packaging decisions are testable;
registration stays in the consuming build file, every project value is a task input set there (so no task action
reaches a script object). The apps see these classes because they apply `campfire-style`.
