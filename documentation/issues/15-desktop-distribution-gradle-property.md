<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 15 — A desktop build cannot tell which distribution it is

## What the user sees

One desktop build runs on three operating systems, but the same operating system is handed the app from more than one
place: a Mac can have Campfire from the Mac App Store or as the unsigned `.dmg` the GitHub release carries, and a
Windows PC from the Microsoft Store or as the unsigned `.msi`. The build decides which of those it is from `os.name`
alone, so **every** macOS build calls itself the Mac App Store build and **every** Windows build calls itself the
Microsoft Store build.

Two consequences:

1. In Settings, the unsigned `.dmg` marks the Mac App Store row as "Mac App Store · this version" and applies the
   App Review filtering meant for a store build (it hides Google's and Microsoft's rows from somebody who downloaded
   the app from GitHub and has no reason to be shielded from them).
2. The reverse, and the one that matters: **a real Mac App Store build would be allowed to show the donation link**,
   because `canAskForDonations` is hard-coded to `true` on desktop. App Store guideline 3.1.1 forbids pointing at any
   way of paying the developer other than an in-app purchase, and that applies to the Mac App Store exactly as it
   applies to the iOS one. The Mac App Store submission would be rejected, or worse, approved and then pulled.

## Cause

`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt:26-36`,
verified at HEAD `984861e4`:

```kotlin
// The installers are handed out by the project itself, with no store's rules to follow.
internal actual val canAskForDonations = true

// One build for three operating systems, each handed out from somewhere else, so the answer is only known once it
// runs.
internal actual val currentDistribution: Distribution? = when {
    isMacOs -> Distribution.MAC_APP_STORE
    isWindows -> Distribution.MICROSOFT_STORE
    operatingSystem.contains("linux") -> Distribution.LINUX
    else -> null
}
```

The comment states the problem out loud — "each handed out from somewhere else" — and then answers it with the one
thing that cannot tell them apart. Where the app came from is not a property of the machine it ends up on; it is a
property of the build, and therefore something the build has to be told.

## The change

A `campfire.desktop.distribution` Gradle property, generated into a Kotlin constant exactly the way the Dropbox key
already is.

### 1. `gradle.properties` — declare the default

Add, after the `campfire.dropbox.appKey` block:

```properties
# Desktop. Which of the places the desktop app is handed out from a build is for: "download" (the .dmg and .msi the
# GitHub release carries), "mac-app-store", "microsoft-store" or "linux" (the .deb). One build runs on three
# operating systems and the same operating system is served from more than one place, so this is something the build
# is told rather than something it can ask the machine it runs on. It decides whether the app may link to the
# donation page at all - a build that goes through App Review may not (guideline 3.1.1). The default is the direct
# download, which is what a build nobody configured is: `:app:desktop:run`, and the installers the release attaches.
campfire.desktop.distribution=download
```

`local.properties` overrides it like any other `campfire.*` property; no change is needed to `settings.gradle.kts`.

### 2. `presentation/build.gradle.kts` — generate the constant

Follow `generateSyncConfiguration` in `data/source/remote/implementation/build.gradle.kts:18-40` and
`generateVersionFile` in this same file (lines 17-35): a `tasks.register` that reads the property with
`project.property(...)`, declares it as an `inputs.property` and the output directory as `outputs.dir`, and writes a
single `internal const val` in a `doLast`. The one difference is the source set: the version constant is generated
into `commonMain`, this one belongs in `desktopMain`, since no other platform has anything to do with it.

Add next to `generateVersionFile`:

```kotlin
/**
 * Which of the places the desktop app is handed out from this build is for. See `gradle.properties` for why the
 * build has to be told rather than asking the machine it runs on, and `ui/platform/Platform.desktop.kt` for what it
 * decides.
 */
val generateDesktopDistributionFile = tasks.register("generateDesktopDistributionFile") {
    val distribution = project.property("campfire.desktop.distribution").toString()
    // A typo here would otherwise be a build that quietly calls itself the direct download, which is the one value
    // that lets it ask for money.
    require(distribution in DESKTOP_DISTRIBUTIONS) {
        "campfire.desktop.distribution is \"$distribution\", which is not one of ${DESKTOP_DISTRIBUTIONS.joinToString()}."
    }
    val outputDirectory = layout.buildDirectory.dir("generated/distribution/kotlin")
    inputs.property("distribution", distribution)
    outputs.dir(outputDirectory)
    doLast {
        outputDirectory.get().asFile.resolve("com/pandulapeter/campfire/presentation").let { directory ->
            directory.mkdirs()
            directory.resolve("CampfireDistribution.kt").writeText(
                """
                package com.pandulapeter.campfire.presentation

                internal const val CAMPFIRE_DESKTOP_DISTRIBUTION = "$distribution"

                """.trimIndent()
            )
        }
    }
}
```

and at the bottom of the file:

```kotlin
private val DESKTOP_DISTRIBUTIONS = listOf("download", "mac-app-store", "microsoft-store", "linux")
```

Wire it into the desktop source set, which the file does not declare yet — add it next to `androidMain` and
`wasmJsMain` in the `sourceSets` block:

```kotlin
        desktopMain {
            kotlin.srcDir(generateDesktopDistributionFile)
        }
```

Note the `project.property(...)` spelling: inside a `tasks.register { }` block a bare `property(...)` resolves
against the task, as the root `CLAUDE.md` says.

### 3. `Platform.desktop.kt` — read it

Replace lines 26-36 with:

```kotlin
/**
 * What the build was told it is (`campfire.desktop.distribution`), rather than what the machine it is running on
 * would suggest: the same operating system is served from a store and from the project's own download page alike,
 * and the two answer to different rules.
 */
internal actual val currentDistribution: Distribution? = when (CAMPFIRE_DESKTOP_DISTRIBUTION) {
    "mac-app-store" -> Distribution.MAC_APP_STORE
    "microsoft-store" -> Distribution.MICROSOFT_STORE
    "linux" -> Distribution.LINUX
    // "download": the .dmg and .msi the GitHub release carries, and every build nobody configured. No store's rules
    // apply to it, which is exactly what a null says.
    else -> null
}
```

with `import com.pandulapeter.campfire.presentation.CAMPFIRE_DESKTOP_DISTRIBUTION` added. `isMacOs`, `isWindows` and
`operatingSystem` stay where they are — `desktopDataDirectory()` still needs them.

### 4. `canAskForDonations` derives from the distribution

It is an `expect val` today with four hand-written actuals, each repeating the same rule. Make it a single derived
`val` in `commonMain` and delete all four actuals (`Platform.android.kt`, `Platform.ios.kt`, `Platform.desktop.kt`,
`Platform.wasmJs.kt`) — the "never leave anything unused behind" rule in the `code-style` skill.

In `presentation/src/commonMain/.../ui/platform/Platform.kt`, replace the `expect val canAskForDonations`
declaration (lines 27-35) with, placed after `currentDistribution`:

```kotlin
/**
 * Whether the settings screen may offer a link that asks for money, which is decided by the store the build is
 * published on.
 *
 * The App Store and the Mac App Store forbid pointing at any way of paying the developer other than an in-app
 * purchase (guideline 3.1.1), and a tip is such a payment. Play's billing is only required for purchases of digital
 * content, which a donation that buys nothing is not; the Microsoft Store has no such rule; and the direct
 * installers, the Linux package and the web build answer to no store at all.
 */
internal val canAskForDonations get() = currentDistribution?.isApple != true
```

This is what makes the difference the plan is for: a build configured as `mac-app-store` now hides the coffee row,
and the unsigned `.dmg` still shows it.

### 5. The release pipeline passes it

`.github/workflows/desktop-publish.yml`: add a `distribution:` column to every matrix entry and pass it on the
command line. It is not a secret, so a `-P` is the right way (unlike the values plan 20 moves into
`local.properties`).

Matrix (replacing lines 51-75, keeping the surrounding comment):

```yaml
          - name: Linux (amd64)
            runner: ubuntu-22.04
            distribution: linux
            task: packageReleaseDeb
            format: deb
            asset: linux-amd64.deb
          - name: Linux (arm64)
            runner: ubuntu-22.04-arm
            distribution: linux
            task: packageReleaseDeb
            format: deb
            asset: linux-arm64.deb
          - name: macOS (Apple silicon)
            runner: macos-latest
            distribution: download
            task: packageReleaseDmg
            format: dmg
            asset: macos-arm64-unsigned.dmg
          - name: macOS (Intel)
            runner: macos-15-intel
            distribution: download
            task: packageReleaseDmg
            format: dmg
            asset: macos-x64-unsigned.dmg
          - name: Windows
            runner: windows-latest
            distribution: download
            task: packageReleaseMsi
            format: msi
            asset: windows-x64-unsigned.msi
```

(The `ubuntu-22.04` runners are plan 18's change; if plan 18 lands first this matrix already says so, if it lands
second it only edits the `runner:` lines.)

Build step (line 106-110), keeping the comment above it:

```yaml
      - name: Build the installer
        run: |
          ./gradlew :app:desktop:createReleaseDistributable :app:desktop:${{ matrix.task }} \
            -Pcampfire.desktop.distribution=${{ matrix.distribution }} \
            -Pcampfire.dropbox.appKey="${{ secrets.DROPBOX_APP_KEY }}"
          ./gradlew --stop
```

(Plan 20 replaces the `-Pcampfire.dropbox.appKey=` line with a `local.properties` written from `env:`; the
`-Pcampfire.desktop.distribution=` line survives that change unchanged.)

The two store builds have no workflow yet. When the Mac App Store one is added it passes
`-Pcampfire.desktop.distribution=mac-app-store`, and the Microsoft Store one `microsoft-store`; say so in the
comment at the top of `desktop-publish.yml`.

### What the default has to be

`download`. A plain `./gradlew :app:desktop:run`, a `packageDistributionForCurrentOS` on a developer's machine and a
fresh clone with no `local.properties` are none of them a store build, and `download` is the value that claims no
store's rules and allows the donation link — which is what those builds are today. The two values that *remove* a
capability (`mac-app-store`) or name a store the build is not in are the ones that have to be asked for explicitly.

## Tests

None. `:presentation` has no tests and this is build configuration plus four lines of platform glue; the root
`CLAUDE.md` is explicit that only pure logic is tested and that the UI is untested. Run the standard suite anyway to
confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

```bash
# The default: the generated constant says "download", and Settings shows the coffee row.
./gradlew :app:desktop:run
cat presentation/build/generated/distribution/kotlin/com/pandulapeter/campfire/presentation/CampfireDistribution.kt

# A store build: the coffee row is gone from Settings → About.
./gradlew :app:desktop:run -Pcampfire.desktop.distribution=mac-app-store

# A typo fails the build rather than producing a "download" build.
./gradlew :app:desktop:run -Pcampfire.desktop.distribution=appstore   # expect: configuration failure naming the four values

# local.properties overrides it, like every other campfire.* property.
echo 'campfire.desktop.distribution=microsoft-store' >> local.properties
./gradlew :app:desktop:run
```

Needs a Mac only to check the macOS behavior specifically; the property works the same on any host. The workflow half
can only be proven by dispatching `desktop-publish.yml` against an existing release tag, or by reading the run's log
for the `-Pcampfire.desktop.distribution=` argument.

## Docs

Root `CLAUDE.md`, the Build section. This sentence lists every configurable property and must gain the new one:

> **Everything configurable is a `campfire.*` Gradle property**, declared with a default in `gradle.properties` and
> read with `project.property("campfire.x")`: the app version, the Android version code and the iOS build number, the
> Android release signing values, the Dropbox app key, and whether the web distribution is precompressed.

becomes

> **Everything configurable is a `campfire.*` Gradle property**, declared with a default in `gradle.properties` and
> read with `project.property("campfire.x")`: the app version, the Android version code and the iOS build number, the
> Android release signing values, the Dropbox app key, which of its four distributions a desktop build is, and
> whether the web distribution is precompressed.

Also in the Build section, the `desktop-publish.yml` bullet gains a sentence: each leg passes
`campfire.desktop.distribution`, which is how a build that goes through App Review is told not to offer the donation
link. The `local.properties` example block does **not** gain an entry: the property is not a secret and its default
is correct for a fresh clone.

`app/desktop/CLAUDE.md`: the Packaging paragraph gains one sentence saying that each packaging leg is told which
distribution it is with `campfire.desktop.distribution`, since jpackage's host operating system cannot tell the store
build from the direct download.

## Files touched

- `gradle.properties`
- `presentation/build.gradle.kts`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`
- `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt`
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.android.kt`
- `presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.ios.kt`
- `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.wasmJs.kt`
- `.github/workflows/desktop-publish.yml`
- `CLAUDE.md`, `app/desktop/CLAUDE.md`

## Depends on

Nothing. Plan 16 depends on this one.

## Rules

- Load the `code-style` skill before the first edit.
- `commonMain` stays JVM-free: the derived `canAskForDonations` reads nothing but `currentDistribution`.
- An `expect` that loses its `actual`s loses all four in the same change, and the unused declarations go with it.
- Everything configurable is a `campfire.*` property read with `project.property`, declared with a default in
  `gradle.properties` and overridable from `local.properties`.
- No user-facing string changes here.
