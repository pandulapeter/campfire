<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 23 — On Linux the window is not matched to the installed desktop entry

## What the user sees

Install the `.deb`, start Campfire from the applications menu, and the running window is not the app that was
launched as far as the desktop is concerned:

- In GNOME's overview and in alt-tab the window is labelled
  **`com-pandulapeter-campfire-CampfireDesktopApplicationKt`**, drawn with the generic "unknown application" icon,
  next to a menu entry that correctly says *Campfire* with the app's own icon.
- Right-clicking the running window's dock icon and choosing "Pin to Dash" (GNOME) or "Pin to Taskbar" (KDE's task
  manager) creates a **second** entry — the one just pinned, iconless and unlabelled — beside the one the package
  installed. Launching from either gets a different-looking icon.
- Notifications and the "Force Quit" dialog use the same string.

The Linux package is the whole of how Campfire is handed out on Linux, so this is what every Linux user sees.

## Cause

Two halves, both verified at HEAD `984861e4`.

**The window's `WM_CLASS`.** X11 (and XWayland, which is how every AWT window reaches a Wayland session, since AWT
has no Wayland backend) identifies a window by `WM_CLASS`. AWT's `XToolkit` derives it from the class at the bottom
of the stack when the toolkit is initialized, with the dots replaced by dashes. The entry point is
`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`, whose `mainClass` the build
declares at `app/desktop/build.gradle.kts:34`:

```kotlin
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
```

so the `WM_CLASS` is `com-pandulapeter-campfire-CampfireDesktopApplicationKt`. Nothing in the repository changes it:

```
$ grep -rn "awtAppClassName\|resourceDir\|StartupWMClass\|jvmArgs\|Toolkit\|System.setProperty" app/desktop/
(no matches)
```

**The `.desktop` entry's `StartupWMClass`.** A desktop environment matches a window to a launcher by comparing
`WM_CLASS` against the `.desktop` file's base name or, when they differ, against its `StartupWMClass` key. jpackage
writes its Linux `.desktop` from a fixed template with `Name`, `Comment`, `Exec`, `Icon`, `Terminal`, `Type`,
`Categories` and the MIME types — and no `StartupWMClass`. The build asks for that entry at
`app/desktop/build.gradle.kts:76-84` and adds nothing to it:

```kotlin
            linux {
                iconFile.set(project.file("src/main/composeResources/drawable/app_icon.png"))
                // jpackage writes the .desktop entry — and with it the icon — only for a package that asks for a
                // shortcut or declares file associations, and this one declares none: a MIME type is what a Linux
                // association is keyed by, and ChordPro files have none of their own to claim.
                shortcut = true
                menuGroup = "AudioVideo;Audio;Music"
                appCategory = "sound"
                debMaintainer = "pandulapeter@gmail.com"
            }
```

So the two strings cannot meet: the package says `campfire-Campfire`, the window says
`com-pandulapeter-campfire-CampfireDesktopApplicationKt`, and nothing bridges them.

**A correction to the obvious fix.** Handing jpackage a custom `.desktop` template through its `--resource-dir` is
not reachable from this build. The Compose Desktop plugin owns that flag: `AbstractJPackageTask` (1.12.0) fixes the
directory at line 358 —

```kotlin
    protected val jpackageResources: Provider<Directory> = project.layout.buildDirectory.dir("compose/tmp/resources")
```

— **clears it inside the task action** (`prepareWorkingDir`, line 608: `fileOperations.clearDirs(jpackageResources)`),
so nothing can be placed there from a `doFirst`, and passes `--resource-dir` itself at lines 401 and 442. A second
`--resource-dir` through `freeArgs` loses, because `AbstractJvmToolOperationTask.makeArgs` emits `freeArgs` **first**
and `AbstractJPackageTask.makeArgs` appends its own afterwards. `LinuxPlatformSettings` (`PlatformSettings.kt:86-96`)
exposes `shortcut`, `packageName`, `appRelease`, `appCategory`, `debMaintainer`, `menuGroup`, `rpmLicenseType`,
`debPackageVersion` and `rpmPackageVersion` — nothing for the desktop entry's contents. So the key is added to the
built package instead.

## The change

Two halves, and both are wanted: the first makes the window say `Campfire`, the second makes the launcher recognize
it.

### 1. The window's class name, in `:app:desktop`

Add to `CampfireDesktopApplication.kt`, called from `main` **before** anything touches AWT — before
`claimSingleInstance`, which on macOS asks `Desktop` for the open-file handler:

```kotlin
/**
 * What GNOME and KDE match a window against to find the launcher it came from. AWT derives it from the class at the
 * bottom of the stack with the dots turned into dashes, so the window would announce itself as
 * "com-pandulapeter-campfire-CampfireDesktopApplicationKt": the string alt-tab shows, and the name a pinned shortcut
 * is created under, beside the one the package installed. There is no API for it — the field belongs to the X11
 * toolkit, which is why the packaged Linux launcher opens that package (see build.gradle.kts) — and no other
 * platform has the field at all, so a failure here is a cosmetic loss and never a reason not to start.
 */
private fun setLinuxWindowClassName() {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, "Campfire")
        }
    }
}
```

`Toolkit` comes from `java.awt`, which `:app:desktop` is free to use — it is a plain JVM module, and this file
already imports `java.awt.Desktop`.

### 2. The `--add-opens` the field needs, in `app/desktop/build.gradle.kts`

`sun.awt.X11` is not an exported package, so `setAccessible` throws `InaccessibleObjectException` without it. Add it
only when the build is running on Linux — which is exactly right here, because jpackage only ever packages for the
machine it runs on, so the Linux package is always built on a Linux host and the other two never carry the flag (an
`--add-opens` naming a package that does not exist prints a JVM warning on every start):

```kotlin
compose.desktop {
    application {
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        // The X11 toolkit's app class name is what a Linux desktop matches a window to its launcher by, and it is a
        // private field of a package java.desktop does not export. Only on a Linux host, since jpackage packages for
        // the machine it runs on and an --add-opens naming a package the runtime does not have warns on every start.
        if (System.getProperty("os.name").orEmpty().lowercase().contains("linux")) {
            jvmArgs("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        }
```

### 3. `StartupWMClass` in the installed `.desktop`, in `app/desktop/build.gradle.kts`

A task that adds the key to the built `.deb`, registered after the packaging tasks and wired with `finalizedBy` so
that `packageDeb` and `packageReleaseDeb` both get it:

```kotlin
/**
 * Adds StartupWMClass to the desktop entry jpackage writes, which is the key that ties the running window to the
 * installed launcher: without it GNOME and KDE show the window under its WM_CLASS and a pin creates a second,
 * iconless entry. jpackage would take a .desktop template through --resource-dir, but the Compose plugin fixes that
 * directory, clears it inside its own task action and passes the flag after any freeArgs, so there is no way to hand
 * it one - the key goes into the finished package instead. The value is what `setLinuxWindowClassName` in
 * `:app:desktop`'s entry point sets the toolkit's app class name to; the two have to agree.
 */
val addStartupWmClassToDeb = tasks.register("addStartupWmClassToDeb") {
    onlyIf { System.getProperty("os.name").orEmpty().lowercase().contains("linux") }
    doLast {
        // …for each *.deb under build/compose/binaries/main{,-release}/deb:
        //   dpkg-deb --raw-extract <deb> <tmp>
        //   append "StartupWMClass=Campfire" to the single *.desktop found under <tmp>
        //   fakeroot dpkg-deb --build <tmp> <deb>
    }
}
tasks.matching { it.name == "packageDeb" || it.name == "packageReleaseDeb" }.configureEach {
    finalizedBy(addStartupWmClassToDeb)
}
```

Write the body with `providers.exec` / an `Exec`-style invocation rather than raw `Runtime`, keep the `.deb` path as
a task input and output so it is not repacked on every build, and fail the task loudly if no `.desktop` is found —
a silent no-op here is the bug coming back.

`fakeroot` is needed so the repacked archive keeps root-owned files; `desktop-publish.yml:98-100` already installs it
on the Linux legs (`sudo apt-get install --yes fakeroot xvfb libegl1 libgl1`), so CI needs no change. A developer
building a `.deb` on a machine without `fakeroot` gets the task's failure, which is the right answer — the package
would otherwise be wrong in a way only an installation shows.

**If review judges the repacking too much machinery**, part 3 alone with the value
`com-pandulapeter-campfire-CampfireDesktopApplicationKt` is a complete fix and needs neither part 1 nor part 2 — but
it pins the desktop entry to the main class's name, so renaming `CampfireDesktopApplicationKt` would break it
silently. The three parts together pin both ends to the literal `Campfire`, which is why they are all here.

## Tests

None; there is nothing pure to test and the UI is untested. Run the standard suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

**Needs a Linux machine (or a VM / container with a desktop session).** Nothing about this is observable from macOS
or Windows: the `.deb` cannot be built there, and `WM_CLASS` does not exist there.

```bash
# 1. The key is in the package, and only once.
./gradlew :app:desktop:packageReleaseDeb
DEB=$(find app/desktop/build/compose/binaries/main-release/deb -name '*.deb')
dpkg-deb --raw-extract "$DEB" /tmp/campfire-deb
grep -rn 'StartupWMClass' /tmp/campfire-deb           # must print exactly one "StartupWMClass=Campfire"
find /tmp/campfire-deb -name '*.desktop'              # the entry it was added to

# 2. The package still installs and the entry is still valid.
sudo apt-get install -y "$DEB"
desktop-file-validate /usr/share/applications/campfire-Campfire.desktop

# 3. The window agrees. Start Campfire from the applications menu, then:
xprop WM_CLASS      # click the window; must print "campfire", "Campfire" — not the com-pandulapeter-… string
```

Then by hand, in a GNOME session: the window in alt-tab and in the overview must be labelled *Campfire* with the
app's own icon, and pinning the running window to the dash must **not** add a second entry — the one already there
must highlight instead. Repeat in a KDE session if one is available; the matching rule is the same.

Also check the two builds that must not change:

```bash
./gradlew :app:desktop:run                        # on macOS and on Windows: no JVM warning on stdout at startup
./gradlew :app:desktop:packageReleaseDmg          # macOS: still builds, and the app still starts
```

The release workflow's smoke test (`desktop-publish.yml`, "Start the release build once") reads the log and fails on
the word `Exception`, so a `--add-opens` warning or an `InaccessibleObjectException` escaping the `runCatching` would
be caught there — dispatching `Publish Desktop` against an existing tag is the cheapest way to confirm all five legs
still start.

## Docs

`app/desktop/CLAUDE.md`, the Packaging paragraph. The sentence about the Linux entry is true and gains the new half:

> The Linux package asks for a `shortcut`, because jpackage writes a `.desktop` entry — and so shows the icon
> anywhere — only for a package that asks for one or declares file associations

gains, after it:

> …, and the entry gets a `StartupWMClass` of its own once the package is built (`addStartupWmClassToDeb`), since the
> Compose plugin owns jpackage's `--resource-dir` and there is no way to hand it a template. That value has to stay
> equal to the app class name `main` gives the X11 toolkit (`setLinuxWindowClassName`, which is what the Linux-only
> `--add-opens` is for): a desktop environment matches a window to its launcher by `WM_CLASS`, and AWT's default is
> the main class with its dots turned into dashes, which is what alt-tab showed and what a pinned shortcut was
> created under.

The paragraph on `CampfireDesktopApplication.kt` at the top of the same file gains a clause saying that `main` sets
the window class name before anything touches AWT.

Root `CLAUDE.md` needs no change; it does not describe the desktop package's contents.

## Files touched

- `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`
- `app/desktop/build.gradle.kts`
- `app/desktop/CLAUDE.md`

## Depends on

Nothing. Touches the same build file as plans 15 and 17, in different blocks.

## Rules

- Load the `code-style` skill before the first edit: the MPL header on anything new, KDoc for declarations and `//`
  for statements, comments that carry the why, trailing commas.
- `:app:desktop` is a plain JVM module, so `java.awt` is allowed here; nothing of this goes anywhere near
  `commonMain`, which stays JVM-free.
- No user-facing strings are added, so neither `strings.xml` is touched.
- Nothing new becomes configurable, so no `campfire.*` property is added.
