<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Make the first frame of an opening bottom sheet cheaper: measure the filter chips once, leave the list behind it alone, and train the desktop's class archive on a sheet

**Challenged:** amended — step 1's cache could serve stale widths (a count changing a chip's width keeps the chip count, and a group whose measure Compose skips never clears it), so it now lives for one `FilterGroupsLayout` measure only, and first checks text's own intrinsic cache; step 4's training run passes the onboarding first-run shows.

**Landed:** steps 1 and 3 (`ChipIntrinsicsCache`; `SongsScreen` reads the open dialog only in the handlers that need
it, so a sheet opening no longer recomposes the list): warm `sheet:open` 19.0 → 12.6 ms. **Left:** step 2, since no
profile could name `animateBounds` as `boundingRectInRoot`'s caller (a JFR run of the benchmark caught too few samples in
the sheet's frame); step 4 (a), since `app:desktop` reaches nothing of the view model it needs (`navigator` and
`dialogHost` are internal to `:presentation`): it needs a public entry on `CampfireViewModel` or `app:desktop` made a
friend of `:presentation`, as `tools/screenshots` is.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all (the archive part: Windows, Linux)  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songFilters/CollapsibleChipFlow.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songFilters/FilterGroupsLayout.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/songFilters/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`
- `app/desktop/src/main/kotlin/com/pandulapeter/campfire/TrainingRun.kt`, `app/desktop/src/main/kotlin/com/pandulapeter/campfire/CampfireDesktopApplication.kt` (hand the training run the view model)
- `app/desktop/CLAUDE.md` (Packaging: what the training run does)
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/components/songFilters/` (existing chip flow tests, if the cache logic is pure)

## Problem

Opening the song filters sheet takes one 13.8 ms frame warm (p90 23.5 ms; layout 9.5 ms of it) and 132 ms the first
time in a process (the probe's `sheet:open`). In the warm frame's layout samples:

- `animateSheetContentHeight` is 39 % inclusive, but that is the whole sheet content measured under it
  (`SheetHeightAnimation.kt:57-59`); the modifier's own work is a few lines. Inside it, `FilterGroupsLayout` 17 %,
  `CollapsibleChipFlowMeasurePolicy.maxIntrinsicHeight` 9 %, its approach 6 %, its measure 5 %, paragraph layout 8 %
  (leaf), and `RectManager.boundingRectInRoot` 16 % (leaf).
- `FilterGroupsLayout` asks every chip group both intrinsic heights before measuring it, in the lookahead pass
  (`FilterGroupsLayout.kt:92-96`):

  ```kotlin
  chipBudgets(
      available = availableHeight.roundToPx() - fixedHeight,
      natural = chipSlots.map { measurables[it].maxIntrinsicHeight(width) },
      minimum = chipSlots.map { measurables[it].minIntrinsicHeight(width) },
  )
  ```

  and each of those walks every chip twice (`CollapsibleChipFlow.kt:249` and `:261`:
  `val widths = chips.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(lineWidth) }`, then
  `chips.maxOfOrNull { it.maxIntrinsicHeight(lineWidth) }`), so a chip's text is laid out four times for intrinsics
  before the lookahead measure lays it out again, then the approach pass places it.
- Every chip carries `Modifier.animateBounds(lookaheadScope)` (`CollapsibleChipFlow.kt:91`), which tracks its bounds in
  the root on every placement: the likely source of the `boundingRectInRoot` share (verify with the probe-style stack
  sample or JFR before acting on it).
- The same frame re-runs the list behind the sheet: the trace counted `SongListRow` ×22, `itemsIndexed` ×22,
  `SectionHeader` ×7, `SongsScreen` ×1 in `sheet:open` frames 0–2. `SongsScreen` reads the dialog whole
  (`SongsScreen.kt:97`, `val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()`) for
  back handlers and `HideKeyboardWhenScrolledDown(listState, isEnabled = visibleDialog == null)` (`:120`), and some row
  parameter is new on that recomposition (the compiler report has `song: Song` and `songFilter: SongFilter` unstable,
  compared by instance; `SongList.kt:146` builds `SongCardOptions.forSongs(userPreferences)` in composition).
  `CampfireContent` reading the dialog whole is plan 10 (c).
- The 132 ms first open is the first `ModalBottomSheet` and `ComposeSceneLayer` of the process: classes loaded and
  verified, code interpreted. The desktop's existing warm-up (`SongViewWarmUp.kt`, 46th review) runs only the
  composition-free part of a song open; the release's class data archive (`recordClassDataArchive`, Windows and Linux)
  is trained by a run that ends once the demo library is drawn (`TrainingRun.kt:30-45`), so no sheet class is in it.

## Fix

1. **One intrinsic walk per group per pass.** First check it is a cost at all: `BasicText`'s layout cache already
   keeps a text's intrinsic widths and its height per width, so the four walks may be four cache reads; act only on
   a profile showing the walks' own time. If it is: compute the chip widths and the line height once per
   `FilterGroupsLayout` measure, not "until the policy's next `measure`". A cache keyed by width and chip count and
   cleared by the group's measure is unsafe twice over: a count moving ("Rock 12" → "Rock 13") changes a chip's width
   without changing the count, and a parent that re-asks intrinsics while Compose skips the group's own measure
   (unchanged constraints) leaves it uncleared, so the budgets would be decided from stale widths until something else
   measured the group. So `FilterGroupsLayout`'s measure opens the cache before `chipBudgets` and closes it right after
   (a small holder it hands its groups' policies, e.g. through the `chip` slot's parent data or a shared object
   remembered by `FilterGroupsLayout`), and `maxIntrinsicHeight` / `minIntrinsicHeight` use it only while it is open. Better still, the lookahead `measure` reuses those widths for its line breaking instead of the
   placeables' only if they agree (they are the same text at the same constraints; assert it in a test, not at run
   time). The pure parts (`chipLines`, `collapsedChips`) are unchanged; if the cache is a class, unit-test it.
2. **Arm the chips' `animateBounds` after the sheet's first layout.** Hold `var isPlacementAnimated by remember {
   mutableStateOf(false) }` in `CollapsibleChipFlow`, set it in a `LaunchedEffect(Unit) { withFrameNanos { } … }`, and
   apply `Modifier.animateBounds(lookaheadScope)` to the chips and the group only when it is on. Nothing moves in the
   first frame (no user change can arrive before it), so no animation is lost; a sort or a count change after it travels
   as now. Only if a profile (the probe's stack sampler, or JFR) confirms `animateBounds` is the `boundingRectInRoot` caller.
3. **Leave the list alone.** Move `SongsScreen`'s dialog reads into the small composables that need them (the two back
   handlers and `HideKeyboardWhenScrolledDown`), each collecting `visibleDialog` itself or reading a derived
   `isDialogOpen`, so a sheet opening recomposes those and not the screen. Then find the row parameter that is new
   (Compose's recomposition counts in a debug build, or the tracer approach of the probe): remember
   `SongCardOptions` in `SongList` on the fields it reads, and if `Song` / `SongFilter` instances are rebuilt upstream on
   unrelated emissions, keep their instances (the repository's `DataState` already caches) or add `:data:model` and
   `:domain:api`'s models to a Compose stability configuration file for `:presentation`.
4. **The first sheet in a desktop process.** Options:
   - (a) *Train the class archive on a sheet*: `TrainingRunEffect` gets the view model; after the demo library is
     drawn — a training run is a first run, so the onboarding card is on top then (`TrainingRun.kt`'s own comment) and
     must be skipped through the navigator first, or the song opens over it and the sheet is held back — it opens one
     demo song and goes back, then opens the Preferences sheet (always there, unlike the filters) and closes it, each
     through the view model with a few frames between, then exits, still inside the run's 15 s bound. The archive then maps those
     classes. Windows and Linux only, as the archive is.
   - (b) *A composition warm-up*: compose a sheet into a throwaway `ImageComposeScene` on a background thread after the
     library loads. Composition is not thread-confined, but a second scene sharing the global snapshot and the app's
     singletons is a new source of bugs for a 100 ms that happens once.
   - (c) *`Class.forName` preloading* of a list of classes: brittle with ProGuard's joined jar and every Compose update.

   Recommended: (a); measure the first open with the release built both ways (skiko's long-frame log, plan 02 step 2).

Add to `songFilters/CLAUDE.md`: the groups' intrinsics are walked once per pass (cached until the next measure), and
the chips travel (`animateBounds`) from the sheet's second frame.

## Tests

The existing UI tests around the sheet must pass: `screens/songs/SongFilterSearchTest.kt`, and the `dialogs` tests that
open `SongFilters`; `commonTest`'s chip flow and `chipBudgets` tests. Add (UI, `components/songFilters` package of the
tests): `` `a filter chip whose count moves it past another travels there` `` — open the filters, select a tag that
reorders another group's counts, pause mid-animation, the chip is between its two places (guards step 2). For step 1, a
`commonTest` that the cached widths give the same `chipBudgets` as fresh ones for a set of widths.

**Measure:** plan 01's `sheet:open` warm trigger frame: layout part down (the intrinsic and bounds shares, then the list's
rows gone from compose), and the cold column for step 4 only on Windows/Linux release builds (the benchmark's JVM has
no app archive). `sheet:close` unchanged.

## Manual check

Windows: the release's first sheet open of a session, before and after step 4 (a), with skiko's long-frame log on
(plan 02 step 2).
