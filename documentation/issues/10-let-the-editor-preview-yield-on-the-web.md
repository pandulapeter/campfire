<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Let the editor's preview give the web page's thread back while it renders

**Challenged:** amended — `prepare` cannot simply become `suspend`: the preview's first rendering and
`rememberSongLyricsModel`'s in-place build run synchronously in composition (the documented first frame), so a
cooperative variant is added beside the synchronous one; the parse in `:chordpro` cannot yield; a measurement gate is
added, since the main-thread composition and text measurement of the new model follow any background build anyway.

**Kind:** performance  ·  **Severity:** medium (web), none elsewhere  ·  **Platforms:** web
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongPreview.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/` (wherever `rememberSongLyricsModel` builds off main),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/CLAUDE.md`, `presentation/src/wasmJsMain/CLAUDE.md`

## Problem

`SongPreview.kt`: `.mapLatest { inputs -> inputs to withContext(Dispatchers.Default) { prepare(inputs) } }`. On wasmJs
`Dispatchers.Default` runs on the page's single thread, and `prepare` (`renderSong` then `prepareSongLyrics`) never suspends,
so `mapLatest` cannot cancel it: after every 150 ms pause in typing a full parse and model build blocks the UI thread, and a
key pressed meanwhile waits. The editor's `CLAUDE.md` ("Parsed and split into sections off the main thread once typing
pauses") does not hold on the web. `rememberSongLyricsModel` likely has the same shape.

## Fix

Gate first: on the web build, time `renderSong` and `prepareSongLyrics` separately for a document near
`LONG_DOCUMENT_LENGTH` (the preview passes no `chordTuning`, so no shape search runs). Drop this plan if the two together
stay under a frame (16 ms): the composition and the line-by-line text measurement of the new model on the main thread,
which no yield can split, then dominate the pause.

Otherwise:

1. **Keep the synchronous path.** `SongPreview` builds its first rendering inside `remember(text) { … prepare(it) }`, and
   `rememberSongLyricsModel` builds a page in place in composition (`prepare(inputs, false)`), both so the first frame is
   never empty (root rule, `songView` and editor `CLAUDE.md`). Neither can call a `suspend` function. Add a cooperative
   `suspend fun prepareSongLyricsCooperatively(...)` (or a `suspend` overload taking the same parameters) that shares
   the per-block step with the synchronous `prepareSongLyrics`, and use it only inside the `withContext(Dispatchers.Default)`
   blocks: `SongPreview`'s `mapLatest`, and `rememberSongLyricsModel`'s `LaunchedEffect` (including
   `withSearchedShapes`, the search being the slow part there).
2. **Where it yields:** once after the parse (`renderSong` is `:chordpro`, which has no coroutines and stays one block),
   every N blocks of `toRenderSections`, before `LayoutBudget.fit`, and before the chord collection / shape search. On
   wasmJs `Dispatchers.Default` re-dispatches through a macrotask, so a `yield()` there lets queued key events and a
   frame run; on the other platforms it also makes the stale build cancellable.
3. Update `rememberSongLyricsModel`'s KDoc ("although not the parse or the search itself, which are not cooperative")
   and the editor `CLAUDE.md`'s "Parsed and split into sections off the main thread" (on the web: on the page's thread,
   yielding between sections).

(b) A longer debounce on the web only stays the fallback if the gate passes but the parse alone is the long part.

## Tests

`runTest`: start the cooperative variant on a long song, cancel it after the first yield, and assert it stopped before
the end (a `CancellationException`, or a count of blocks processed through a test hook); and that it returns a model
equal to the synchronous `prepareSongLyrics` for the same inputs (`RenderSectionsTest` cases).
