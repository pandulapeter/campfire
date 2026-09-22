# Pre-release review, fifth pass, 2026-09-22: the platforms nobody has tested yet

One file per issue, numbered by lane. Every file is a self-contained brief for an agent (or a person): what the user
sees, where the cause is, exactly what to change, the tests to add, how to verify it, which docs become untrue, which
files it touches and which plans must land first. Delete a file once its change has landed.

This is the fifth review. The first (2026-09-14, 69 issues) landed between `d40f9754` and `7e7d893c`, the second
(2026-09-21, 63 issues) between `9ed6fc7f` and `c997bd85`, the third (2026-09-22, 48 issues) between `693f567b` and
`f34ae47a`, the fourth (2026-09-22, 54 issues) between `f5d5c071` and `dd23a50c`. This one was taken at `984861e4`
and aimed at one thing the earlier four did not: **the three platforms that have barely been run, and the two store
submissions that have not happened yet.** iOS, macOS, Windows and Linux, read as builds that a reviewer at Apple,
Microsoft or Google will open, and as binaries that a stranger will install.

That aim is why the yield is what it is after four sweeps. Most of what is here is not visible in the Kotlin at all:
it is a fact about a platform — that the iOS Keychain survives an uninstall, that `cancelQuit()` aborts a system
logout, that jlink does not package locale data unless asked, that Windows will not replace a file another handle has
open, that a `t64` dependency does not exist on Ubuntu 22.04. Reading the code harder would not have found them; the
platform had to be the question.

Six area reviewers wrote the findings (iOS and App Review; the desktop across three operating systems and two stores;
the data layer and sync; `:presentation`; the release pipeline; `:chordpro` against files exported by other apps).
Five writer agents then **re-verified every finding against `984861e4`** before writing a plan for it — quoting the
code, and in `:chordpro`'s case reproducing each one with jshell against a freshly built jar. Nothing had to be
dropped as wrong, but several fixes were corrected in the writing; where a plan differs from what its reviewer
suggested, it says so and why. Line numbers are as of `984861e4`; re-locate by the quoted code where they have
drifted.

Three plans grew beyond their finding during verification: **32** gained a second route to the same crash (the view
model's own rename path, not just the repository cache), **09** turned out to block **08** (gating the transposer on
`isChordName` before the lowercase bass note is understood would stop `[D/f#]` transposing at all), and **25** turned
out to race the demo planting, so the first-run answer has to be shared rather than asked twice.

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`, or
  with `textResource` wherever user text goes into the sentence.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Races are answered with **state**, not time: a guard that asks whether the thing is already happening, a first-run
  answer that is computed once and shared. No debounce windows, and no animation or visible wait added to cover a
  race. A bounded retry of a documented OS failure (02) is not a debounce, and its plan says why.
- Where a plan changes documented behaviour, update the `CLAUDE.md` files and `documentation/*.md` it names in its
  **Docs** section.
- Run the unit tests after every change:

  ```
  ./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```

  Compile every target the change touches, at minimum `:app:desktop:run` for a manual check and
  `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  for the rest.
- Do not commit unless asked. Load the `commit-messages` skill before writing any commit message.
- A plan that turns out to be wrong or impossible is **not improvised around**: leave its file in place and say why.

## Decisions taken for this review (2026-09-22, by the user — do not re-open)

- **Store packaging is out of scope for this round.** Plan 15 adds the `campfire.desktop.distribution` property so a
  build knows what it is; the actual Mac App Store package (a `.pkg` target, entitlements, the sandbox, the bundle id,
  a separate build number) is its own piece of work, done with a Mac to test on. The same for the Microsoft Store.
- **A reinstall starts disconnected on iOS** (25): a run that finds no preferences document clears the Keychain
  credentials before the sync connection is restored.
- **The Settings section that listed the other builds is replaced** (16) by one About section: a single row leading to
  the README, which hints that other builds exist without naming them, and a "rate this app" row. No per-platform
  rows, no "Coming soon" placeholders, no store-policy filtering of a list that no longer exists. The user will fill
  in the README and the store listings by hand as they are published.
- **The rating row follows the operating system, not the installer** (16): Android → Play, iOS → the App Store,
  macOS → the Mac App Store, Windows → the Microsoft Store, Linux and the web → hidden. It appears on the direct
  downloads too, and is hidden while that platform's listing URL is still empty, which today is every store but Play.
- **The donation link is what the distribution flag governs** (15): hidden for a build that goes through App Review
  (Apple's guideline 3.1.1), shown everywhere else — Play, the Microsoft Store, the direct downloads and the web all
  permit it.
- **The Linux packages are built on Ubuntu 22.04** (18), so one `.deb` installs on both sides of the `t64`
  transition.
- **Names are normalized to NFC** (07), in the names the app writes and in the sync key, so a decomposed twin of a
  non-Latin name is one song rather than two. Existing files keep their names until something renames them.
- **Reversed from the first review: a bracket that is not a chord name is no longer transposed** (08). The first
  review decided `[Chorus]`-style words keep being transposed; the user has reopened that and chosen the gate, since
  the editor's transpose writes `[Dhorus]` to the file. A label somebody wrote as `[A]` is still transposed, which is
  unknowable from the text alone.

## Decisions from the earlier reviews (still standing — do not re-open)

An emptied remote folder stops the run and asks; credentials live behind the Android Keystore and the iOS Keychain,
and in a plain file on desktop and the web; the demo library is planted only on a first run; sync never merges, a
conflict lands as a ` (2)` copy, an edit beats a deletion, a rename is a deletion and a new file; Android backup
carries the library and the preferences; non-Latin names keep their letters; German notation is detected per song;
one desktop instance per library, one tab per origin on the web; the UI is untested; the Setlists tab always shows
the setlists, whatever the library holds; a library over the import's cap is exported anyway, and explained
afterwards; the language picker names each language in its own name; a German song without `H` stays a documented
limit; the Windows installer claims only `.cho`, `.chopro` and `.chordpro`.

## Lanes

Merge order **B, C, A, D, E**. B is self-contained; C is mostly build files, workflows and a screen nobody else
touches; A rewrites the sync engine and the file storage; D builds on A's modules; E has the view model, which D also
edits, so it goes last.

| Lane | Area | Issues, in this order |
| --- | --- | --- |
| A | Sync and data safety | 01, 02, 03, 04, 05, 06, 07 |
| B | `:chordpro` | **09, 08**, 10, 11, 12, 13, 14 |
| C | Stores, the About section, the release pipeline | 15, 16, 17, 18, 19, 20, 21, 22, 23, 24 |
| D | The iOS and desktop shells | 25, 26, 27, 28, 29, 30, 31 |
| E | `:presentation` screens | 32, 33, 34, 35 |

**09 lands before 08** — the only place where the numeric order is wrong. Everything else is numeric within its lane.

### Where the lanes will conflict

- **Both `strings.xml` files**: A (01, 06), C (16) and E add keys. Different keys; each plan names its insertion
  point by a neighbouring key. Keep every key from either side.
- **`CampfireViewModel.kt`**: D (25) and E (32, 33). 32 and 33 edit `updateSongFileName` within a dozen lines of each
  other; land 32 first.
- **The sync API** (`SyncRepository`, `SyncProvider`): A (01) and D (25) both add to it.
- **`SyncEngine.kt`**: A's 01, 03 and 07, three different functions.
- **`JvmFileStorage.kt`**: A's 02 renames `escapesDeviceNames` to `isWindows`, which 03 reads. 02 first.
- **`app/desktop/build.gradle.kts`**: C's 15, 17 and 23, different blocks.
- **`desktop-publish.yml`**: C's 15, 18, 19 and 20, all in the build step and the matrix. 15 and 20 quote the
  combined text.
- **`ui/platform/Platform.kt` and its four actuals**: C's 15 and 16.
- **The root `CLAUDE.md` and `presentation/CLAUDE.md`**: nearly every lane. Resolve as a word-level three-way merge,
  never by taking one side's paragraph whole.
- **Five drawables** (`ic_tablet`, `ic_laptop`, `ic_desktop`, `ic_terminal`, `ic_website`) are deleted by 16;
  `SettingsScreen.kt` is their only user today. Whoever lands last should re-grep.
- **`:data:model`** gains platform source sets in 07; if C touches `campfire-library` or `data/model/build.gradle.kts`,
  check the `iosArm64` and `wasmJs` wiring still resolves.

## The issues

| # | Title | Severity | Lane |
| --- | --- | --- | --- |
| 01 | Guard remote deletions the way local ones are guarded | blocker | A |
| 02 | Read library files without taking a Windows lock | major | A |
| 03 | Skip remote names Windows cannot store, and say so once | major | A |
| 04 | Delete the Keystore key only when it is really gone | major | A |
| 05 | Skip a file that vanished during an OPFS listing | major | A |
| 06 | Never hand out a half library as a backup | major | A |
| 07 | Normalize names to NFC so one song is one name | major | A |
| 08 | Never transpose a bracket that is not a chord | major | B |
| 09 | Follow a lowercase bass note through the transposition | major | B |
| 10 | Keep a CR-only file's line endings, and highlight it | major | B |
| 11 | A non-breaking space is a space | minor | B |
| 12 | Never cut a wrapped tab row through a character | minor | B |
| 13 | A byte order mark is never content | minor | B |
| 14 | Trim a bracket before deciding it is a chord | minor | B |
| 15 | Tell the desktop build which distribution it is | major | C |
| 16 | One About section, one link to the README and one rating row | major | C |
| 17 | Add the locale data and the accessibility bridge to the packaged runtime | major | C |
| 18 | Build the Linux packages on Ubuntu 22.04 | major | C |
| 19 | Refuse to publish a build whose sync key is empty | major | C |
| 20 | Keep the release secrets off the command line | major | C |
| 21 | Expand release-notes escapes only for a hand-dispatched run | minor | C |
| 22 | Let the iOS version phase read `local.properties` | minor | C |
| 23 | Match the Linux window to its desktop entry | minor | C |
| 24 | Move the App Store category into `Info.plist` | minor | C |
| 25 | Forget the sync connection on a fresh installation | major | D |
| 26 | Answer the macOS quit request instead of cancelling it | major | D |
| 27 | Report a share sheet that never opened | minor | D |
| 28 | Write exported files off the main thread | minor | D |
| 29 | Delete the temporary files an export and an import leave behind | minor | D |
| 30 | Read files opened in place with a file coordinator | minor | D |
| 31 | Open the consent page the way every other link is opened | minor | D |
| 32 | A rename can name one song twice in a setlist | blocker | E |
| 33 | Renaming from song details closes the screen | major | E |
| 34 | Song details swallows modified arrow keys | minor | E |
| 35 | Chords pile up over right-to-left lyrics | minor | E |

## What this review could not do

Every earlier review has ended with the same sentence, and it is now the largest risk left: **nothing here has been
run on the platforms it is about.** These plans were written from the code, the built artifacts and the platform
documentation. Still owed, and not answerable by any further sweep:

- a sandboxed macOS build, submitted or at least signed and run — and a real logout with the app open (26);
- a Windows machine: the MSI, an antivirus scanning the library, a file whose name came from another platform (02, 03);
- an iPhone: a reinstall with a connected account (25), a file opened from iCloud Drive (30), a share sheet, an
  export of a large library (28, 29);
- Linux: the `.deb` on Ubuntu 22.04 and on 24.04, and the window in GNOME's alt-tab (18, 23);
- a Dropbox account with two devices: the remote-deletion guard (01) above all, which is the one plan here that can
  destroy somebody's library if it is wrong;
- an internal Play track, for the update gate;
- TalkBack, VoiceOver and Narrator; rotation; an on-screen keyboard.

A sixth sweep of this shape would be worth less than any one of those.
