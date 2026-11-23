<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep the song-open count's preferences write, the transition report, and whole-object reads at the root from recomposing the app while a screen slides

**Challenged:** amended — the new UI test as worded passed with today's code too (a write on a background dispatcher is not on disk two frames in); it now waits real time with the animation clock paused, and covers an open interrupted by a back. Noted that the flush lands on the push-settle frames plan 05 also moves work to.

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all  ·  **Run on:** any machine
**Files:**
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/spreadTheWord/SpreadTheWordController.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/spreadTheWord/CLAUDE.md` (When: one clause)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt` (must not grow)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireContent.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/MidTransitionBackHandler.kt`
- `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/spreadTheWord/SpreadTheWordTest.kt`

## Problem

**(a) A preferences write in the push.** `SpreadTheWordController.onBackStackChanged` runs from the navigator's
listener as the back stack changes, i.e. in the push, and counts the open at once (`:72`, `:109-114`):

```kotlin
if (top is CampfireDestination.SongDetails && top.importPreview == null && top.id !in songDetailsIds) countSongOpen()
…
updatePreferences("count the song opened") { it.copy(songOpens = it.songOpens.counted(today)) }
```

Until the count is enough (10 opens on 3 days, `spreadTheWord/CLAUDE.md`), every song opened writes the preferences:
JSON and a file write off the main thread, then a new `UserPreferences` emitted mid-slide, which recomposes every
reader of the whole object (34 `userPreferences.collectAsStateWithLifecycle()` sites; on screen during a push:
`CampfireApp`, `CampfireContent`, `CampfireScreens`, `SongList` (`SongList.kt:138`), `SongsScreen`'s actions,
`SongDetailsScreen`, `SongPages`, `SongDetailsPager`).

**(b) The transition report recomposes the root twice per transition.** Every entry reports
(`CampfireScreens.kt:463-471`):

```kotlin
val isRunning = LocalNavAnimatedContentScope.current.transition.isRunning
SideEffect {
    viewModel.navigator.setNavigationTransitionRunning(isRunning)
    onRunningChanged(isRunning)
}
```

`onRunningChanged` writes `CampfireContent`'s `var isNavigationTransitionRunning by remember { mutableStateOf(false) }`,
which `CampfireContent` reads while composing to pass a `Boolean` down (`CampfireContent.kt:150`,
`isNavigationTransitionRunning = isNavigationTransitionRunning`), and `CampfireScreens` hands it to
`MidTransitionBackHandler(isTransitionRunning = isNavigationTransitionRunning, …)` (`:397-401`). So the frame after a
transition starts and the frame after it ends each recompose the scaffold's content and all of `CampfireScreens`' body
(it cannot skip: a parameter changed).

**(c) Whole objects read at the root.** `CampfireApp.kt:137`: `val userPreferences by
viewModel.userPreferences.collectAsStateWithLifecycle()` recomposes `CampfireApp`'s body on any preference write, to
read `language`, `tagColors`, `areTooltipsEnabled`, `instrument`, `uiMode`, `themeColor`, `backgroundWarmth`.
`CampfireContent.kt:73-80` reads `visibleDialog` whole for `visibleDialog is DialogType.Export`, so opening any sheet
recomposes `CampfireContent` and `CampfireScreens` (the probe's `sheet:open` compose samples: `CampfireContent` 13 %,
`CampfireScreens` 11 % inclusive), and `metronomeSettings` whole for `isVisualBeatEnabled`.

## Fix

**(a)** Count at the end of the push's transition, not in its first frame. In `SpreadTheWordController`:
`onBackStackChanged` only notes a pending count (`pendingSongOpens++` where it counts today); `onTransitionEnded`
(already called by `Navigator.setNavigationTransitionRunning` when any transition ends) first flushes the pending count
(`countSongOpen()` once per pending open, then zero), then does what it does now. Restructure its early return so the
flush happens on every transition end, not only a return from a song. A push with no transition (behind the launch
screen, a restored stack) is flushed by the next transition's end; the count is a nudge, and an open lost to a process
end mid-slide does not matter. Options considered: counting on the pop (misses an app closed on a song), or debouncing
the write in the repository (hides the cause). Recommended: on transition end.

Check the rules still hold (`spreadTheWord/CLAUDE.md`): each push of a non-preview `SongDetails` counts once, the
editor closing over one and a rename (same id) still do not (the id check stays in `onBackStackChanged`), and the
request still waits for the pop's own end (the flush comes first, so the pop that ends the tenth open sees it counted —
read `songOpens` after the flush from the updated value, not the stale `userPreferencesState.value`, or apply the
pending count to the copy `isSpreadTheWordDue` is asked with). Add to the package `CLAUDE.md`'s When: "an open is
counted once its push has settled, so the write never lands mid-slide".

The flush, and the `UserPreferences` it emits, land on the frames right after the push settles, which is where plan
05 moves the background pages' models to: measure `push:*`'s first settled frames with both landed, and if they
stack into a long frame, delay the flush by a frame or two there (still never mid-slide).

**(b)** Pass state, not a value: `CampfireContent` hands `CampfireScreens` `isNavigationTransitionRunning = { … }`
(or the `State` itself), and `MidTransitionBackHandler` takes `isTransitionRunning: () -> Boolean`, read inside a small
child that only it recomposes. Its `isBackEnabled` is a composition-time value of `NavigationBackHandler`, so the read
stays in composition, but in that child alone. `CampfireContent`'s own read is already inside `snapshotFlow` in an effect.

**(c)** At the root, read derived fields: `CampfireApp` keeps `val userPreferences =
viewModel.userPreferences.collectAsStateWithLifecycle()` as a `State` and derives what it uses
(`remember { derivedStateOf { prefs.value?.language } }` and so on, or one small `@Immutable` `RootPreferences` data
class derived once, so a songs-open count or a transposition write changes nothing there). `CampfireContent`:
`val isExportDialogVisible by remember { derivedStateOf { visibleDialog.value is DialogType.Export } }` from the
`State`, and `isVisualBeatEnabled` derived the same way (or a `map { it.isVisualBeatEnabled }.distinctUntilChanged()`
flow on the controller). `CampfireContent` already does this for `isCoverArtEnabled` and `isKeyboard` (`:87-89`);
follow that pattern.

## Tests

`spreadTheWord/SpreadTheWordTest.kt` must pass as is (the request still appears once on the way back after the tenth
open on the third day). Add there: `` `a song opened is counted only once its slide has ended` `` — with the clock
paused, open a song, advance two frames, then let real time pass with the animation clock still paused (wait until the
app's IO is idle, or poll the file for a second): `preferences.json` on disk still holds the old `songOpens`, which
fails with today's code, whose write lands during that wait; let the slide settle: it holds one more (what lands on
disk, per `tools/screenshots/CLAUDE.md`). Also: open a song and go back before its slide ends (one interrupted
transition, one end): the open is counted once. `chrome/PredictiveBackTest.kt`
covers (b)'s back mid-transition and must pass. `desktopTest` for the rest.

**Measure:** plan 01's `push:*`: the frames ~2 and the first frame after the slide ends lose a root recomposition
(compose part of those frames down; check the per-frame CSV); with a fresh library (`songOpens` under the threshold,
which the committed library's preferences are) the mid-slide frame where the write landed disappears. `sheet:open`:
the compose part drops by the `CampfireContent` / `CampfireScreens` share.

## Manual check

None: the rules are covered by the UI test, and nothing here differs by platform.
