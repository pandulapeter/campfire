# Let the desktop JVM compile composables past HotSpot's 8,000-byte limit

**Kind:** performance · **Severity:** medium · **Platforms:** desktop (JVM)
**Files:** `app/desktop/build.gradle.kts`, `app/desktop/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`.../screens/songDetails/SongDetailsAppBar.kt`, `.../screens/songDetails/SongDetailsPagerEffects.kt` (KDoc lines only);
option B instead: `.../dialogs/CampfireDialogs.kt`, new files beside it and beside `SongDetailsScreen.kt`, a new test or
script (see Fix)
**Challenged:** sound — `-XX:-DontCompileHugeMethods` checked on the toolchain runtime the build packages with (JBR
21.0.10+7-b1163.105, `~/.gradle/jdks/jetbrains_s_r_o_-21-aarch64-os_x.2`): it starts, and `-XX:+PrintFlagsFinal` lists
`bool DontCompileHugeMethods = false {product} {command line}`. `application.jvmArgs` reaches `run` (the IDE's Desktop
configuration is that Gradle task), `runRelease`, and jpackage's `[JavaOptions]`, which `RecordClassDataArchive` and
`PackageMsix` both keep (they add lines, never rewrite the section). Option B's class-file check would enforce a rule
only HotSpot has: ART's optimizing compiler skips only methods past its own, much higher huge-method threshold (10,000
dex code units, about 20 KB), which nothing here approaches, and R8 reshapes the release methods anyway. Both methods
have grown since the measurement (`f3042f6d0` added a dialog type and menu items), which A makes moot.

**Decision D1** (awaiting the user): option A recommended.

## Problem

HotSpot never JIT-compiles a method of more than 8,000 bytes of bytecode (`-XX:+DontCompileHugeMethods`, on by default):
such a method is interpreted on every call for the life of the process. In the desktop build of 2026-10-26 (javap on
`presentation/build/classes/kotlin/desktop/main`) two composables are over it:

| Method | Bytecode |
|---|---|
| `SongDetailsScreenKt.SongDetailsScreen-jIwJxvA` | 8,294 bytes |
| `CampfireDialogsKt.CampfireDialog` (private, the `when (dialog)` over every `DialogType`) | 8,291 bytes |
| `SongLyricsKt.SongLyrics-vkPPWEM` | 7,000 bytes (next to cross) |

Every other method in every module's desktop classes is under 7,000 bytes, except generated static initializers
(`GuitarChordTable`, `DropDChordTable`, `UkuleleChordTable`, `StringsDefault`, `StringsHu` `static {}`), which run once
and are irrelevant.

`SongDetailsScreen` recomposes on every page change, dialog change, and preference change; `CampfireDialog` on every
dialog change. Interpreted, each call costs a few hundred microseconds more than compiled — not a frame on its own, but
it never warms up, it is part of every song details open, and it keeps coming back: the rule is written down three
times and was already fixed once (`5b88dc607 Split the song details screen so that the desktop app compiles it instead
of interpreting it.`), yet nothing enforces it:

```kotlin
// SongDetailsScreen.kt, KDoc of songDetailsKeyboardShortcuts
 * the buttons do; where they are not, the keys scroll. Not part of [SongDetailsScreen] itself, which has to stay small
 * enough for HotSpot to compile.
// SongDetailsAppBar.kt
 * actions and the metronome panel under them. A composable of its own so that [SongDetailsScreen] stays small enough
 * for HotSpot to compile, which it never does for a method of more than 8,000 bytes of bytecode, and that one runs on
 * every page change. ...
// SongDetailsPagerEffects.kt, KDoc of FollowPager
 * pager is. Not part of [SongDetailsScreen] itself,
 * which has to stay small enough for HotSpot to compile.
```

## Fix

### Option A (recommended): turn the limit off for the app's JVM

In `app/desktop/build.gradle.kts`, inside `compose.desktop { application { … } }`, before the host-specific
`jvmArgs(...)` calls, add an unconditional

```kotlin
// Compose turns a composable into one method, and HotSpot never compiles one over 8,000 bytes of bytecode: the song
// details screen and the dialog host crossed it and were interpreted on every recomposition for good.
jvmArgs("-XX:-DontCompileHugeMethods")
```

- `DontCompileHugeMethods` is a `product` flag of HotSpot (JDK 21, the JetBrains Runtime included); an unknown `-XX`
  flag would stop the JVM from starting, so verify it on the toolchain runtime first (Tests).
- The Compose plugin passes `application.jvmArgs` to `:app:desktop:run`, `runRelease`, and jpackage's `--java-options`,
  so it lands in every image's `Campfire.cfg` (`.dmg`, Mac App Store `.pkg`, `.msi`, `.deb`); `PackageMsix` copies the
  image's `.cfg` and appends its own `-Dcampfire.packageFamilyName`, keeping the rest. The Mac App Store bundle is
  signed after the `.cfg` is written, and a `-XX` flag needs no entitlement. The CDS training run
  (`recordClassDataArchive`) starts the same image, so the archive and the shipped flags agree.
- `:tools:screenshots` and the unit tests do not get it; neither needs it.
- Edit the three KDocs: drop the HotSpot clause and keep the reason that still holds (a smaller screen recomposes less
  of itself; `SongDetailsAppBar`'s "What only the bar reads is collected and decided here, so that a change of it
  recomposes the bar rather than the whole screen" stays). E.g. "Not part of [SongDetailsScreen] itself, so a change of
  it recomposes only this." Do not undo the splits.
- `app/desktop/CLAUDE.md`: one bullet under `## Packaging` (or a short `## JVM flags` section before it):
  "`-XX:-DontCompileHugeMethods` (`application.jvmArgs`, so `run` and every package): a composable is one method, and
  HotSpot otherwise never compiles one past 8,000 bytes of bytecode (`SongDetailsScreen` and `CampfireDialog` were)."

### Option B: keep the limit, split the two, and enforce it

- Split `CampfireDialog`'s `when` into two or three private composables by kind (the setlist forms, the song sheets, the
  confirmations), each under 8,000 bytes, and move one more block out of `SongDetailsScreen` (the `SongDetailsPager(...)`
  call's argument plumbing or the `ImportPreviewBar` block into a private composable).
- Add a check that fails when a method of `:presentation`'s desktop classes passes 8,000 bytes, static initializers
  exempt: a `desktopTest` that reads its own class files through the class loader and parses the `Code` attributes (no
  ASM on the classpath today, so a ~60-line class-file reader), or a `.github/scripts` script over `javap -c` wired
  into the pre-commit hook (needs built classes, so it can only run where they exist — weaker).

**Why A**: one line covers every present and future method in every module and library, needs no class-file reader,
no exception list, and no further splitting for a JVM limit; the splits already made stay for readability. B keeps the
limit alive and spends a check on a rule only one platform has. Choose B only if the user wants the JVM flags left at
their defaults.

## Tests

No unit test (A changes a launch flag). Verify:

- `<toolchain JBR>/bin/java -XX:-DontCompileHugeMethods -version` succeeds (the JBR the build uses; `./gradlew
  :app:desktop:createDistributable --info | grep -i javaHome` or the image's `runtime` folder names it).
- `./gradlew :app:desktop:createDistributable`, then the flag is in the image's `Campfire.cfg`
  (`find app/desktop/build/compose/binaries/main -name Campfire.cfg | xargs grep -n DontCompileHugeMethods`).
- `./gradlew :app:desktop:createReleaseDistributable` and the same grep under `main-release` (ProGuard path).
- Optional: in a running `:app:desktop:run`, `jcmd <pid> VM.flags | grep DontCompileHugeMethods` shows `false`.
