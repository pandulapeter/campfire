<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Let the editor's preview give the web page's thread back while it renders

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

(a) Make the preparation cooperative on every platform, yielding (`yield()`) between sections the way the print layout does
("after each song and every 50 groups"), so a newer input cancels it; or (b) a longer debounce on the web only. Recommended:
(a), which also makes cancellation work on native and needs no platform switch.

## Tests

With `prepare` made `suspend`, a `runTest` test can start it on a long song, cancel it, and assert it stopped before the end
(a `CancellationException`, or a count of the sections built through a test hook).
