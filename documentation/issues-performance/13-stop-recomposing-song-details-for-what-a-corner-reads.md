<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# Stop recomposing the whole song details screen for the song texts and the window height, which only a corner of it reads

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`)
`ui/screens/songDetails/SongDetailsScreen.kt`,
`ui/screens/songDetails/SongDetailsPagerEffects.kt` (`LoadSongTexts`),
`ui/screens/songDetails/CurrentSongInfo.kt` (new, if the song info reads move out),
`ui/components/WindowSize.kt` (a `rememberIsShortWindow()` beside `SHORT_WINDOW_HEIGHT`),
`ui/screens/songDetails/CLAUDE.md` only if it describes where these are read

## Problem

`SongDetailsScreen` (`ui/screens/songDetails/SongDetailsScreen.kt`) is one Composable of about 285 lines that builds the
app bar, the keyboard shortcuts modifier, the pager, and many lambdas and holders handed to them. Anything it reads
directly recomposes all of it, and the lambdas it creates make its children recompose too. Two reads are wider than
what uses them:

**The song texts.** At the top:

```kotlin
val songTexts by viewModel.songTextStore.songTexts.collectAsStateWithLifecycle()
```

used only for

```kotlin
val currentSongText = currentSong?.let { songTexts[it.fileName] }
```

(which feeds `hasSongInfo` and `openCurrentSongInfo`), and handed to `SongDetailsEffects(... songTexts = songTexts ...)`,
which only passes it to `LoadSongTexts`, which only reads it inside an effect:

```kotlin
LaunchedEffect(currentPage, songs) {
    listOfNotNull(songs.getOrNull(currentPage - 1), songs.getOrNull(currentPage + 1))
        .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
```

`songTexts` changes once per text read: a push reads the current page and its two neighbours one at a time
(`SongTextStore.readAhead` launches a `loadSongContent` each), so the screen recomposes whole up to three times during
the push slide, and again on every sync run's re-read and every pruning, for texts it does not show.

**The window height.**

```kotlin
val isCompactHeight = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
```

reads the container size in composition, so every frame of a window being resized (and of an Android rotation or a
foldable unfolding) recomposes the whole screen, though only the boolean matters. `SetlistSummary.kt` already does this
right: `derivedStateOf { windowInfo.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT }`.

**Song info in read only mode** (minor): `hasSongInfo` runs `viewModel.songMetadataEditing.hasSongInfo(it, ...)`, i.e.
`ChordProParser.parseMetadata(text)`, in composition. It is already `remember(currentSongText, isReadOnly, ...)`, only
in read only mode (`isReadOnly && ...` short-circuits), and a directive scan rather than a parse, so it costs once per
current text; it is worth moving only together with the `songTexts` read, not on its own.

## Fix

`SongDetailsScreen` is past the 120-line function limit, so the change must leave it no longer than it is (shorter is
welcome); every line it adds is paid for by a line moved out.

1. **Song texts read as a `State`, narrowed by `derivedStateOf`.** Replace the `by` collection with the state object and
   a derived read of the current song's text:

   ```kotlin
   val currentSongText = rememberCurrentSongText(viewModel.songTextStore, currentSong?.fileName)
   ```

   where `rememberCurrentSongText` (in a new small file, or in `CurrentSongInfo.kt` together with step 3) collects
   `songTextStore.songTexts` with `collectAsStateWithLifecycle()` and returns
   `remember(state, fileName) { derivedStateOf { fileName?.let { state.value[it] } } }.value`. The screen then
   recomposes only when the current song's own text changes.
2. **`LoadSongTexts` reads the store itself.** Drop the `songTexts` parameter from `SongDetailsEffects` and
   `LoadSongTexts`; inside the `LaunchedEffect`, read `songTextStore.songTexts.value` at the moment it runs (more current
   than the map captured at composition, and the effect's keys are unchanged). This removes a parameter from both.
3. **Optional: move the song info decision out with it.** `hasSongInfo` and `openCurrentSongInfo` move into one
   `rememberOpenCurrentSongInfo(...)` beside `rememberCurrentSongText`, returning the `(() -> Unit)?` the app bar takes;
   the `parseMetadata` call stays remembered by text there. Moving it into `SongTextStore` beside `inferFor` was
   considered and not recommended: it would add a per-text state to the store for one read only sheet's button.
4. **The short window as a derived boolean.** Add to `ui/components/WindowSize.kt`:

   ```kotlin
   /** Whether the window is shorter than [SHORT_WINDOW_HEIGHT], read so that only a change of the answer recomposes. */
   @Composable
   internal fun rememberIsShortWindow(): Boolean {
       val windowInfo = LocalWindowInfo.current
       return remember(windowInfo) { derivedStateOf { windowInfo.containerDpSize.height < SHORT_WINDOW_HEIGHT } }.value
   }
   ```

   and use it for `isCompactHeight`. The other one-line readers (`ImportPreviewBar`, `ExportScreen`, `TunerScreen`, …)
   may adopt it later; not in this change.

Behaviour is unchanged: the same values reach the same children, only fewer compositions produce them. Nothing here is a
user-caused change, so no animation is involved.

## Tests

None can be written as a commonTest: what changes is how often a Composable recomposes, not a value. `LoadSongTexts`'
filter is unchanged logic. The UI tests (`:tools:screenshots:test`) already open song details and page through a
setlist; they must still pass.

**Measure** (after plan 01): `./gradlew :tools:screenshots:benchmark`, the push to song details in a setlist (the
current page and both neighbours read during the push): the frames after frame 1 should lose the whole-screen
recompositions the text arrivals caused (fewer composition samples in `SongDetailsScreen` during the slide); a window
resize scenario, if plan 01 has one, should show the screen composed only when crossing `SHORT_WINDOW_HEIGHT`.
