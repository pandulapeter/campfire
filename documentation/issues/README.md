# 46th review: UI performance — the first song details open of a session

Reviewed at `284e1acf3` on `master` (plans checked against `c92ae5e33`, 2026-10-26). Angle: UI performance, from the
user's report "The very first time during the session when the song details screen is opened feels laggy", confirmed
on the desktop (JVM).

## Headlines

- The lag is the JVM warming up, once per process: ~360 ms of main-thread work on the first open versus ~40–50 ms on
  every later one; a fresh view model in a warm JVM costs the later-open amount. Plan 03 warms the part of it that needs
  no composition (~40 ms) in the background after launch; the rest is the window's own composables loading for the first
  time, which only the window can do.
- Every open, on every platform, reads the song's text only after the screen's first frame, so the first page's
  in-place model and layout land in the middle of the navigation slide (plan 02).
- `SongDetailsScreen` and `CampfireDialog` are over HotSpot's 8,000-byte limit and are interpreted forever on the
  desktop, a rule already broken once and still unenforced (plan 01).
- The Android Baseline Profile has no rules for Chords, Scales, Settings, the editor, or bottom sheets: the walk skips
  steps silently (plan 04).

## Fixed during the review

- `125e3b3a1` Restore the iOS run configuration that the navigation rail change removed by accident.
- `c92ae5e33` Say in the remote source's documentation that the import download fetches shared links.

(Both docs or run configuration; nothing on this angle was simple enough to fix on the spot.)

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | [Let the desktop JVM compile composables past HotSpot's 8,000-byte limit](01-let-the-desktop-jit-compile-huge-composables.md) | medium | A |
| 02 | [Ask for a song's text as its screen is pushed](02-read-a-songs-text-as-its-screen-is-pushed.md) | medium | A |
| 03 | [Warm the song view's non-UI path once, in the background, after a desktop launch](03-warm-the-song-view-once-after-a-desktop-launch.md) | low | A |
| 04 | [Check that the recorded Baseline Profile covers every walked screen, and walk the screens it never reached](04-make-the-baseline-profile-walk-report-the-steps-it-skips.md) | low | B |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| A | desktop JVM and the song details open | 01, 02, 03 | `app/desktop/**`, `presentation/src/desktopMain/**`, `ui/screens/songDetails/` (`SongDetailsScreen.kt`, `SongDetailsAppBar.kt`, `SongDetailsPagerEffects.kt`, `CLAUDE.md`), `ui/state/songText/SongTextStore.kt` and its test, `ui/CampfireViewModel.kt` (or, under D1 = B, `ui/dialogs/CampfireDialogs.kt` and the check) |
| B | Android Baseline Profile generator and its check | 04 | `app/baselineprofile/**`, `app/android/build.gradle.kts` (the `generateBaselineProfile` check only) |

Lane A is one lane because 01 and 02 both edit `SongDetailsPagerEffects.kt` (KDocs) and 01 and 03 both edit
`app/desktop/CLAUDE.md` (plan 03 only under D2 = B); 03 comes last because plan 01's flag changes what is left to warm,
and under D2 = B its probe must be taken with the flag in place.

**Merge order: B, then A.** B touches nothing shared; A touches the most shared UI files (and the files the user was
editing during the review).

Shared files: none between the lanes. `strings.xml` is untouched.

## Decisions (taken by the user, 2026-10-26: D1 = A, D2 = A, D3 = fail — each the recommendation)

- **D1 (plan 01)** — **A: `-XX:-DontCompileHugeMethods` in the desktop `jvmArgs`** (recommended: one line covers every
  present and future method, reaches `run` and every package, no check to maintain; the flag is verified on the
  toolchain JBR) or B: split both composables under 8,000 bytes and add a class-file check that fails past it.
- **D2 (plan 03)** — **A: the non-UI warm-up only** (recommended: the `CampfireDestination` serializer and the demo song's
  model on `Dispatchers.Default`, ~40 ms, ~30 of it off the click itself), or B: A plus a background offscreen
  composition of `SongLyrics` (not recommended: no precedent in the repo for a second composition off the EDT beside a
  live window, its own font cache, a gain the JIT share mostly escapes; shipped only if its probe shows ≥ 100 ms), or
  neither (drop the plan).
- **D3 (plan 04)** — **fail `generateBaselineProfile` when the written profile lacks a walked screen** (recommended; the
  files are written before the check, so the recording is kept) or only print the missing screens.

## Challenge

Ran on 2026-10-26 against `01e6ff0d5` (codebase-review step 7), one fresh read-only pass over all four plans:

- **01 sound.** The flag starts the toolchain JBR (21.0.10+7-b1163.105) as a `product` flag; `jvmArgs` reach `run`, the
  IDE's Desktop configuration, `runRelease`, and every image's `Campfire.cfg`, which the CDS training and the MSIX task
  keep. Option B's check would enforce a HotSpot-only rule: ART's huge-method threshold is far higher.
- **02 amended.** The read ahead centres on the page the screen is on (`navigator.currentSongFileName`), since the
  listener also fires when a paged pager comes back on top; the plan now says plainly that the work moves into frame 0
  (two in-place builds for a setlist open; on a cold JVM, the whole first-open cost delays the slide's start instead of
  stalling it) and does not shorten the first open by itself.
- **03 amended.** Recommendation flipped from B to A, started from `CampfireDesktopApp` (internal, no public entry
  point, no `:app:desktop` or `TrainingRun.kt` change, no probe). B's premise that `:tools:screenshots` renders scenes off
  the EDT was false (it uses `Dispatchers.Main`); each scene has its own `FontFamily.Resolver`, so B warms none of the
  window's text caches; a few renders stay below the JIT's thresholds. Severity lowered to low.
- **04 amended.** Failing the instrumentation run would most likely stop Gradle before the profile is copied into
  `src/main/generated`, which only an emulator could confirm; the check now reads the written profile for one rule per
  walked screen in a `doLast` of `:app:android:generateBaselineProfile`, which replaces the per-step recorder. Lane B now
  owns that one block of `app/android/build.gradle.kts`.

## Measurements

- Offscreen desktop probe in `:tools:screenshots`, demo library, 360x640dp: first open in a JVM ~360 ms main-thread
  over 4 frames (one 222 ms); later opens ~40–50 ms (`SongGridSearch` ~5 ms, the rest of the song layout ~10 ms); a
  fresh Koin graph and view model in a warm JVM costs a later open's amount.
- The ~286 ms excess (overlapping): class loading ~107 ms (the session's first `BasicTextField` ~13 ms, from
  `StepperField`); not-yet-compiled code ~117 ms (`SongLyrics` → `SongSectionsLayout` / `SongSectionItem` +65,
  `SongMetadataSection` / `SongPlayingControlsRow` / `Stepper` +33, `searchGrid` / `flowIntoPages` +16,
  `rememberSongLyricsModel` / `prepareSongLyrics` +13, `SongDetailsAppBar` +15, `NavigationChromeScaffold` transition
  +49); invokedynamic linkage ~31 ms; Skia's first paragraph ~24 ms; string-table static initializers ~8–10 ms; the
  `CampfireDestination` serializer on the click ~29 ms. Harmony 1.7 ms, `ChordVoicingSearch` 1.3 ms.
- javap over every module's desktop classes (build of 2026-10-26 12:29): methods over 8,000 bytes are
  `SongDetailsScreen-jIwJxvA` 8,294 and `CampfireDialog` 8,291, plus five generated `static {}` blocks (chord tables,
  string tables; run once). `SongLyrics-vkPPWEM` is at 7,000; nothing else passes 7,000.

## Checked and found solid

- The chord shape tables are warmed at startup off the main thread (`PreferencesController`, `ChordVoicings.prepare` on
  `Dispatchers.Default`).
- Koin is not on the open's path; the metronome's audio is created only on a click; covers come from Coil's memory
  cache.
- The song file is read off the main thread, behind the repository's LRU; startup does not block the main thread.
- Lazy lists have stable keys and `contentType`; the editor's incremental caches hold; the Chords tab searches off the
  main thread.

## Checked, real but below a frame, not planned

- Selection mode rebuilds the list per tick (~1 ms at 3,320 songs).
- Per-row `AnimatedContent` / `AnimatedVisibility` transitions (unmeasured, nothing seen).
- List items recompose on scroll start and end (deliberate: `listItemAnimation` switches off while scrolling).
- Language display names worked out per row (only libraries with several languages).
- `ScalesScreen`'s `associateWith` maps per recomposition and a per-cell offscreen layer.
- ~14 icons loaded synchronously on first use; JetBrains Mono's first load (~2 ms).
- The double scale walk (~2 ms measured).
- The web's `InferredSongKeys` on its one thread.
- The `ChordVoicings` lazy lock contended right after launch.
- `state/Breadcrumbs.kt`'s `userPreferences.filterNotNull().map { it.toString() }.distinctUntilChanged()` plus
  `changedPropertyNames` on the main thread per preference write: one linear pass over a few tens of KB of
  `toString` (the per-file override and fold maps) per tapped write, around a millisecond; moving it off the main thread
  (`flowOn(Dispatchers.Default)`) would let "Settings changed" print after a later "Screen:" line, breaking the step
  order the log exists to keep.

## Dropped after verification

- **Defer the in-place model of `targetPage - 1` when a song is opened from a setlist** (`buildsModelInPlace`:
  `page == currentPage || page == targetPage || page == targetPage - 1 || isVisible`): it is there for the pedal — a
  step back from the top of a song scrolls the page before it to its end at the moment of the press
  (`pageScrollStates[pagerState.targetPage - 1]?.let { … scrollTo(state.maxValue) }`), which a page without a model
  cannot do; deferring it saves one model build (a few ms warm) at the cost of the pedal guarantee, and plan 02 already
  moves that build out of the transition by reading the neighbour's text as the screen is pushed.
