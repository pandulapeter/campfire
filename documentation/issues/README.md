# Pre-release review, sixth pass, 2026-09-23: after every known fix

One file per issue, numbered by lane. Every file is a self-contained brief for an agent (or a person): what the user
sees, where the cause is, exactly what to change, the tests to add, how to verify it, which docs become untrue, which
files it touches and which plans must land first. Delete a file once its change has landed.

This is the sixth review. The first five (2026-09-14 to 2026-09-22, 269 issues) have all landed; the last of them
ended at `4164354f`, followed by four fixes from another agent and the manual test scripts (`2065e47f`). This one
was taken at **`2065e47f`**, with every known fix in, and asked again across the whole codebase — every module,
every platform (Android, iOS, macOS, Windows, Linux, web) — rather than from one new angle.

Seven area reviewers read the code (`:chordpro`; local storage, zip and names; sync; the domain and repositories;
`:presentation`'s state and navigation; its screens and platform shells; the app shells and the release pipeline).
Five writer agents then **re-verified every finding against `2065e47f`** before writing its plan, quoting the code
and, where a claim was about a library (Material 3 insets, skiko's HiDPI setup, `CreateDocument`), checking the
library's sources. No finding was dropped as wrong, but many fixes were corrected in the writing — where a plan
differs from what its reviewer suggested, it says so and why. Line numbers are as of `2065e47f`; re-locate by the
quoted code where they have drifted.

**What the yield looks like.** One real data-loss bug in the core (21: Replace in an import can overwrite a library
song the same import brings back unchanged), three smaller data-loss paths that need a coincidence (30, 44, 46), a
cluster in sync where one bad file or one cancelled connection leaves things worse than the docs promise (09, 16,
17, 18), a set of places where `:chordpro` reads real-world files differently from the spec (01–06), and the rest
wrong states, wrong messages and stale docs. Nothing found crashes.

## Work in progress at review time — read before executing

While this review ran, another agent was writing end-to-end tests and had **uncommitted changes** in the checkout:
`SyncEngine.kt`, `SyncEngineTest.kt`, `FakeSyncProvider.kt`, `SyncProvider.kt`, `DropboxSyncProvider.kt` (and
`DropboxModels.kt`, `DropboxRequestTest.kt`) — batching remote deletions into one call — plus
`SyncAuthenticator.desktop.kt`, `app/desktop/build.gradle.kts`, `CampfireDesktopApplication.kt`, a new
`TestDriver.kt`, the root `CLAUDE.md`, `data/repository/implementation/CLAUDE.md`,
`data/source/remote/implementation/CLAUDE.md`, `documentation/sync.md` and several files in `documentation/testing/`.
Every plan quotes the **committed** version (`git show 2065e47f:<path>`). Plans 09, 16, 17, 18, 19, 20, 23, 26, 28, 45
and 47 touch those files and say so; their implementer must re-locate the quoted code in whatever is committed when
the plan runs, and merge into the other agent's work rather than over it. **Do not start executing until that work
is committed or abandoned** (`EXECUTION.md` §1 checks it).

## Rules that apply to every plan

- **Load the `code-style` skill before editing any `.kt`, `.kts` or `strings.xml` file.** It governs the MPL header,
  the KDoc / `//` split, the "why, not what" comment voice, trailing commas, `modifier` first, and string resources.
- New UI strings go into **both** `presentation/src/commonMain/composeResources/values/strings.xml` and
  `values-hu/strings.xml`, and are read with `com.pandulapeter.campfire.presentation.localization.stringResource`, or
  with `textResource` wherever user text goes into the sentence.
- Shared code stays JVM-free (no `java.*` in `commonMain`).
- Races are answered with **state**, not time: no debounce windows, no animation or visible wait added to cover a
  race. (This is why 47 corrects the Windows test script rather than batching hand-overs within 300 ms.)
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

## Decisions taken for this review (2026-09-23, by the user — do not re-open)

- **A `{transpose}` in the middle of a song is a modulation** (02). Only one before the body transposes the whole
  song; later ones move the chords from that point on, a valueless one restores the one before, in the viewer and in
  the editor's text transposition.
- **An export is named by the header** (25): a single song as the import would name it, a setlist by its title. The
  code changes; the docs were right.
- **The swipe-to-remove on setlist rows is not coming back** (40). The docs that still describe it are corrected.
- **An unsaved editor draft survives the system ending the app** (44). It is kept outside the library, written when
  the app goes to the background, and reopened on the next launch.

## Defaults chosen while writing — confirm or override before executing

These were not put to the user; each plan names its choice and the alternative.

- **17 recommends not offering "Update file name" for a case-only difference** (`isNamed` ignores case as it already
  ignores Unicode form). A rename that only changes capitals cannot travel through a case-insensitive cloud folder,
  so the alternatives — a case-folded setlist lookup, or a provider `move` plus a local move on every other device —
  either fix only the display or need work on both ends. Cost: legacy capitalised names stay as they are, and
  libraries that already diverged under 4.2.x are not repaired. **This is the one judgement call worth a look.**
- **44 writes the draft on every platform, not only iOS**, on `ON_PAUSE`: it also closes Android swipe-away, mobile
  browsers discarding a tab and a desktop crash, and it can be tested on the desktop. Limiting it to iOS is one
  `expect val`.
- **05: chords written inside a comment are transposed and respelled** (they do not vote on the song's notation).
- **39: the pager bar counts setlist slots** (`3 / 4` for the third entry, a missing one included).
- **43: the notification-permission docs are corrected** rather than the permission being asked only once for ever.
- **15 is optional** (four Latin letters with no decomposition folded in file names).
- Not planned: several `{artist}` / `{subtitle}` directives keep only the last. That needs a product decision
  (which artist names the file?) rather than a fix.

## Lanes

Merge order **B, C, A, E, D**. B is the storage and sync base that C's 27 and 28 build on; C is the domain; A is
`:chordpro` plus one function of the view model; E is docs, CI, the web worker and one desktop file; D edits the
view model, `CampfireApp.kt` and the editor more than anyone else, so it goes last.

| Lane | Area | Issues, in this order |
| --- | --- | --- |
| A | `:chordpro` | **01, 03, 02, 05, 06**, 04, 08, 07 |
| B | Local storage and sync | **10, 11, 09, 16, 17, 14**, 12, 13, 15, **18, 19**, 20 |
| C | Domain and repositories | **21, 22**, 23, 24, 25, 26, 27, 28 |
| D | `:presentation` | **33, 31, 30, 29, 34, 32, 35, 36, 41, 42**, 37, 38, 39, 40, 43, **44** |
| E | App shells, web worker, docs, release | 45, 46, 47, 48, 49 |

The order within a lane is not numeric where plans depend on each other:

- **A:** 02 needs 01 (continuations, `ChorusRecall.blocks`, the serializer's piece walk) and 03 (`rewriteLines`); 05
  and 06 edit what 02 writes. 04, 08 and 07 are independent and last to keep rebases trivial.
- **B:** 10 → 11 → 09 → 16 (the storage and the engine's read path); 17 → 14 (with 17 as recommended, 14 becomes
  hardening); 14 and 16 both change `uniqueName`'s signature; 18 → 19.
- **C:** 21 → 22 (22 edits the conflict condition and the Replace path 21 writes).
- **D:** the view model: 33 → 31 → 30 → 29 → 34 → 44; `CampfireApp.kt`: 32 → 35 → 41 → 44; the editor: 36 → 42 → 44;
  36 needs 35's `WindowInsets.contentEdges`; 44 needs 30's consent-jump guard and lands last.
- **E:** independent.

### Where the lanes will conflict

- **`CampfireViewModel.kt`**: A's 02 (`renderSong`), B's 13 (three constants in the companion), C's 23 and 24 (small,
  local), and most of D. Different functions; D goes last and rebases over the rest.
- **Both `strings.xml` files**: B's 19, C's 23, D's 43 (removes `settings_sync_syncing`) and 44 add or remove keys.
  Keep every key either side added.
- **`FileNames.kt`**: B's 14, 16 and 17 in one lane; 14 and 16 both change `uniqueName`.
- **`SongLocalSourceImpl.kt`**: B's 10, 11, 14 and C's 26, 27. C's 27 lands after B's 14.
- **`SyncRepositoryImpl.kt`**: B's 18, 19, 20 and C's 28. C's 28 lands after B's 18 and 19, and must agree with
  them on what a failed forget means.
- **`FileStorage.wasmJs.kt`**: B's 10 and 11, and E's 46 (the worker it drives; different functions).
- **Test fakes** (`GetScreenDataUseCaseImplTest`, `ExportLibraryUseCaseImplTest`, `FakeSyncCollaborators`): C's 23,
  24 and 26. Merge, never overwrite.
- **`app/desktop/CLAUDE.md` and `documentation/testing/05-linux.md`**: E's 45 and 47, different lines.
  **`documentation/publishing/ios-app-store.md`**: E's 48 and 49.
- **The root `CLAUDE.md`, `presentation/CLAUDE.md` and the module `CLAUDE.md` files**: nearly every lane. Resolve as
  a word-level three-way merge, never by taking one side's paragraph whole.

## The issues

| # | Title | Severity | Lane |
| --- | --- | --- | --- |
| 01 | A chorus cut by a comment is recalled half, and every cut section shows its heading twice | wrong rendering | A |
| 02 | A `{transpose}` in the middle of a song moves the whole song | wrong chords | A |
| 03 | Two tab environments in one section move by different octaves in the viewer and the editor | wrong transposition | A |
| 04 | A song written only as tablature is treated as a song without chords | wrong behaviour | A |
| 05 | Chords written in a comment are never transposed or respelled | wrong chords | A |
| 06 | A key written as `G major` or `A minor` is never transposed | wrong key | A |
| 07 | A tag typed on a Mac and the same tag typed elsewhere are two tags | minor | A |
| 08 | Four small `:chordpro` disagreements (highlighter, repeat counts, delegate environments, an empty tab's label) | minor | A |
| 09 | One library file the storage cannot read stops every sync run | major | B |
| 10 | A file deleted between the existence check and the read throws instead of reading as missing | minor | B |
| 11 | A folder that cannot be listed or a file that cannot be deleted is reported as an unknown failure | minor | B |
| 12 | A UTF-8 file with one stray code-page byte is decoded entirely as Windows-1252 | major | B |
| 13 | A stored text size outside the app's range is used as it is | minor | B |
| 14 | A rename differing only by case and Unicode form is numbered needlessly — and half a fix deletes the file | latent data loss | B |
| 15 | Latin letters with no decomposition (ə ɛ ɔ ŋ) become separators in file names | optional | B |
| 16 | A conflict copy takes a name only free on this device, and swaps two songs' identities | major | B |
| 17 | A rename that only changes case never reaches other devices, but the setlists that follow it do | major | B |
| 18 | A connection given up after the token exchange keeps the tokens, and the next launch syncs | major | B |
| 19 | A secret store that fails for a moment looks like no connection, and reconnecting overwrites the good token | minor | B |
| 20 | The sync guide promises that a restored phone's first sync "duplicates nothing" | docs | B |
| 21 | Replace overwrites a library song that the same import brings back unchanged | **data loss** | C |
| 22 | An import records a song under a spelling of its name the library does not list | major | C |
| 23 | Renaming or deleting a song misses setlists the cache has not seen; a deletion stops at the first failure | major | C |
| 24 | One unreadable part of the library empties both list screens on the first read | minor | C |
| 25 | A single song and a setlist are exported under their file names instead of their headers | minor | C |
| 26 | A library export leaves out readable songs the scan has not seen, and calls them unexportable | minor | C |
| 27 | A rename whose file cannot be read back is reported as "nothing moved", though it moved | minor | C |
| 28 | A reinstall that fails to forget the old sync credentials restores them on the next launch, for good | major (iOS) | C |
| 29 | Quick taps on a setlist song's transposition stepper are lost | wrong behaviour | D |
| 30 | A sync connection finishing at start up can throw away unsaved editor text | data loss (rare) | D |
| 31 | Web: the browser's Forward into a search reopens it empty | minor | D |
| 32 | Android: the "update required" screen does not cover open dialogs, sheets and menus | major | D |
| 33 | The launch screen never goes away when a first run cannot read its preferences | major (rare) | D |
| 34 | A sheet or dialog about a song stays up after the song has gone | minor | D |
| 35 | Android landscape: lists, lyrics and the editor run under the camera cutout | wrong layout | D |
| 36 | Editor: the caret goes under the on-screen keyboard anywhere but at the end of the song | major | D |
| 37 | An exported file can lose its extension (desktop) or gain `.txt` (Android) | minor | D |
| 38 | Some filter and order changes do not scroll the lists back to the top | minor | D |
| 39 | The pager bar's "2 / 3" disagrees with the setlist's own numbering when an entry's file is missing | minor | D |
| 40 | The docs describe a swipe-to-remove on setlist rows that no longer exists | docs | D |
| 41 | Web without a touchscreen: the launch mark gets the desktop's slower exit | minor | D |
| 42 | Ctrl / Cmd + S does nothing in the editor while the preview is the only pane | minor | D |
| 43 | Small `:presentation` fixes: notification docs, stepper docs, iPad share popover, an unused string | minor | D |
| 44 | iOS: unsaved editor text is lost when the system ends the app in the background | data loss | D |
| 45 | Linux window opens at 1x on a HiDPI screen since the window class is set first | regression | E |
| 46 | A failed save on older Safari leaves the file half new, half old | data damage (rare) | E |
| 47 | The Linux and Windows test scripts point at the wrong folder, a missing command and an import that cannot happen | docs | E |
| 48 | The store publishing guides ask for work that is already done and name a field that does not exist | docs | E |
| 49 | A release that forgot to raise the build numbers fails only at the Play upload | release safety | E |

## What this review could not do

**Nothing here has been run.** Every plan was written from the code, the library sources and the platform
documentation, and each one's "Verification" section is how to confirm it before fixing it. The platform checks
the fifth review listed as owed are still owed, and several plans add to them:

- a Dropbox account with two devices: 16 (conflict copies), 17 (a case-only rename), 18 (a cancelled connection),
  and whether Dropbox treats NFC and NFD spellings as one path (a Mac Dropbox client writes NFD) — the engine
  assumes it does, and nobody has checked;
- an iPhone: 44 (a draft across the system ending the app), 28 (a reinstall), 36 (the keyboard over the editor),
  43 (the iPad share popover);
- an Android phone with a cutout, in landscape (35), and an internal Play track with update priority 5 (32);
- Linux on X11 with `Xft.dpi` set (45);
- Safari before 26, or Chrome with `createWritable` removed, with a quota that runs out mid-save (46);
- macOS and Windows desktop for the case-insensitive import paths (14, 22).
