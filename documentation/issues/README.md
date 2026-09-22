# Pre-release review, third pass, 2026-09-22: stress, monkey-tapping and crashes

One file per issue, numbered by priority within each group. Every file is a self-contained brief for an agent (or a
person): what the user sees, where the cause is, exactly what to change, the tests to add, how to verify it, which
docs become untrue, which files it touches and which plans must land first. Delete a file once its change has landed.

This is the third review. The first (2026-09-14, 69 issues) landed between `d40f9754` and `7e7d893c`, the second
(2026-09-21, 63 issues) between `9ed6fc7f` and `c997bd85`. This one was aimed at what a public release with no crash
reporting will meet: double and triple taps, taps on things that are animating in or out, held keys, rotation and
window resizing mid-action, data changing under an open screen, hostile or merely huge input, and slow or refused
platform services. Seven area reviewers wrote the findings; five verifiers then re-traced every one against the code
and the library sources of the versions in `gradle/libs.versions.toml`, corrected or rewrote fixes where needed, and
rejected what did not hold. Where a plan says **Verifier:** under its severity, its fix was changed at that stage.
Line numbers are as of commit `a38dea2f`; re-locate by the quoted code where they have drifted.

Two plans finish a fix an earlier review left incomplete: **01** (review 2's plan 12, landed as `bb501e03`, dropped the
path and the text encoding of the Safari write fallback) and **38** (the `%`-safe formatting of `44d0f01f` does not
reach the plural of the failed-files sentence).

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`, or
  with `textResource` wherever user text goes into the sentence.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Monkey-tapping is answered with **state**, not time: a guard that asks whether the thing is already happening, a
  menu that knows it has closed, a key that knows it has not been released. No debounce windows, and no animation or
  visible wait added to cover a race (see the "no incidental animations" rule the earlier reviews followed).
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

## Decisions already taken (do not re-open)

From the earlier reviews: an emptied remote folder stops the run and asks; `[Chorus]`-style bracketed words keep
being transposed; credentials live behind the Android Keystore and the iOS Keychain, and in a plain file on desktop
and the web; the demo library is planted only on a first run; sync never merges, a conflict lands as a ` (2)` copy,
an edit beats a deletion, a rename is a deletion and a new file; Android backup carries the library and the
preferences; non-Latin names keep their letters; German notation is detected per song; one desktop instance per
library, one tab per origin on the web; the UI is untested.

Taken by the user on 2026-09-22 for this review:

| Plan | Question | Decision |
|------|----------|----------|
| 08 | What the Setlists tab shows while setlists exist and the library holds no songs | **The setlists, always.** The library's empty state belongs to the Songs tab; an empty setlist keeps its "Add songs" row, whose picker says the library is empty |
| 09 | A library whose export is over the import's 24 MiB cap | **Export it anyway, then explain** after the save that the archive is too large to import in one go and how to bring it back. A streaming import with higher caps can follow if libraries that size turn up |

## What the plan writers decided that is worth a second look

- **10** and **11** are two halves of one guard. 10 lets the view model run one pick, export or share at a time and
  ignores a second one (no queue, no timer); that is only safe because every platform picker is made to answer on
  every way it can go away, which is why 10 also teaches the iOS picker about a sheet swiped off and about refusing
  to present over something. 11 makes a menu entry fire once per opening, which 10 cannot do for the transfers that
  return at once (Android's share chooser, the web's download).
- **12** blocks touches on a screen whose lifecycle is below RESUMED while the host's is RESUMED (a screen sliding in
  or out). It deliberately does **not** use the view model's `isNavigationTransitionRunning`, which the reviewer found
  stays true after every pop.
- **13** moves the sync buttons so that they no longer trade places under a finger: "Sync now" stays where it is and
  is disabled during a run, "Stop syncing" sits in the progress row below it.
- **06** skips a library file over the per-file import cap in the scan, and sync leaves it out on both sides (its
  index entry included, so it is not taken for a deletion) and names it among the run's failures.
- **24** leaves the single-instance lock to the OS at exit and only closes the hand-over listener early; releasing the
  lock by hand before `exitProcess` would let a new process read the library while this one may still be writing.
- **40** changes how a tab is cut into systems on narrow screens: a new system starts when the string names start
  over in the same order, or at a chord line between two staves.
- **44** keeps two of its three throttles time-based on purpose: the platform limit they answer (notification posts)
  is itself a rate.

Rejected at verification, and why (so that nobody files them again): holding Ctrl/Cmd+S cannot write the file
repeatedly (the first write lands long before the key repeats); an import opened with Campfire (`a38dea2f`) is not
popped by the song screen before the list catches up (the rescan publishes `Loading` first); an OPFS refusal on the
web does not hang the loading page (coroutines 1.11's `Promise.await` rethrows it as a plain `Exception`, which the
scan catches); `AndroidSecretStore` dropping the key on a transient error cannot happen with a key that needs neither
an unlocked device nor authentication; an unreadable preferences file is not a silent failure (the lists show their
error and Retry reads it again); the conflicts question can appear above the required-update screen, and that is
harmless.

## Groups

| Range | Group |
|-------|-------|
| 01–09 | Data loss, and features that silently stop working |
| 10–18 | Double taps, taps during animations, stuck states, crashes |
| 19–29 | Platform shells: keys, windows, pickers, lifecycles |
| 30–41 | Wrong behaviour in the screens, the editor and the parser |
| 42–48 | Performance and hostile input |

## Index

| # | Issue | Severity | Lane |
|---|-------|----------|------|
| 01 | Web on Safari before 26: nothing can ever be saved (the worker fallback writes to the root, and cannot write text) | data loss | B |
| 02 | A setlist touched while sync downloads a new version of it puts the old one back, and the next run uploads it | data loss | A |
| 03 | A song or setlist saved while sync is downloading that same file is overwritten by the download | data loss | A |
| 04 | A setlist change, rename or deletion cut short by leaving reaches the file but not the lists, and is written back over | data loss | A |
| 05 | Web: a network failure escapes every catch of a sync run; it ends silently and its downloads are not in the lists | data loss path | A |
| 06 | A huge file in the library folder is read whole on every start (iOS: killed at every launch) and by every sync | crash | A |
| 07 | A setlist with a longer title loses its menu on a phone and cannot be renamed, archived, exported or deleted | stuck state | D |
| 08 | With no songs, the Setlists tab hides every setlist behind "Your library is empty", with no buttons | stuck state | D |
| 09 | A library of several thousand songs exports to an archive the app's own import refuses | wrong behaviour | C |
| 10 | Double-tapping Export or Import builds two exports and opens two pickers; Android then deletes the export | wrong behaviour | C |
| 11 | Double-tapping a menu entry runs it twice: two downloads, share sheets, save dialogs, pickers | wrong behaviour | D |
| 12 | Double-tapping the editor's Close closes the song too; a screen sliding in or out still takes taps | wrong behaviour | C |
| 13 | Double-tapping "Sync now" stops the run it just started; double-tapping "Stop syncing" starts it again | wrong behaviour | D |
| 14 | The fast scroller gets stuck "dragged" when the list stops being scrollable mid-drag | stuck state | D |
| 15 | Leaving right after Disconnect leaves the account shown as connected, and "Sync now" does nothing | stuck state | A |
| 16 | Ticking a song in the picker of a setlist sync deleted recreates the setlist from the sheet's old copy | wrong behaviour | C |
| 17 | Android: a song opened from a setlist of thousands crashes the app on every trip to the background | crash | C |
| 18 | Android: a very long paste into a search field crashes the app on its way to the background | crash | D |
| 19 | Desktop: holding Escape closes every screen and then quits the app; web: empties the back stack | wrong behaviour | E |
| 20 | Web: Ctrl/Cmd+S in the editor also opens the browser's "Save page as" dialog | wrong behaviour | E |
| 21 | Web: one notch of Ctrl+wheel throws the text size to 250 % or 50 % | wrong behaviour | E |
| 22 | iOS: with the app's theme unlike the phone's, the status bar is invisible and system sheets use the other theme | wrong behaviour | E |
| 23 | Android: a file Campfire cannot read, opened or shared, brings the app up and says nothing | wrong behaviour | E |
| 24 | Desktop: reopened while the last window is closing, it loses the file or runs without the single-instance lock | data loss path | E |
| 25 | Desktop: one unreadable drop breaks drag and drop until the app is restarted | wrong behaviour | E |
| 26 | Android: a configuration change while an export is being prepared makes it fail; the picker leaks an Activity | wrong behaviour | E |
| 27 | Web: a first visit whose demo song request never answers stays on the loading page for good | stuck state | C |
| 28 | iOS: a sync suspended in the background is reported as "could not reach Dropbox" instead of interrupted | minor | E |
| 29 | Web: storage refusals are never `LibraryStorageException`, so they get the wrong message | minor | B |
| 30 | Transposing in the editor moves the caret, so the next keystroke lands elsewhere | wrong behaviour | B |
| 31 | Switching Edit/Preview, rotating or resizing across the split sends the editor back to its first line | wrong behaviour | D |
| 32 | Two quick Next taps (or pedal presses) in a setlist move on only one song | wrong behaviour | D |
| 33 | An import's conflicts question replaces whatever dialog the user is in | minor | C |
| 34 | Android: rotating drops the snackbar on screen and every message queued behind it | minor | C |
| 35 | Dragging a setlist row past the end of its setlist stalls the other rows for up to a second | wrong behaviour | D |
| 36 | A search reopened while it is still closing comes back without the keyboard | minor | D |
| 37 | Tapping a section header scrolls it to below the top by the height of the song's header | minor | D |
| 38 | A sync with many conflicts lists every one of them; a failed file named with `%` is mangled | minor | D |
| 39 | A tag (or a new song's title) pasted from two lines of Windows text becomes two stray lyric lines | wrong behaviour | D |
| 40 | On a phone, stacked tab systems are read out of order; a crafted tab runs out of memory | wrong behaviour / crash | B |
| 41 | A file starting with two byte order marks loses its first directive | minor | B |
| 42 | Android: every configuration change freezes the screen and swallows taps behind an invisible launch screen | performance | C |
| 43 | Every setlist change re-sorts the whole song list; the first scan sorts it once per 64 files read | performance | A |
| 44 | Sync restarts the Android foreground service and re-posts the iOS notification for every file | performance | E |
| 45 | When Dropbox refuses the token mid-run, every transfer in flight renews it again | minor | A |
| 46 | A song carrying tens of thousands of tags or languages stalls every scan and freezes the web app | hang | B |
| 47 | A crafted zip of a few megabytes keeps an import busy for half an hour | hang | B |
| 48 | A large "Keep both" import lists the whole library once per colliding song | performance | B |

## Working in parallel

How to actually run it — worktrees, subagents, commits and merges — is in [EXECUTION.md](EXECUTION.md).

The lanes were cut from the plans' own **Touches** and **Depends on** sections: two plans that edit the same source
file are in one lane, serial in the order given, except for the small, named touch points below.

| Lane | Scope | Order | Why it is one lane |
|------|-------|-------|--------------------|
| A | Repositories, local sources, sync engine and repository, Dropbox | 04 → 02 → 03 → 06 → 43 → 05 → 15 → 45 | `SetlistRepositoryImpl.kt` (04, 02), `SetlistLocalSourceImpl.kt` (02, 06), `SyncEngine.kt` (03, 06), `SongLocalSourceImpl.kt` (06, 43), `SyncRepositoryImpl.kt` (05, 15), `DropboxSyncProvider.kt` (05, 45) |
| B | Web storage, zip, file names, `:chordpro` | 01 → 29 → 41 → 48 → 47 → 46 → 40 → 30 | `FileStorage.wasmJs.kt` (01, 29), `ChordProSyntax.kt` (46, 30) |
| C | View model and app root | 42 → 12 → 10 → 17 → 33 → 27 → 16 → 09 → 34 | `CampfireViewModel.kt` (all but 12), `CampfireApp.kt` (42, 12, 09, 34); 34 rewrites every `_messages` line and goes last |
| D | Screens, components, dialogs | 07 → 08 → 14 → 18 → 36 → 35 → 11 → 13 → 38 → 37 → 32 → 31 → 39 | `ListItems.kt` (07, 08), `SetlistsScreen.kt` (08, 35, 11), `Search.kt` (18, 36), `Dialogs.kt` (08, 18, 39), `SyncSettings.kt` (13, 38), `SongEditorScreen.kt` (11, 31) |
| E | Desktop, web, iOS and Android shells | 19 → 20 → 21 → 25 → 24 → 22 → 28 → 44 → 23 → 26 | `CampfireWebApp.kt` (19, 20), `CampfireDesktopApp.kt` (19, 25), `CampfireDesktopApplication.kt` (19, 24), `CampfireViewController.kt` (22, 28), `IosSyncNotifier.kt` (28, 44), `FilePicker.android.kt` (23, 26) |

Merge order: **B, A, E, C, D.** B and A touch almost nothing another lane touches; C carries the view model; D edits
screens that C and E have been in, so it goes last.

Cross-lane touch points to expect at merge time (small, distinct hunks):

- `CampfireViewModel.kt`: C (everything), D (08: `libraryPlaceholder`).
- `SongEditorScreen.kt`: B (30: the transposition call), D (11: the revert entry's lambda; 31: the scroll states),
  E (20: the `onPreviewKeyEvent` condition).
- `Dialogs.kt`: C (16: the song picker's call), D (08, 18, 39).
- `IosFilePicker.kt`: C (10) only; `FilePicker.android.kt`: E (23, 26) only.
- `strings.xml` (both languages): C (09), D (38), and any other plan adding a string — additions only; keep every
  side's keys.
- Root `CLAUDE.md` and the module `CLAUDE.md` files: many plans add or correct a sentence; keep every side's.

Not reproduced by running, only by reading and by the library sources — the plans say what to watch for when
verifying: 01 (Safari 18 or any iOS 18 browser), 02, 03, 05, 15 and 45 (a Dropbox account, two installations or the
browser's offline switch), 06 (an iOS device and a 500 MB file), 17 and 18 (`adb` and a very large setlist / paste),
22 and 28 (an iOS device), 24 (Windows or Linux, relaunching while closing), 25 (Linux X11 drag sources), 26 (rotation
during a slow export), 27 (a browser's request blocking).
