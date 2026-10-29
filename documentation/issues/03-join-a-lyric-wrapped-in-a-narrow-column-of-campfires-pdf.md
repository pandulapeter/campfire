<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Join a lyric the export wrapped in a narrow column back into one line on import

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricRows.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayout.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

A lyric too long for its column is wrapped by the export onto the next row (`[A]Headed down south to the [E]land of the
pines` at 12 pt in two portrait columns prints `pines` on a row of its own, with no chord row over it). Imported back,
that row is a new line: the song gains a line break the file never had. Chords and words survive, so nothing is lost
but the line structure, which a re-export and the reader's own layout then keep. The PDF as printed cannot tell the
wrapped row from the next lyric without chords: both sit one row pitch (1.45 times the size) under the line above.

## Fix

Give the wrapped row a mark the import can read and no reader sees. Options: (a) recommended, write the continuation
row's text with a leading U+2060 WORD JOINER in the text layer only (the export already writes U+2060 and no-break
spaces as padding marks, see `PrintPdfText`), and in `ChordSheetCampfireLayout` join a lyric row starting with it to
the line above; (b) indent the continuation row by a fixed amount and join by that, which changes the printed look.

## Tests

- A `PrintLayoutTest` case: a lyric wider than the column prints its continuation row with the mark.
- `PdfRoundTripTest`: print in `PrintSettings(columns = 2)` at the default font size (today it uses 9 pt so nothing
  wraps); corpus song `01-basic-verse-chorus.cho` must keep its first verse line whole.
