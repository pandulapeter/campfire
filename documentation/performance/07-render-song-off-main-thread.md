<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 07 — Parse, transpose and build the sections off the main thread (details page and editor preview)

| | |
|---|---|
| Lane | A |
| Impact | medium in the editor's preview (it runs on every typing pause); low–medium on the details screen (once per transposition step, and when a page's text arrives) |
| Confidence | medium |
| Platforms | Android, iOS, desktop. On the web `Dispatchers.Default` is the same single thread, so there it is correct but no faster. |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | Changes `SongLyrics`'s `song` parameter into a prepared model, which conflicts with every other `SongLyrics` plan. Land after 04, 05 and 08. 06 then edits the pager around the new call. Order: … 05 → 08 → **07** → 06 → 09 → 10. |
| Commit message | `Keep transposing a song and typing in the editor from stalling the interface.` |

## Problem
Both callers of `SongLyrics` parse the whole song synchronously during composition, on the main thread.

- **Details page** (`SongDetailsScreen.kt:599`):
  ```kotlin
  val renderedSong = remember(songText, transposition, chordSpelling) { renderSong(songText, transposition, chordSpelling) }
  ```
  Each transposition step, each spelling change, and each page whose text arrives runs `parseChordPro` + `transposeChordPro` + `convertChordProNotation` (`CampfireViewModel.kt:1516-1523`) inside the composition of that frame.
- **Editor preview** (`SongEditorScreen.kt:678-685`):
  ```kotlin
  var previewedText by remember(text) { mutableStateOf(text.value) }
  LaunchedEffect(text) {
      snapshotFlow { text.value }.distinctUntilChanged().debounce(PREVIEW_DELAY_MILLIS).collect { previewedText = it }
  }
  val song = remember(previewedText, transposition, chordSpelling) { viewModel.renderSong(previewedText, transposition, chordSpelling) }
  ```
  This runs on every 150 ms pause in typing, in the frame right after the pause. That is exactly when the user is likely to type the next key.
- In both cases, `SongLyrics.kt:177` then runs `song.toRenderSections(...)` in the same composition (joining cut sections, `prepareForDisplay`, `groupIntoRuns` for every part).

All of this work is linear, and the `:chordpro` regexes are precompiled. It is still a whole-document pass with a lot of allocation, and it lands on the frame the user is waiting on. Layout still has to happen on the main thread afterwards, but it no longer has the parse in front of it.

## Fix
1. **`SongLyrics.kt`.** Move the section building out of `SongLyrics` into a model that can be built on any thread:
   ```kotlin
   /** A song ready to be laid out: what [SongLyrics] draws, built away from the main thread. */
   internal class SongLyricsModel internal constructor(val song: ChordProSong, internal val sections: List<RenderSection>)

   internal fun prepareSongLyrics(song: ChordProSong, shouldShowChords: Boolean, labels: DefaultSectionLabels) =
       SongLyricsModel(song, song.toRenderSections(shouldShowChords, labels))

   /** The labels a model is built with, read here since they are string resources. */
   @Composable internal fun rememberDefaultSectionLabels(): DefaultSectionLabels = DefaultSectionLabels(…)   // moved from SongLyrics.kt:171-176
   ```
   - `RenderSection`, `SectionPart` and `DefaultSectionLabels` go from `private` to `internal`.
   - `SongLyrics` takes `model: SongLyricsModel` instead of `song` and `shouldShowChords`. Its `sections` becomes `model.sections` and the header uses `model.song`. The labels are still needed there for the fold toggles (`defaultLabels.labelOf`), so read them with `rememberDefaultSectionLabels()`.
2. **Shared helper** in the same file. The first result is computed synchronously, so a page never opens on an empty frame (see the "no incidental animations" rule: eager state rather than a gate). Every later change is computed on `Dispatchers.Default`, cancelling the previous one, and the previous model stays on screen until the new one is ready:
   ```kotlin
   @Composable
   internal fun rememberSongLyricsModel(inputs: SongLyricsInputs, prepare: (SongLyricsInputs) -> SongLyricsModel): SongLyricsModel {
       val latestPrepare by rememberUpdatedState(prepare)
       val state = remember { mutableStateOf(inputs to prepare(inputs)) }
       LaunchedEffect(inputs) {
           if (state.value.first != inputs) state.value = inputs to withContext(Dispatchers.Default) { latestPrepare(inputs) }
       }
       return state.value.second
   }
   internal data class SongLyricsInputs(val text: String, val transposition: Int, val spelling: ChordSpelling, val shouldShowChords: Boolean, val labels: DefaultSectionLabels)
   ```
   The restart of `LaunchedEffect` gives `mapLatest` semantics. The parse itself is not cooperative, so a superseded run finishes on the background thread and its result is dropped.
3. **Details page** (`SongDetailsScreen.kt:599`):
   ```kotlin
   val labels = rememberDefaultSectionLabels()
   val model = rememberSongLyricsModel(SongLyricsInputs(songText, transposition, chordSpelling, shouldShowChords, labels)) {
       prepareSongLyrics(renderSong(it.text, it.transposition, it.spelling), it.shouldShowChords, it.labels)
   }
   if (model.song.blocks.isEmpty()) { … empty state … }
   ```
4. **Editor preview** (`SongEditorScreen.kt:678-685`): fold the debounce and the render into one flow on `Dispatchers.Default`. Keep the synchronous first render the same way:
   ```kotlin
   val labels = rememberDefaultSectionLabels()
   val latestTransposition by rememberUpdatedState(transposition)
   val latestSpelling by rememberUpdatedState(chordSpelling)
   var model by remember(text) { mutableStateOf(prepare(text.value, transposition, chordSpelling, labels)) }
   LaunchedEffect(text, labels) {
       snapshotFlow { Triple(text.value, latestTransposition, latestSpelling) }
           .distinctUntilChanged()
           .debounce(PREVIEW_DELAY_MILLIS)
           .mapLatest { (t, tr, sp) -> withContext(Dispatchers.Default) { prepare(t, tr, sp, labels) } }
           .collect { model = it }
   }
   ```
   `prepare` calls `viewModel.renderSong(...)` and then `prepareSongLyrics(…, shouldShowChords = true, …)`. Before relying on this, check that the three use cases behind `renderSong` (`ParseChordProUseCase`, the transpose one and the notation one) and their `:chordpro` objects keep no shared mutable state. A read of `ChordProParser`, `ChordProTransposer` and `ChordProNotation` found only locals and precompiled `Regex`es.
   The debounce now also covers a transposition or spelling change. That is fine: before, they applied immediately, and now they apply 150 ms later.
5. **What must not change:**
   - The first frame of a page and of the preview is the rendered song, never an empty frame.
   - Lyrics-only mode still never reaches the preview.
   - `renderSong`'s own contract ("call it from a `remember` keyed on all three"): update its KDoc to say it may run on a background thread.

## Verification
- Run `./gradlew :presentation:desktopTest :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.
- Android, a long song. Tap transpose + repeatedly while recording a Perfetto trace. The `renderSong` and parse slices move off the `main` thread onto a `DefaultDispatcher` worker.
- Editor Split on a tablet or desktop with a long song. Type steadily. The frame after each pause no longer contains the parse, and the preview never goes blank or flashes.
- Web: the preview and the transposition still work, since `Dispatchers.Default` is the same thread there.
- `presentation/CLAUDE.md`: in the `SongLyrics.kt` and `screens/songEditor/` entries, note that the song is parsed and its sections are built off the main thread, with the previous rendering kept until the new one is ready.
