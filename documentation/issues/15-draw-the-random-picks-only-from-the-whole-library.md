<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Draw the random picks only from the whole library, and keep the launch screen up until it is read while they would show

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the hold is not taken with Songs switched off or the songs list not on top (a restored
process on another screen, a share pushed during the read), and preferences not read yet count as awaiting, so a first
batch landing before them cannot latch the launch screen away early; tests extended. Confirmed it cannot hang: a failed
read ends `Loading`.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/SongListState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongGroups.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/SongListStateTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ListPlaceholdersTest.kt` (its
`groups()` helper builds `SongGroups`), a new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchStateTest.kt`

## Problem

On a cold launch of a library of more than 64 songs (with the random picks on, the default), the five picks at the top
of the songs list are replaced several times in front of the user, without animation.

The first read of the library publishes partial lists. `SongLocalSourceImpl.loadSongs` hands the list on after the first
batch of 64 and each time it has doubled:

```kotlin
if (index < batches.lastIndex && songs.size >= publishedCount * 2) {
    publishedCount = songs.size
    onProgress(songs.toList())
}
```

`BaseLocalDataRepository.publishPartialData` emits each as `DataState.Loading(data)`, only on a first read
(`isPublishingPartialData = previousData == null`; a rescan keeps the old list on screen). `LibraryState.indexedSongs`
carries them with `isLibraryRead = false`, and `SongListState.songGroups` draws the picks from whatever is there,
regardless of `indexed.isLibraryRead`:

```kotlin
randomPicksSeed = seed.takeIf { randomPicksPreferences.isEnabled },
```

```kotlin
val picks = randomPicksSeed?.let { seed -> randomPicks(songs = sections.flatMap { it.songs }, seed = seed) }.orEmpty()
```

`randomPicks` takes the five songs of lowest `pickRank` among the songs given, so each larger batch almost surely has
five other winners (a 2,002-song library publishes at 64, 128, 256, 512, 1,024, and 2,002 songs: up to five different
sets of picks). Meanwhile the launch screen has already gone at the first batch, by design:

```kotlin
(songGroups.isLibraryRead || songGroups.groups.isNotEmpty()) &&
```

(`LaunchState.hasLibraryToShow`), and the list does not animate items while `!hasLoadedLibrary`
(`SongsScreen`: `rememberHasLoadedLibrary(isLoading = !songGroups.isLibraryRead)`), so the cards at the very top swap
abruptly while the reader looks at them. That breaks the root rule "Data arriving or a wrong initial value is fixed at
the source so the first frame is right, never animated over". The live run of this review measured 2,002 songs ready in
428 ms warm and about 1.6 s on a cold JVM on desktop, so the swapping lasts that long; on the web (OPFS) and on slower
phones it lasts longer.

## Fix

The picks are a draw over the whole list, so they cannot be right before the whole list is known; the choice is only
what the user sees until then.

- **(A, recommended) No picks from a partial read, and the launch screen held until the read ends while picks would
  show.** In `SongListState.songGroups`, pass `randomPicksSeed = seed.takeIf { randomPicksPreferences.isEnabled &&
  indexed.isLibraryRead }`, and carry a new `SongGroups.areRandomPicksAwaited` (true when the picks are switched on, the
  read is not over, and the normalized query is empty; a search restored open shows no picks, so it waits for nothing).
  In `LaunchState.hasLibraryToShow`, replace the songs condition with
  `songGroups.isLibraryRead || (songGroups.groups.isNotEmpty() && !songGroups.areRandomPicksAwaited)`.
  The first frame the user sees then has the final picks, at the cost of the launch screen staying up for the rest of
  the first read instead of only its first batch: nothing for a library of up to 64 songs (one batch, never published
  partially), a few hundred ms for 2,000 songs warm on desktop, about 1.5 s on a cold JVM, more on the web. This is not
  a new wait gate: it is the existing cover, waiting for the one value that cannot be right earlier, and a launch on a
  web address already waits for the whole read (`navigateOnLaunch` waits for `screenData` to leave `Loading`). The
  partial publishing keeps its purpose with the picks switched off.

  Where the hold buys nothing, it is not taken:
  - **Songs switched off** (Settings → Features): the picks preference stays on but no list shows them, and the root
    is another screen. `areRandomPicksAwaited` also needs `areSongsEnabled`; carry it in `RandomPicksPreferences`
    (the class exists so other preference changes do not rebuild the list, and this one changes what it shows).
  - **Preferences not read yet** (`userPreferences` null): `RandomPicksPreferences.of(null)` says the picks are off,
    which would let a first batch landing before the preferences latch `hasLibraryToShow` early, after which the
    picks group arrives at the end of the read and pushes the list down (option B's harm). Count unknown preferences
    as awaiting: `areRandomPicksAwaited = (preferences == null || preferences.isRandomPicksEnabled &&
    preferences.areSongsEnabled) && !indexed.isLibraryRead && normalizedQuery.isEmpty()`. It cannot hang: a read that
    fails ends `Loading` (`LibraryState.LoadingLatch`: `DataState.Error` is not loading), so `isLibraryRead` turns true
    on every outcome, and the launch screen waits for the preferences anyway.
  - **The songs list not on top** (an Android process restored on a song or another tab, `CampfireDestination.Songs`
    not last on the back stack): in `LaunchState.hasLibraryToShow`, read the hold as
    `songGroups.areRandomPicksAwaited && isSongsOnTop`, with `isSongsOnTop` a further input of the inner `combine`,
    `snapshotFlow { backStack.lastOrNull() == CampfireDestination.Songs }` (the same test `CampfireApp`'s `ON_START`
    makes; `Navigator.backStack` is a `SnapshotStateList`, and `LaunchState` already holds it; `snapshotFlow` rather
    than a plain read so that a screen pushed over the list while the read runs, a share or "Open with", releases the
    hold at once instead of at the next batch). A web address is held by `navigateOnLaunch` as before.

  A sync or rescan during the first read cannot add a hold: a rescan keeps the old list (`isPublishingPartialData` is
  only for a first read), and the hold is latched with `hasLibraryToShow`. A library shrinking to 20 songs or fewer
  later only removes the picks group, as today.
- (B) No picks from a partial read, launch screen unchanged: the picks group then lands above the first section once
  the read ends, pushing the whole list down under the reader (or slides in, if animated): data arriving, narrated or
  not. Rejected by `no-incidental-animations`.
- (C) Freeze the first batch's picks until the read ends, then draw from the whole library: one swap instead of five,
  still on screen. Keeping them for good instead would draw from the first 64 files of the listing, which are not a
  random sample (the listing is usually in name order, so `a…` artists).
- (D) Rank the picks from the file listing, before parsing (the data layer knows every name up front): couples the
  data layer to the picks, and is still wrong under a filter restored after process death, which needs parsed tags.

Write (A). Update the `hasLibraryToShow` KDoc ("Neither list holds a slow read back by more than its first batch" gets
"unless the random picks are to lead the songs list"), `ui/launch/CLAUDE.md` (the launch screen also waits for the
whole read while random picks would show), and the Random picks section of `ui/screens/songs/CLAUDE.md` (picks are
drawn only from a library read whole). Add `areRandomPicksAwaited` to `SongGroups`' KDoc and its every constructor
call (`SongListState`'s initial value, `ListPlaceholdersTest.groups`).

Drop this plan if the user prefers a fast launch over the swap (then write C as the smaller harm).

## Tests

- `SongListStateTest`: a `songListState()` fed an `IndexedSongs` of 30 songs with `isLibraryRead = false` and the picks
  on gives no `RandomPicks` group and `areRandomPicksAwaited = true`; the same songs with `isLibraryRead = true` lead
  with the picks and `areRandomPicksAwaited = false`; with the picks off, Songs switched off, or a query,
  `areRandomPicksAwaited` is false; with the preferences still null it is true.
  (The helper needs `indexedSongs` and `userPreferences` parameters; today it hardcodes both.) Fails before the fix:
  the partial read leads with picks, and the property does not exist.
- New `LaunchStateTest` (commonTest, `runTest`, flows as `MutableStateFlow`s, the other gates false, `backStack` a
  `mutableStateListOf(CampfireDestination.Songs)`): with `songGroups` holding songs, `isLibraryRead = false`, and
  `areRandomPicksAwaited = true`, `hasLibraryToShow` stays false; it turns true once `isLibraryRead` does; with
  `areRandomPicksAwaited = false` it is true at the first batch; with a song details destination pushed on the back
  stack (the snapshot applied with `Snapshot.sendApplyNotifications()`) it turns true without another batch.
