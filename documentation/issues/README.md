<!--
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
  distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->

# Twenty-second review: structure, readability, testability

**Reviewed commit:** `2940b0e0a` on `master` (2026-10-08). **Angle:** readability, scalability, testability, developer
satisfaction and the SOLID principles — a structure review, not a bug hunt. Nothing here changes what the app does.
The uncommitted change to `documentation/TO_DO.md` at the time of the review is the user's own and untouched.

The user asked for the simple refactors to be done straight away and for plans only for the complex ones, and for
anything touching the module structure to be confirmed first. So this sweep is in two halves:

1. **Already landed on `master`** (unpushed) — behaviour-preserving splits and moves, see "Done in this sweep".
2. **The plans in this folder** — the refactors that need judgement, touch threading, DI, CI or the module structure.
   Nothing in them has been started.

## Done in this sweep

Every commit is a verbatim move (verified by script per lane: the original's code lines equal the union of the new
files' lines, `private` → `internal` aside) or a small extraction with its tests passing; each compiled on the
platforms it touches.

- **One file per non-private top-level composable** across `:presentation` (dialogs, song details, components, app
  chrome, settings, editor, lists, metronome, import report, web effects), the desktop title bar and the screenshot
  tool's system chrome. Private sub-composables used by one composable stay in its file. `Dialogs.kt` (2652 lines),
  `SongLyrics.kt` (3169), `SongDetailsScreen.kt` (1929), `ExportScreen.kt` (1843), `CampfireApp.kt` (1725),
  `ListItems.kt` (1379) and the rest are gone or small. Files still over ~500 lines are each one indivisible
  declaration (listed in "Follow-ups noticed while splitting").
- **`:chordpro`:** the parser's state builders and chorus recalls in files of their own (982 → ~350 lines);
  `ChordProSyntax` cut into `ChordProLines`, `ChordProDirectives`, `ChordProMetaItems`, `ChordProHeaderLayout`,
  `ChordProEnvironments`, `ChordProTokens`; one `metaKey`/`metaValue` pair; the chord rewrite engine and the caret
  mapping out of `ChordProTransposer` (`ChordProChordRewriter`, `ChordProOffsetMapping`); one `ChordProVocabulary` for
  constants 14 files declared on their own (note: `MAX_HAND_FINGER` was folded into `ChordVoicings.MAX_FINGERS`, both
  4); `ChordSheetConverter.convertSong` broken into steps; seven helpers nothing outside the module used made
  `internal`.
- **Data and domain:** sync models, name folding and summaries in files of their own; the repository Koin module moved
  into its `.implementation` package like every other module's; `DropboxTransport` and `DropboxTokens` out of the
  provider and one `exponentialBackoffSeconds`; `PdfTextExtractor` split (`PdfContentInterpreter`, `PdfContentBudget`,
  `PdfLineLayout`, `PdfRunningLines`); the web OPFS storage split (`OpfsBridge`, `OpfsWriter`); one common
  `FileSecretStore` behind `DesktopSecretStore` / `WebSecretStore`; the screen-data use case's pure helpers
  (`SongCatalog`, `SongSorting`, `DataStates`); `ImportRun` and `ImportTriage` in the import use cases; a
  `LibraryListRepository` base replacing 15 copies of the cache update in the song and setlist repositories.
- **`:presentation` packaging:** the view model's nested types moved to the packages that use them (`DialogType` →
  `ui.dialogs`, `Message` → `ui.messages`, …; step 0 of plan 01); new packages `ui.playing` (tempo, capo,
  transposition overrides), `ui.screens.export`, `ui.songLayout` (what the PDF shares with the song page — `ui.print`
  no longer imports a screen), `ui.search`, `ui.firstRun`, `ui.update`; `Stepper`, `BeatRow`, `ChordCell` and friends
  out of the screens that first used them; `FONT_SCALE_SAVE_DELAY_MILLIS` renamed `PREFERENCE_WRITE_DEBOUNCE_MILLIS`;
  tests next to their code; new tests for the export preview geometry, the library size rounding, the editor's,
  setlist list's and song list's pure helpers and one `timeSignatureOf` bridge; `isCtrlWheelZoomOwnedByApp` instead of
  the launch-screen flag; stale paths in the docs fixed. Not moved, by actual usage: the sheet infrastructure (only
  `ui.dialogs` uses it), the settings building blocks (a set, better moved together later), the song-info helpers.
- **Shells and build:** the unused `android-library` alias removed; `kotlin("test")` added to every library's
  `commonTest` by the convention plugin; `app/android` and `app/desktop` sources in `src/main/kotlin`; desktop `main()`
  split (`configureBeforeToolkit`, `MacQuitHandlerEffect`, `WindowInputEffects`, `StartupLog.kt`, `TrainingRun.kt`);
  `CampfireMainActivity` split (`SyncServiceStarter`, `UrlOpening.kt`, `CampfireMetronomeService.start`, the intent
  readers in `AndroidFileImport.kt`).

**Range:** `2940b0e0a..94d4b7fcd`, 80 commits, one line each, no attribution. Verified after the last merge: every
module's `desktopTest`, and `:app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64
:app:web:wasmJsBrowserDistribution :app:desktop:packageDistributionForCurrentOS :tools:screenshots:compileKotlin
:app:di:compileKotlinDesktop` — all green.

## Headlines (plans)

1. **`CampfireViewModel` is one 4.6k-line class with 53 constructor parameters** and ~80 composables taking it whole
   (01, with 02–09 preparing it). The single biggest readability and testability lever in the codebase.
2. **`SyncRepositoryImpl` does seven jobs behind three locks and real-time tests** (20–24). 22 carries a trap the
   challenge found: see the Koin rule below.
3. **CI compiles only the desktop target before a release** (61): an `androidMain`, `iosMain`, `wasmJsMain` or shell
   break is first seen inside `publish-all.yml`, after the tag exists.
4. **The CLAUDE.md set is ~380 KB** (root 104 KB loaded every session, `presentation/CLAUDE.md` 274 KB with four
   headings) (60).
5. **`:chordpro` writes its line-reading rules seven times and its metadata vocabulary in six places** (40, 41).

## Repo-wide rule found by the challenge

**Never give a defaulted parameter to the constructor of a `@Single` / `@Factory` / `@KoinViewModel` class, or to a
`@Single` module function.** The Koin compiler plugin 1.2.1 runs with `skipDefaultValues = true`: it compiles, passes
`:app:di`'s graph check and silently uses the default in production. Every plan below that adds a definition follows
this; any new one must too. (Worth a line in the root `CLAUDE.md`'s Koin bullet when the first such plan lands.)

## Index

| # | Plan | Payoff | Effort | Risk | Lane |
|---|---|---|---|---|---|
| 01 | Split `CampfireViewModel` into internal state holders behind a thin facade | high | L | medium | P |
| 02 | One generic `SongOverrides<T>` / `PendingOverrides<T>` for tempo, capo, transposition | medium | M | medium | P |
| 03 | Shared components take state and callbacks, not the view model | medium | M | low | P |
| 04 | An injected `SongRenderer` with the editor notation explicit | medium | M | low | P |
| 05 | One pure `songPlaybackOf` over one `PlayingOverridesSnapshot` | low | S | low | P |
| 06 | One `DebouncedPreference<T>` for the three debounced writers | medium | S | low | P |
| 07 | A sealed `SongEditTarget` instead of `isEditorDraft: Boolean` | low | M | low | P |
| 08 | `BrowserRoutes` in common code, decoupled and tested | medium | S | low | P |
| 09 | An owned `OverlayState`, an injectable persistence request, shared types out of `screens/` | low | S | low | P |
| 10 | No service-locator lookups for the cover loader and the web metronome | low | S | low | P |
| 11 | One common, tested keyboard shortcut table | low | S | low | P |
| 20 | One `recovering` helper for the logged-failure blocks | low | S | low | D |
| 21 | Inject scope and clocks into the long-lived repositories; tests on virtual time | medium | M | low | D |
| 22 | Split `SyncRepositoryImpl` into collaborators | high | L | medium | D |
| 23 | `rememberDemoLibraryFiles` into a `DemoLibraryRepository` | low | S | low | D |
| 24 | `SyncEngine` → run context, pure `PassListing`, pure `DeletionGuard`, `ConflictResolver` | medium | L | medium | D |
| 25 | Stop exporting `:data:source:remote:api` to domain and presentation | medium | S | low | D |
| 26 | `SyncProvider` = `SyncConnection` + `SyncFolder` | low | S | low | D |
| 27 | `SyncStateLocalSource` split by client | low | S | low | D |
| 28 | One sealed `SyncRunEndingException` | low | S | low | D |
| 29 | One library-name identity rule and one tag normalization in `:data:model` | medium | M | low | D |
| 30 | `UserPreferences.withSongRenamed` / `withoutSongOverrides` | low | S | low | D |
| 31 | `JvmFileStorage` once, in a shared JVM source set | low | S | medium | D |
| 32 | **Module:** document and zip codecs into `:data:formats` | low | M | low | D |
| 33 | **Module:** sync into `:data:sync:implementation` | low | L | medium | D |
| 34 | An injected `Logger` port instead of 71 `println`s | low | M | low | D |
| 35 | Whole-document writes into a `WholeDocumentRepository` subclass | low | S | low | D |
| 40 | One internal `ChordProLineScanner` | medium | L | medium | C |
| 41 | One internal `MetadataKind` table | medium | M | medium | C |
| 42 | `ChordVoicings` split, cache resettable in tests | low | M | low | C |
| 43 | `:chordpro` subpackages | low | M | low | C |
| 44 | One chord-name visitor | low | S | low | C |
| 45 | A testable `MetronomeEngine` | medium | M | low | C |
| 46 | A common, tested `AudioClock` / `PlaybackGapTracker` | low | S | low | C |
| 47 | The PCM outputs' constants once on `AudioOutput` | low | S | low | C |
| 48 | `explicitApi()` for `:chordpro` and `:metronome:api`; `ChordProSerializer` out of the API | low | M | low | C |
| 60 | The CLAUDE.md set cut into small, directory-scoped files | high | L | low | last |
| 61 | Compile every platform in the tests workflow | high | S | low | B |
| 62 | One implementation for the publish workflows' repeated steps | medium | M | high | B |
| 63 | `.editorconfig`, ktlint (Spotless) and an MPL-header check | medium | M | low | B |
| 64 | Desktop packaging tasks and the web finisher into build-logic, tested | medium | M | medium | B |
| 65 | Gradle build cache, parallel, `setup-gradle` in CI | medium | S | medium | B |
| 66 | The configuration cache | medium | M | medium | B |
| 67 | A `campfire-koin` convention plugin | low | S | low | B |
| 68 | One tested sync-notification throttle for Android and iOS | medium | M | medium | S |
| 69 | Shared-text naming out of the Android shell, tested | low | S | low | S |
| 70 | One `store_http.py` for the store scripts | low | S | medium | B |
| 71 | Pinned, checksummed screenshot fonts | low | S | low | B |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|---|---|---|---|
| D | data, domain, sync | 20, 21, 23, 27, 26, 28, 22, 24, 25, 29, 30, 35, 34, 31, 32, 33 | `data/**`, `domain/**`, `app/di`, the `tools/screenshots` fakes 23/25 name, the imports 25 changes in `presentation` |
| C | `:chordpro`, `:metronome` | 40, 41, 44, 42, 43, 48, 45, 46, 47 | `chordpro/**`, `metronome/**`, the one `EditorToolbar.kt` KDoc 41 names |
| B | build, CI, store scripts | 65, 67, 63, 61, 62, 64, 66, 70, 71 | `gradle/**`, root build files, `settings.gradle.kts` (shared with 32), `.github/**`, `app/desktop/build.gradle.kts`, `app/web/build.gradle.kts`, `tools/screenshots/build.gradle.kts` |
| S | shells | 69, 68 | `app/android`, `app/ios`, new files in `presentation/.../ui/platform` |
| P | presentation | 09, 06, 02, 07, 04, 08, 10, 11, 05, 03, 01 | `presentation/**` (rest), `app/desktop` and `app/web` Kotlin that calls the view model, `tools/screenshots/Shot.kt` |
| last | docs | 60 | every `CLAUDE.md`, `.claude/skills/*` wording |

**Merge order: D, C, B, S, P, then 60.** D first because 25 and 29 change what `:presentation` imports and calls; C
next (independent); B before anything that adds a build step; S before P because both touch `ui/platform`; P last as
it touches the most shared UI files and 01 is the longest; 60 after everything, since nearly every plan edits a
`CLAUDE.md` and 60 moves that text.

**In-lane ordering the challenge settled:**
- D: 20 → 21 → 23 → 27 → 22 (22's `SyncIndexStore` takes 27's type); 24 after 22 (22 leaves `SyncEngine`
  unannotated and builds it in a module function); 26 and 28 both edit `SyncProvider.kt`; 29 and 30 both create
  `:data:model`'s `commonTest` (the first one creates it); 24 and 29 both touch `SyncKey.folded` — the second finds it by
  expression; 34 after 20–24.
- C: 40 → 41 (both touch the highlighter and prettifier) → 44, 42 → 43 → 48 last; 45 → 46 → 47 (all edit
  `SilentAudioOutput`).
- B: 65 → 66; 64 step 4 before 66's web fix; 71 after it; 61, 63, 64, 65 all add to `tests.yml`; if 62 lands first its
  `setup-jdk` action is where 65's `setup-gradle` and 61's jobs go. 62's steps 1 and 2 are one commit (a publish run
  between them would fail).
- P: 09 before 01's `DialogHost` step; 02 before 05 and before 01's `PlayingOverrides` step; 04 before 01's
  `SongRenderer`/`EditorSession` steps; 06 before 01's font-scale/metronome/export steps; 07 before 01's
  `SongMetadataEditing` step; 03 before 01's last step. 02 and 06 both rename `FONT_SCALE_SAVE_DELAY_MILLIS` — use
  whatever name the packaging pass left (it may already be renamed). 03 and 10 both add to `MetronomeContext.kt`.
  01's step 0 (nested types out of the view model) was done by this sweep's packaging pass: verify and skip.

**Shared files.** `CLAUDE.md` files and `strings.xml` are merged word by word, every sentence from both sides kept.
`settings.gradle.kts`: D (32, 33) adds modules, B (65, 66) adds settings — keep both. `libs.versions.toml` and the
root plugins block: B only (63, 67), plus 32's new module needs nothing there.

## Decisions — answered by the user on 2026-10-08

| # | Question | Answer |
|---|---|---|
| 01 | Make the view model's holders Koin `@Factory`s after the split? | No — keep them built inside the view model |
| 29 | Which case rule decides "the same library file name" everywhere? | A — per-character fold (what `equals(ignoreCase = true)` does) |
| 32 | **Module:** create `:data:formats` for the document and zip codecs? | A — create it (document + zip; `backup/` stays) |
| 33 | **Module:** move sync to `:data:sync:implementation`? | **B — create it** (last in lane D, after 20–24 and 27, with the prerequisites the challenge listed) |
| 43 | `:chordpro` subpackages | **B — phase 1 and phase 2** (`chordpro.syntax`, then `chords`/`edit`/`convert` for the public objects) |
| 48 | Explicit API mode; where `ChordProSerializer` goes | both modules; move the serializer to `commonTest` |
| 60 | How far to restructure the CLAUDE.md set | all seven steps; cross-module behaviour into `ui/CLAUDE.md` |
| 61 | When the macOS (iOS compile) CI job runs | on every trigger |
| 63 | Lint tool, existing code, line length | Spotless + ktlint; curated rules, no bulk reformat; 150 |
| 66 | Third-party plugin breaks the configuration cache | `fail` + `notCompatibleWithConfigurationCache` on those tasks |

## Checked and found solid

`SyncPlanner`, `ImportPlanner`, `SyncedPreferences`, the cover searches (pure and tested); `:metronome:api` (minimal);
the sequencer, synthesizer and mixer; `:chordpro`'s model package; `:app:di`; the `local.properties` injection and
`PREFER_SETTINGS`; `IosFilePicker` living in `:app:ios`; the thin use cases (they follow the stated convention).

## Dropped after verification

- **Use cases as `fun interface`** — three have default parameters a `fun interface` cannot; one test fakes a use case.
- **`ChordProDefinitions.shapeOf` as a "second grammar"** — it writes ChordPro's own `{define}` syntax, a different
  format from the preference one.
- **A shared Android/desktop PCM feed loop** — the loops differ (shorts vs little-endian bytes, failure test, thread
  priority, release); three lambdas to save ten lines.
- **Unifying `rewriteBlock`/`rewriteLine` with the chord-name walks** — they build a new song rather than walk one.
- **The web launcher's inline JavaScript into `launch.js`** — unsafe under the offline-boot rules: the page is the one
  unversioned file, and a kept page whose launcher is missing could not start offline.
- **`campfire-jvm-app` / `campfire-android-app` convention plugins** — one `jvmToolchain` line in four modules.
- **A recovering helper and use-case grouping as simple refactors** — superseded by 20/22, and grouping thin use cases
  into multi-class files runs against this sweep's small-files direction.

## Challenge

Every plan was challenged by a fresh agent that did not write it: **33 amended, 15 sound, none dropped.** Notable
amendments: 22 (the Koin default-value trap; lock order; tests that could not fail), 32 (coroutine dependencies, the
goldens stay with the local source), 04 (an internal type in the public view model constructor would not compile), 08
(the wasm opt-in must leave the moved file), 41 (the INVALID ⇔ dropped test would have failed on `{cover:}`), 62
(composite actions must exist in the workflow's commit before the fetch step), 66 (`problems=warn` reuses unsafe
entries), 70 (Apple's log lines would change).

## Follow-ups noticed while splitting (not planned)

Candidates for a later look, each a judgement call rather than a move: `SongSectionsLayout` (564-line composable) as a
pure measure/place policy; `SongDetailsAppBar`, `LoadedSongEditor`, `ExportScreen` and `SetlistList` each one
350–520-line composable; `CampfireDialogs`' 240-line `when` over every `DialogType`; `SongTagsDialog` and
`SongLanguagesDialog` near-duplicate checklist sheets; `SongLyrics`' ~25 parameters; `PrintLayouter` (577 lines);
`NavigationScrim` written as a side effect of transition lambdas; the import report's pinned item counts kept apart from
the builders that emit the items.

## Manual checks owed

For the refactors already landed (behaviour-preserving, compiled on every platform, unit tests green), before the next
release: the half-hour release check on a Mac, with extra attention to the desktop start, quit with unsaved editor text
(Cmd+Q asks), pinch zoom and the "Started in" log line (desktop `main()` was split); sharing a file and text to the
Android app, a background sync notification and the metronome with the screen locked (`CampfireMainActivity` was
split); a PDF import and a Word import (the PDF extractor was split); a web launch (OPFS storage split); a Dropbox sync
run (Dropbox provider split). Regenerate the Baseline Profile before the release (class names moved).

Each plan carries its own manual check for when it executes.
