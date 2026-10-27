<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Ship the desktop's QuickJS library in the app image and load it from there, and take every platform's copy out of the release jar

**Kind:** packaging · **Severity:** medium · **Platforms:** desktop (macOS, Windows, Linux)
**Challenged:** amended — `tasks.named("prepareAppResources")` would fail the script (Compose registers its
application tasks in `afterEvaluate`), so it is reached through `tasks.matching { … }.configureEach` as the script's other
hooks are; the library goes into `appResourcesRootDir`'s `common/` folder (only the host's is extracted anyway), so no
Compose target id has to be derived; a host the jar has no library for (Windows on ARM) copies nothing instead of
failing `run`; the jar is picked by component id (its file is `quickjs-jvm.jar`); the Compose and loader behaviour the
plan relies on is now verified against the 1.12.2 plugin sources and the 1.0.15 jar.
**Files:** `app/desktop/build.gradle.kts`,
`app/desktop/src/main/kotlin/com/pandulapeter/campfire/CampfireDesktopApplication.kt`, `app/desktop/CLAUDE.md`

## Problem

On the desktop, abc2svg runs in quickjs-kt (`io.github.dokar3:quickjs-kt-jvm` 1.0.15), whose jar carries a native
library for each of five platforms (`jni/linux_aarch64/libquickjs.so`, `jni/linux_x64/libquickjs.so`,
`jni/macos_aarch64/libquickjs.dylib`, `jni/macos_x64/libquickjs.dylib`, `jni/windows_x64/libquickjs.dll`; 4.46 MB
uncompressed together). Its `com.dokar.quickjs.NativeLibraryLoader.loadLibrary("quickjs")`, read from the jar's
bytecode, tries in this order:

1. `System.loadLibrary("quickjs")` (the `java.library.path`; on Windows that name means `quickjs.dll`);
2. if the system property `com.dokar.quickjs.library.path` is set, `System.load` of `<that folder>/libquickjs.<so|dylib|dll>`,
   **throwing an `IllegalStateException` if the file is not there** (no fall-through);
3. `<java.home>/bin/libquickjs.dll` on Windows, `<java.home>/lib/libquickjs.<so|dylib>` elsewhere, if it exists;
4. otherwise it copies the jar resource `jni/<linux|macos|windows>_<aarch64|x64>/libquickjs.<ext>` to
   `Files.createTempFile("quickjs_…", …)` in `java.io.tmpdir`, marks it `deleteOnExit()`, and loads that.

The packaged app has none of the first three, so every launch that renders a tune takes the fourth:

- **Windows** leaves an ~860 KB `quickjs_*.dll` in `%TEMP%` per session: `deleteOnExit` runs while the DLL is still
  mapped into the process, and Windows refuses to delete a mapped file.
- **The Mac App Store build** loads its only native library from outside the signed bundle (the container's temporary
  folder), and the jar inside the bundle carries Mach-O binaries for both Mac architectures plus Linux and Windows
  binaries, the kind of thing the store's validation looks for. `app/desktop/build.gradle.kts` already strips skiko's
  other-architecture dylib from the jar for exactly this reason, in the `proguardReleaseJars` `doLast` that returns
  early on any host but a Mac:

```kotlin
tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
    val proguardOutput = layout.buildDirectory.dir("compose/tmp/main-release/proguard")
    doLast {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return@doLast
        val otherArchitecture = if (System.getProperty("os.arch") == "aarch64") "x64" else "arm64"
        val unused = listOf("libskiko-macos-$otherArchitecture.dylib", "libskiko-macos-$otherArchitecture.dylib.sha256")
```

- Every installer carries ~3.6 MB of other platforms' QuickJS binaries.

## Fix

Put the host's library into the image as an app resource, point the loader at it through step 2, and strip `jni/` from
the release jar.

1. **An extraction task** in `app/desktop/build.gradle.kts`: a `Sync` task (e.g. `extractQuickJsLibrary`) that takes
   the `quickjs-kt-jvm` jar from the `runtimeClasspath` (an `artifactView` with a `componentFilter` for the module
   `io.github.dokar3:quickjs-kt-jvm`, never a file name: the jar is `quickjs-jvm.jar`; it arrives through `:app:di` →
   `:abc:implementation`), copies `jni/<host folder>/libquickjs.*` out of its `zipTree` flattened into
   `build/quickjsLibrary/common/` (only the host's library is extracted, so the `common` folder, which Compose takes
   on every host, needs no Compose target id), and fails the build in a `doLast` when it copied nothing (as the ANGLE
   copy checks its names). Host folder from `os.name` / `os.arch`, as the loader names them: `macos_aarch64`,
   `macos_x64`, `windows_x64`, `linux_x64`, `linux_aarch64` (`amd64` and `x86_64` are `x64`). **On any other host
   (Windows on ARM, another Linux architecture) copy nothing and do not fail**: the jar has no library for it, so the
   loader fails there anyway and tunes stay text, and failing would break `run`, which `prepareAppResources` feeds, on a
   machine that builds today. Configuration cache: the `doLast` reads only locals (the destination directory as a
   `Provider`/`File`, the host folder as a `String`, whether the host is one of the five), never a script `val`.
2. **Hand it to Compose as app resources**: `nativeDistributions { appResourcesRootDir.set(layout.buildDirectory.dir("quickjsLibrary")) }`
   and `tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(extractQuickJsLibrary) }`. Not
   `tasks.named`: the Compose plugin registers its application tasks in `project.afterEvaluate` (`ComposePlugin.kt`),
   so the task does not exist while the script runs; this is why the script already reaches `proguardReleaseJars` and
   `createReleaseDistributable` through `tasks.matching`. What the plugin does with it (`configureJvmApplication.kt` and
   `AbstractJPackageTask.kt` of 1.12.2): `prepareAppResources` is a `Sync` of `common/`, `<os>/`, and `<os>-<arch>/`;
   `run` and `runRelease` depend on it and pass `-Dcompose.application.resources.dir=<its folder>`; every package
   copies it into the jpackage input as `resources` and gets `-Dcompose.application.resources.dir=$APPDIR/resources`
   in its launcher. On macOS the file is in the bundle before jpackage runs: JDK 18 and later sign a loose `.dylib` of
   the image themselves (the plugin's `MacJarSignFileCopyingProcessor` says so), and the plugin re-signs the runtime and
   the bundle after it, unlike a `doLast` copy into the finished image, which would break the signature. (The ANGLE
   DLLs can be copied after the image is made only because Windows images are not signed at that step.) Recommended
   over the alternatives: putting the file on `java.library.path` depends on what the jpackage launcher puts there and
   needs `quickjs.dll` on Windows; putting it under `java.home` writes into the runtime image, which the store build
   signs separately with the runtime entitlements.
3. **Point the loader at it** in `main` (`CampfireDesktopApplication.kt`), before `startCampfireDependencyGraph()`,
   next to the ANGLE check: when `com.dokar.quickjs.library.path` is unset and the folder named by
   `compose.application.resources.dir` holds `libquickjs.dylib` / `libquickjs.dll` / `libquickjs.so` (by OS; the loader
   puts `lib` in front on Windows too, read from `NativeLibraryLoader.getLibraryName`), set the property to that
   folder. Only when the file is there, because the loader throws rather than falling through when the property names
   a folder without it (a build on a host with no library, the desktop tests, `tools:screenshots`, which keep the
   temporary copy). A comment says why: the library inside the image is the signed one, and a temporary copy is left
   behind on Windows every session.
4. **Strip `jni/` from the release jar** in the `proguardReleaseJars` `doLast`: restructure it so that on every host
   the `jni` folder is deleted from each output jar (walk `zip.getPath("jni")` deepest first, if it exists; quickjs-kt
   is the only dependency with a `jni/` folder in today's joined jar), and only on a Mac the other architecture's
   skiko files as now. Update the KDoc above it to cover both. `runRelease` still renders (it gets the resources
   folder); the CDS archive is recorded from the image, after this edit, as today.

Debug images (`createDistributable`, no ProGuard) keep the jar's copies, but load the resource first anyway.

`app/desktop/CLAUDE.md`, Packaging: one bullet saying QuickJS's library for the host is an app resource
(`appResourcesRootDir`'s `common/`, extracted from the quickjs-kt jar, nothing on a host it has none for), loaded
through `com.dokar.quickjs.library.path` set in `main`, and stripped from the release jar with every other
platform's, since otherwise it is unpacked into the temporary folder on every launch (left behind on Windows,
outside the signed bundle on the Mac App Store).

## Tests

No unit test can see a packaged image. The extraction task fails the build when the library is missing, which is the
part a dependency update could break silently. The rest is the Manual check.

## Manual check

A mistake here leaves every tune as text in the store builds, or gets the Mac App Store upload rejected.

- **Mac App Store**: build the store configuration and run it sandboxed as the "Testing a sandboxed Mac build locally"
  recipe in the user's store notes says (`packageReleasePkg -Pcampfire.desktop.distribution=mac-app-store`, or the
  `createReleaseDistributable` it is made of; re-sign ad hoc with `--options runtime` and the entitlements minus the two
  App ID keys, a bundle id of its own, put in `~/Applications`). Open a song with an `abc` block: the staff is drawn.
  Then: `codesign -dv` on `Contents/app/resources/libquickjs.dylib` shows it signed; no `quickjs_*` file appears in
  the container's `Data/tmp`; `unzip -l` of the joined jar in `Contents/app` lists no `jni/`. Validate the `.pkg`
  (`xcrun altool --validate-app`) before the next submission.
- **Windows**: install the `.msi` (and the `.msix`), open a tune, close the app: the staff was drawn and `%TEMP%`
  (for the MSIX, the package's `AC\Temp` under `%LOCALAPPDATA%\Packages`) holds no `quickjs_*.dll`.
- **Linux** (`.deb`): a tune draws.
