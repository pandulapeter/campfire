<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep the PDF's engravings across option steps that do not change the column they are engraved for

**Challenged:** amended — a result is recorded only while the run is still active (a layout cancelled by the next
option step must not leave a tune remembered as failed); two widths kept rather than four, since every width holds a
drawing of every tune of a songbook.

**Kind:** performance  ·  **Severity:** medium (high for songbooks with tunes)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/LaidOutDocumentProducer.kt`,
`…/ui/print/PrintNotations.kt`, a new `…/ui/print/PrintEngravings.kt`, `…/ui/print/CLAUDE.md` (the Layout bullet on
`abc` runs), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintNotationsTest.kt`

Lane P.

## Problem

`produceLaidOutDocument` engraves every tune of the source again before every layout:

```kotlin
val notations = abcRenderer?.let { printNotationsOf(input, inputSettings, it) }.orEmpty()
```

An engraving reads only the tune, its part, the song's spelling and title (all fixed by the source, which is read once
when the screen opens), whether sheet music is shown, and the width
`(printColumnWidth(settings) / printNotationUnit(settings)).toInt()` — paper, orientation, margins, columns, and text
size. Every other option step (diagrams, metadata, key, tempo, overview, scales, …), and every step back to a width seen
before, engraves the whole songbook again: on desktop 1.3–28 ms per tune in the engine plus parse, on one engine thread
(on the web, the main thread).

## Fix

1. A small class `PrintEngravings` (main thread only, like the producer that uses it) keeping, per width, the result of
   each (song file name, `AbcTune`) — the drawing, or that it failed, so a failing tune is not tried again — for the
   last **two** widths (the one laid out and the one before, which is what a step back of the text size, margins, or
   columns returns to; LRU by width), each width's map dropped whole when it ages out. Not more: each width holds a
   parsed drawing of every tune of the source, and a songbook may hold a hundred.
2. `printNotationsOf` takes it and asks it before rendering; the rendering itself is unchanged
   (`renderNotationPartOrScore` with the song's `notationChords.movedBy(tune.offset)` and title). It records each
   tune's result as soon as it has it (so a run cancelled half way by the next option step keeps the tunes it finished,
   and the next run starts from them), but **only after `currentCoroutineContext().ensureActive()`**: the producer's
   block is cancelled on every option change, and a null that arrives from a cancelled run (an engine call interrupted
   rather than failed) must not be remembered as a tune that cannot be rendered, which would print it as text until the
   screen is opened again.
3. `produceLaidOutDocument` remembers one per source and renderer (`remember(source, abcRenderer) { PrintEngravings() }`)
   and hands it in. A new source (the screen opened again, Retry reading it again) starts empty.
4. `print/CLAUDE.md`: in the `abc` run bullet ("…for the column width right before each layout, which waits for
   them…"), add that engravings are kept per width for the export's source, so a step that keeps the width engraves
   nothing.

Must not break: "the preview and the file never show a tune as text that would print as a staff" (a layout still waits
for every engraving its width needs); a width change still engraves for the new width; `showSheetMusic` off still
engraves nothing; the debounce and Retry (`state.attempt`) behaviour.

## Tests

- `PrintNotationsTest` (it already has a `RecordingRenderer`): a call cancelled after its first tune records that tune
  and nothing for the rest (a renderer that suspends on the second); a third width drops the first; two calls with the same settings and one
  `PrintEngravings` render each tune once; a call whose margins change the width renders again; a failed tune is not
  rendered again at the same width; changing only an option the width does not read (diagrams) renders nothing.
