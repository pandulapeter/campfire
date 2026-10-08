# Apply the Koin compiler plugin and its two libraries through one campfire-koin convention plugin

**Challenged:** amended — `KoinPlugin` must not assume `campfire-library` was applied first (it waits for the Kotlin Multiplatform plugin with `pluginManager.withPlugin`); step 4 keeps the root `apply false`, the pattern the build already uses for KGP and AGP next to build-logic's `implementation` of them; ordered after the build-cleanup pass that edits the same root plugins block, catalog and convention-plugin code.

**Kind:** build  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `gradle/build-logic/build.gradle.kts` (plugin registration and the Koin compiler plugin's Gradle artifact on
the classpath); new `gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/plugins/KoinPlugin.kt`;
`gradle/libs.versions.toml` (a library alias for the Koin compiler Gradle plugin artifact); the seven module scripts
`app/di/build.gradle.kts`, `data/repository/implementation/build.gradle.kts`,
`data/source/local/implementation/build.gradle.kts`, `data/source/remote/implementation/build.gradle.kts`,
`domain/implementation/build.gradle.kts`, `metronome/implementation/build.gradle.kts`, `presentation/build.gradle.kts`;
root `build.gradle.kts` (`alias(libs.plugins.koin.compiler) apply false`); `gradle/build-logic/CLAUDE.md`; root
`CLAUDE.md` (Conventions → "Koin is wired with Koin Annotations through the Koin compiler plugin … applied by every
module that declares a definition")
**Depends on:** none (land after the build-cleanup pass that removes the `android-library` alias from `libs.versions.toml` and the root `plugins {}` block and moves `commonTest`'s `kotlin("test")` into `campfire-library` — same files; reuse whatever helper that pass adds for source-set dependencies)

## Problem

Seven modules each repeat the same three lines, e.g. `domain/implementation/build.gradle.kts`:

```kotlin
plugins {
    id("campfire-library")
    alias(libs.plugins.koin.compiler)
}
…
        commonMain.dependencies {
            …
            implementation(libs.koin.annotations)
            implementation(libs.koin.core)
```

and only `app/di/build.gradle.kts` configures the plugin:

```kotlin
// A failed graph check is reported as the missing definition; the line advertising an AI service after it is left out.
koinCompiler {
    aiAssist = false
}
```

The rule "a module that declares a definition applies the Koin compiler plugin" is enforced by memory alone, and the
plugin is pinned per exact Kotlin version (the Koin compiler plugin 1.2.1 ↔ Kotlin 2.4.20), so every Kotlin bump
touches a plugin that is applied in seven places with possibly diverging settings.

The non-library modules' repetition the finding also named (`jvmToolchain(libs.versions.jvmTarget…)` in
`:app:android`, `:app:baselineprofile`, `:app:desktop`, `:tools:screenshots`; `compileSdk`/`minSdk` in `:app:android`
and `:app:baselineprofile`) is one or two lines per module across four different plugin types and is **not** worth a
plugin; this plan leaves it alone.

## Fix

1. Add the Koin compiler Gradle plugin's artifact to `gradle/libs.versions.toml` as a library (the plugin marker
   `io.insert-koin.compiler.plugin:io.insert-koin.compiler.plugin.gradle.plugin`, version `koin-compilerPlugin`, or
   the plugin's implementation artifact — check which one Maven Central/the Gradle Plugin Portal publishes) and to
   `gradle/build-logic/build.gradle.kts` as `implementation(...)`. Add `gradlePluginPortal()` to
   `gradle/settings.gradle.kts`' repositories if the artifact is only there.
2. `KoinPlugin` (`campfire-koin`): inside `pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { … }` — so
   the order of `id("campfire-library")` and `id("campfire-koin")` in a module's `plugins {}` does not matter (the
   `KotlinMultiplatformExtension` exists only once KGP is applied, and the Koin compiler plugin is a
   `KotlinCompilerPluginSupportPlugin` that expects KGP) — applies `io.insert-koin.compiler.plugin`, sets
   `aiAssist = false` on the `koinCompiler` extension (harmless outside `:app:di`; one setting everywhere), and adds
   `koin-annotations` and `koin-core` as `implementation` to `commonMain` of the `KotlinMultiplatformExtension`
   (looked up through build-logic's `libs` catalog helper, as `LibraryPlugin` looks up its plugin ids). Register it in
   `gradle/build-logic/build.gradle.kts` like the other two. It does not apply `campfire-library` itself: modules keep
   choosing `campfire-library` or `campfire-compose-library`.
3. In the seven modules replace `alias(libs.plugins.koin.compiler)` with `id("campfire-koin")` and delete the two
   `implementation(libs.koin…)` lines. `:app:di` keeps `api(libs.koin.core)` (its entry points need the types) and
   loses its `koinCompiler { }` block; `:presentation` keeps its extra Koin libraries (`koin-compose`,
   `koin-compose-viewmodel`, `koin-core-viewmodel`). `:data:source:remote:implementation` declares them in a nested
   `commonMain` block (line ~57) — remove only the two lines.
4. Keep `alias(libs.plugins.koin.compiler) apply false` in the root `build.gradle.kts`: the build already pairs a root
   `apply false` with build-logic's `implementation` for KGP and AGP (`libs.kotlin`, `libs.gradle`), and keeping the
   same pairing for Koin loads its plugin classes once, from the root classloader every module inherits, so the
   `koinCompiler` extension type `KoinPlugin` configures is the one the applied plugin created. Make sure the catalog
   version the root alias and the build-logic library use is the one `koin-compilerPlugin` entry.
5. Docs: `gradle/build-logic/CLAUDE.md` (third plugin), root `CLAUDE.md` Conventions sentence → "applied through
   `campfire-koin` by every module that declares a definition".

## Tests

No new tests. Guarding: the Koin compiler's compile-time graph check in `:app:di` (a missing definition fails the
build), `./gradlew desktopTest`, and plan 61's compile jobs (Android, iOS, Wasm), which exercise the plugin on every
target.

## Manual check

Start the desktop app once (`./gradlew :app:desktop:run`) and confirm the library loads (Koin graph intact).
Deliberately remove one `@Single` locally and confirm the build still fails with the missing-definition message and
no AI-service line, then restore it.
