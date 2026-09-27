<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 18 — Build the library-sized derived states off the main thread

| | |
|---|---|
| Lane | C |
| Impact | medium (first load and every library edit); low per keystroke |
| Confidence | high |
| Platforms | all (most visible on Wasm and Kotlin/Native, whose string handling is slower than ART's) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndex.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/SearchIndexTest.kt |
| Depends on / conflicts with | Any other plan that edits `CampfireViewModel.kt` near lines 550–750 or `SearchIndex.kt` (check the app-shell lane's plans). |
| Commit message | `Build the searched, grouped and labelled song lists off the main thread.` |

## Problem
`asState` is `distinctUntilChanged().stateIn(viewModelScope, Eagerly, ...)` (`CampfireViewModel.kt:2290-2294`). `viewModelScope` runs on `Dispatchers.Main.immediate`, and none of the flows below has a `flowOn`. So all of their transforms run on the main thread.

That undoes what the domain layer does on purpose. `GetScreenDataUseCaseImpl` builds its whole-library pass with `.flowOn(Dispatchers.Default)` because a large library would otherwise block frames.

The flows that do library-sized work, for a 2,000-song library:

- **`indexedSongs`** (`:603-611`): `songSearchIndex.update(input.all, input.filtered)`.
  - On first load this normalizes title, artist and every tag of every song, about 8,000 `normalizeSearchText` calls. The accent lookup is a chain of `this in "…"` scans (`Accents.kt:31`), so text with many accents costs more.
  - On every later library emission it rebuilds two 2,000-entry `LinkedHashMap`s and the filtered list. That includes each partial batch of the first scan and every single-song edit, such as a tag added from a row.
- **`SongSearchIndex.index`** (`SearchIndex.kt:67-80`): does `previous.copy(song = song)` even when `previous.song === song`. So a library emission whose songs are mostly the same instances still allocates 2,000 new `SearchableSong`s. The filtered pass (`:58-61`) already has this identity shortcut; the all-songs loop (`:52-56`) does not.
- **`labelsOnEverySong`** (`:554-559`):
  ```kotlin
  tags = songs.map { song -> song.tags.map { it.lowercase() }.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty(),
  languages = songs.map { it.languages.toSet() }.reduceOrNull { a, b -> a intersect b }.orEmpty(),
  ```
  This builds 2,000 sets up front, plus the lowercase strings, on every library emission. In practice the intersection is empty after two songs.
- **`songGroups`** (`:636-644`): `rankSongs` over every song plus `normalizeSearchText(query)` on every keystroke. That is about 10,000 `contains` calls, roughly 0.3–1 ms on ART and more on Wasm and Kotlin/Native.
- **`visibleSetlists`** (`:703-716`): maps every entry of every setlist against the index on every library or setlist emission.
- **`setlistsWithSongs`** (`:725-732`): normalizes every setlist's title and description on every keystroke of the setlists search (`matchesSearch`, `:2303-2306`).

Two flows were checked and are fine on main:

- `librarySummary` (`:621-632`) is two integer `sumOf` passes, which is negligible.
- `demoLibraryOffer` (`:666-671`) builds a set of every file name in `DemoLibrary.isPresentIn` (`DemoLibrary.kt:45-48`) on each `screenData` emission, well under a millisecond. It can move off main in the same way if wanted, but that is not required.

## Fix
1. In `SearchIndex.kt`, give `index()` the same identity fast path the filtered pass has:
   ```kotlin
   private fun index(song: Song, previous: SearchableSong?): SearchableSong = when {
       previous?.song === song -> previous
       previous != null && previous.song.title == song.title && previous.song.artist == song.artist && previous.song.tags == song.tags -> previous.copy(song = song)
       else -> SearchableSong(...)
   }
   ```
2. In `CampfireViewModel.kt`, add `.flowOn(Dispatchers.Default)` (import `kotlinx.coroutines.Dispatchers`, which is available in common code) just before `.asState(...)` on:
   - `indexedSongs`, after the `.map { IndexedSongs(...) }`;
   - `labelsOnEverySong`;
   - `songGroups`;
   - `visibleSetlists`;
   - `setlistsWithSongs`.

   Each of these is safe to move:
   - `SongSearchIndex.previous` is only touched by the one sequential collector of `indexedSongs`.
   - `normalizeSearchText` is pure.
   - The `snapshotFlow` inside `SearchState.activeQuery` can be collected off the main thread, since snapshot reads are thread-safe.
   - `ScrollToTopWhenChanged` already expects the contents to arrive frames after the key (see its KDoc).
3. Rewrite `labelsOnEverySong` to fold one song at a time and stop once the result is empty:
   ```kotlin
   val labelsOnEverySong = allSongs.map { songs ->
       var tags: Set<String>? = null
       var languages: Set<String>? = null
       for (song in songs) {
           if (tags?.isEmpty() != true) song.tags.mapTo(HashSet()) { it.lowercase() }.let { tags = tags?.intersect(it) ?: it }
           if (languages?.isEmpty() != true) song.languages.toSet().let { languages = languages?.intersect(it) ?: it }
           if (tags?.isEmpty() == true && languages?.isEmpty() == true) break
       }
       LabelsOnEverySong(tags = tags.orEmpty(), languages = languages.orEmpty())
   }.flowOn(Dispatchers.Default).asState(LabelsOnEverySong())
   ```
4. Update the `asState` KDoc, which currently only discusses eagerness, and the `indexedSongs` KDoc with one line each saying the work runs on `Dispatchers.Default`.

What must not change:

- The `initialValue`s.
- The eager sharing, and the `distinctUntilChanged` in `asState`.
- Every result, which must be the same as now. Only the thread moves.

## Verification
- Run `./gradlew :presentation:desktopTest`. In `SearchIndexTest.songFieldsAreNormalizedOnceAndLatestModelsReplaceCachedOnes`, add this assertion: an `update` with the very same `Song` instances returns `assertSame` `SearchableSong`s, with no new normalize calls.
- Also run `:app:android:assembleDebug`, `:app:web:wasmJsBrowserDevelopmentRun` and `:app:ios:linkDebugFrameworkIosSimulatorArm64`.
- Manual checks:
  - Import a large library (for example 2,000 generated `.cho` files in a zip) on the web build.
  - The launch-to-list time and the frame drops during the first scan should improve; the Chrome performance panel shows the main thread's long tasks.
  - Typing in the songs search must still rank results as before.
  - Tag labels that every song carries must still be hidden.
