# Ship a class data sharing archive of the app's own classes, recorded by a training run at build time

**Kind:** performance (startup)  ·  **Severity:** high  ·  **Platforms:** Windows (MSIX), Linux (.deb); not macOS
**Lane:** D  ·  **Files:** `app/desktop/build.gradle.kts`, `app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt`,
`.github/workflows/publish-windows.yml`, `.github/workflows/publish-linux.yml`, `app/desktop/CLAUDE.md`, `CLAUDE.md` (root, Build section:
the `publish-linux.yml` and `publish-windows.yml` bullets)
**Challenged:** amended — the training process is started with `ProcessBuilder` and a real timeout (ExecOperations has none) with its output in a file; the frame wait is time-bounded so a session that draws no frames still exits normally and dumps; a read-only stale `campfire.jsa` is made writable before it is deleted; paths in `JAVA_TOOL_OPTIONS` are quoted; the CI `-Xlog` file is a relative name (a Windows drive letter's `:` is an `-Xlog` separator); the display check asks for `DISPLAY` only; the training must run on the image, never on `PackageMsix`'s copy (which carries the package family name).

## Problem

Every desktop start loads and verifies the app's classes one by one from the joined 26 MB jar. The build only archives
the JDK's own classes (`-Xshare:dump` after jlink, `app/desktop/build.gradle.kts` 177–209), and its KDoc says why the
app's are left out:

```kotlin
 * The app's own classes are not archived. Only a JVM that runs the app can write that archive
 * (`-XX:+AutoCreateSharedArchive`), and on JDK 21 the start that records it takes about six times as long, the file
 * would be written into the install folder, where no uninstaller knows about it, and a JVM that finds one written for
 * other jars neither uses it nor replaces it - which after an update is every one of them.
```

All three objections are about recording the archive **on the user's machine**. None applies to an archive recorded
**once, at build time**, into the app image, and shipped with the package it belongs to (`-XX:ArchiveClassesAtExit`
at build, `-XX:SharedArchiveFile` at run time).

Measured on the release image at 491c4254a (JBR 21.0.10, `scratchpad/desktop-review/logs`, `scratchpad/live/runs`):

- One start loads 10.7–12.2k classes; only 1.7–2.1k come from the JDK archive. 5,750 come from the joined jar, and
  about 2,445 are generated invokedynamic lambda classes (631 from the Compose resource `String*` accessors alone).
- With a dynamic archive (≈ 56 MB, 14 MB gzipped) recorded from one start: 98 % of classes come from the archive
  (11,379 of 11,562); first composition 0.55 → 0.36 s; first song card 0.92 → 0.54 s; window 0.68–0.72 → 0.43 s;
  startup CPU 2.4–2.5 → 1.7 s. On a stand-in for a 2-slow-core 2-in-1 (`-XX:ActiveProcessorCount=2
  -XX:+UseSerialGC -Xmx512m`, E-cores, "ecoreweak" runs): library on screen 4.6 → 2.8 s, window 3.2 → 2.2 s, CPU
  8.7 → 5.2 s, and the four startup GC pauses gone.
- The archive keeps mapping when the whole `.app` is moved and when the jar's modification time changes (re-verified
  for this plan with `touch -t 202001010000` on the jar: 11,322 of 11,502 shared). JBR 21 checks the jar's size and
  the base archive's identity, so a rebuilt jar disowns an old archive — which is exactly when the build records a
  new one.
- An archive recorded under G1 maps under SerialGC (the stand-in ran Serial, 9,956 of 10,121 shared), so the GC the
  ergonomics pick on a small machine does not have to match the build machine's: a dynamic archive holds class
  metadata, not heap objects.

On a low-end Windows 11 2-in-1, where the web build starts noticeably better than the native one, this is the single
largest startup cost the desktop build can remove without touching shared code.

## Fix

Record the archive in a task between `createReleaseDistributable` and the packaging tasks, on Windows and Linux hosts
only, and point the launcher at it.

**1. A training-run switch in `CampfireDesktopApplication.kt`.** A forced kill skips the archive dump (the JVM writes
it in its exit path), and on Windows a process started by Gradle can only be killed forcibly, so the app has to end
itself. Read a system property once in `main`:

```kotlin
/** Set by the build's training run (`recordClassDataArchive`), which needs the process to end on its own: a killed JVM writes no archive. */
private val isTrainingRun = System.getProperty("campfire.trainingRun") == "true"
```

and inside the `SwingWindow` content, next to the other effects:

```kotlin
if (isTrainingRun) {
    LaunchedEffect(Unit) {
        val data = desktopDataDirectory()
        // The demo library being written means Koin started, the preferences were read and the songs scanned.
        while (!File(data, "preferences/preferences.json").isFile ||
            File(data, "library/songs").listFiles { file -> file.extension == "cho" }.isNullOrEmpty()
        ) delay(100)
        // Enough frames for the song cards, the welcome sheet and their animations to have been composed and drawn -
        // bounded in time too, so a session that produces no frames still ends normally (and so still writes the archive)
        // instead of being killed by the build's timeout.
        withTimeoutOrNull(15_000) { repeat(120) { withFrameNanos { } } }
        exit()
    }
}
```

`exit` is the existing `leave(::exitApplication)`, so the run ends the way a closed window does (no sync is connected
in a fresh data directory, so `settleSynchronizationBeforeExit` returns at once) and `application` ends the process
with `exitProcess`, which is the path that writes the archive. The property is harmless anywhere else: nobody sets
it, and if somebody does, the app closes after its first screen. (Optional, cheap extra coverage: before `exit()`,
open the first demo song with `viewModel.value?.openSong(...)` and wait another 60 frames, so the song details
screen's classes are archived too. Only if it needs no new API; the startup path is what matters.)

**2. A `recordClassDataArchive` task in `app/desktop/build.gradle.kts`**, registered like the other custom tasks
(an abstract `DefaultTask`; the process itself is started with `ProcessBuilder`, as the jlink step above does, since
`ExecOperations.exec` has no timeout), `onlyIf { isWindowsHost || isLinuxHost }`,
`dependsOn("createReleaseDistributable")`, and made a dependency of `packageReleaseDeb`, `packageReleaseMsi` and
`packageReleaseMsix` (all three package the image in place: the Compose plugin passes the release image to jpackage
with `--app-image`, and `PackageMsix` copies it). It edits another task's output, so mark it
`doNotTrackState("Edits the release app image in place")`. Not `runRelease`, so a developer's run stays fast. Steps:

1. Locate the image `build/compose/binaries/main-release/app/Campfire`, its launcher (`Campfire.exe` on Windows,
   `bin/Campfire` on Linux), its `$APPDIR` (`app` on Windows, `lib/app` on Linux) and the launcher configuration
   `<appdir>/Campfire.cfg`.
2. Make it idempotent, since the image may be up to date from an earlier run: remove a `java-options=-XX:SharedArchiveFile=`
   line from the `.cfg` and delete `<appdir>/campfire.jsa` if present, calling `setWritable(true)` on it first (an earlier
   run that failed between the dump and step 5 leaves it read-only, which on Windows refuses the delete). A JVM given a
   dynamic archive cannot record another on top of it.
3. Start the launcher with a throwaway home, so nothing of the run (demo library, preferences, `~/.skiko`, the
   instance lock) lands in the image or in the builder's own profile:
   - `temporaryDir/home`, deleted before and after;
   - environment `JAVA_TOOL_OPTIONS=-XX:ArchiveClassesAtExit="<absolute appdir>/campfire.jsa" -Dcampfire.trainingRun=true -Duser.home="<home>"`
     — each path in double quotes, which HotSpot's parser of the variable honours, so a checkout under a folder with a
     space in its name still trains;
   - on Windows also `APPDATA=<home>\AppData\Roaming` and `LOCALAPPDATA=<home>\AppData\Local` (the data directory
     comes from `%APPDATA%`, not `user.home`; see `desktopDataDirectory()` in `:presentation`'s `Platform.desktop.kt`);
     on Linux remove `XDG_DATA_HOME` from the environment;
   - everything else inherited, so a CI step's `SKIKO_RENDER_API` and Xvfb `DISPLAY` reach it;
   - stdout and stderr redirected to a file in `temporaryDir` (`redirectErrorStream(true)` plus `redirectOutput`), so a
     chatty run can never block on a full pipe.

   It runs on the image `createReleaseDistributable` wrote, before any packaging task: `PackageMsix` adds
   `-Dcampfire.packageFamilyName` only to its own copy, so the training run's data directory is `%APPDATA%\Campfire` —
   inside the throwaway home — and never a `Packages\…\LocalState` folder. Keep it that way: never train on the copy.
4. `waitFor(3, TimeUnit.MINUTES)`. On a timeout `destroyForcibly()` it and fail the build; on a non-zero exit, or no `campfire.jsa`, or one
   under 10 MB, fail with the process output (the dump prints a few dozen `[warning][cds] Skipping …` lines for JFR
   and proxy classes, which are expected and not a failure).
5. `campfire.jsa.setWritable(true)` — the JVM writes it read-only, which on Windows stops the next jlink/jpackage run
   from clearing the image (the same reason as in the jlink step).
6. Add `java-options=-XX:SharedArchiveFile=$APPDIR/campfire.jsa` right after `[JavaOptions]` in the `.cfg`, the way
   `PackageMsix.addPackageFamilyNameToLauncher` does. `PackageMsix` then inserts its own line beside it, which is a
   system property and does not affect the archive.

JVM flags must be the same at training and at run time only where CDS checks them (compressed oops/class pointers,
module options such as the Linux `--add-opens`, which come from the same `.cfg`). The GC does not need pinning (see
Problem). A mismatch of any kind makes the JVM print a CDS warning and start without the archive, never fail.

On Linux the task needs an X display (JBR 21's AWT is X11 only; under Wayland it goes through XWayland's `DISPLAY`):
if `DISPLAY` is not set, fail with "run the packaging under xvfb-run" rather than hang.

**3. Workflows.**

- `publish-linux.yml`, "Build the package": run Gradle under Xvfb with software rendering, since the runner has no
  GPU: `SKIKO_RENDER_API=SOFTWARE_FAST xvfb-run --auto-servernum ./gradlew :app:desktop:createReleaseDistributable :app:desktop:packageReleaseDeb`.
- `publish-windows.yml`, "Build the package": `export SKIKO_RENDER_API=SOFTWARE_FAST` before `./gradlew`, for the
  same reason the start check sets it.
- Both "Start the release build once" steps: add `-Xlog:class+load=info:file=class-load.txt` to `JAVA_TOOL_OPTIONS` —
  a relative name, resolved against the step's working directory (the checkout, where both steps start the app), not
  an absolute Windows path, whose drive letter's `:` the `-Xlog` syntax reads as a separator — and after the existing checks fail the job when fewer than
  80 % of the lines say `source: shared objects file` (`grep -c 'source: shared objects file'` over `wc -l`), printing
  both numbers either way. Training and check both use `SOFTWARE_FAST`, so the expected figure is about 95 %; without
  the archive it is about 17 %.

**4. macOS: not now.** The `.pkg` is the only Mac build that ships, and it is signed inside
`createReleaseDistributable` (jpackage's `--mac-sign` plus the plugin's re-sign of the runtime and the app), so a
file added to `Contents/app` afterwards breaks the bundle's seal; the store-signed bundle also cannot be started
outside TestFlight to train it (the start check trains nothing; it re-signs a copy ad hoc). Doing it would mean
training an ad hoc copy, copying its `.jsa` into the store bundle and re-signing — a second signing pass for the
only build that runs on Apple silicon alone, where the start is already 0.4–0.7 s. Leave it out and say so in the
KDoc; nothing in the sandbox rules forbids a non-executable file in `Contents/app` if it is ever wanted.

**5. Documentation.** Replace the KDoc paragraph quoted above with what the build now does and why it is safe
(recorded at build, shipped in the image, rebuilt with every image, size-checked by the JVM, ignored with a warning
when it does not match, not on macOS and why). Update `app/desktop/CLAUDE.md`'s Packaging paragraph ("The app's own
classes are not archived — the build file says why") and the "A release build has to be started once" paragraph
(the share check), and the root `CLAUDE.md`'s `publish-linux.yml` and `publish-windows.yml` bullets (the packaging
runs a training start; the start check also requires the archive to be used).

**Size:** about +56 MB installed, +14 MB in the compressed package.

**Follow-up option, not this plan:** JDK 24/25's AOT cache (JEP 483/514/515, `-XX:AOTCacheOutput`) also stores
linked classes and JIT profiles and would cut more, but it needs a JetBrains Runtime 25 toolchain; the repository is on
`jvmTarget = "21"` and whether Compose Multiplatform 1.12's packaging supports a JDK 25 runtime image is unverified.

## Tests

None: this is build logic and a launcher switch, which the project does not unit test. The CI start check's
shared-class ratio (step 3) is the automated guard, on every Linux and Windows release.

## Manual check

1. On Windows: `./gradlew :app:desktop:packageReleaseMsix`, confirm `app/desktop/build/compose/binaries/main-release/app/Campfire/app/campfire.jsa`
   exists and `app/Campfire.cfg` names it, and that `%APPDATA%\Campfire` was not touched by the build.
2. Install the package (Developer Mode, `Add-AppxPackage -Register app/desktop/build/tmp/packageReleaseMsix/package/AppxManifest.xml`),
   set a user environment variable `JAVA_TOOL_OPTIONS=-Xlog:cds=info:file=C:\Temp\cds.txt` (`setx`, then start
   Campfire from the Start menu), and check that `cds.txt` says `Mapped dynamic region`. Repeat once the Store has
   certified a build, on the Store-installed copy, since a Store installation lays the files down itself. Remove the
   variable afterwards.
3. On the low-end 2-in-1, compare time to the song list against the previous release, cold (after a reboot) and warm.
   Also after a Defender signature update (the first start then rescans every file the app opens, now including
   the archive).
4. Linux: install the `.deb`, start it from the menu with the same `JAVA_TOOL_OPTIONS`, and check the log likewise.
