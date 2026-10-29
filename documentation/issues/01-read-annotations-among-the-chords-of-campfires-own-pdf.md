<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Read the annotations Campfire's PDF prints among a row's chords back as annotations, keeping the row a chord row

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`

## Problem

A lyric line holding a ChordPro annotation (`[*Riff]`, `[*twice]`, `[*x2]`) or a bracket naming no chord (`[x]`) is
printed by the export with the annotation in italics on the chord row. Importing that PDF back, `chordRow` refuses the
row because one of its tokens is neither a chord nor furniture:

```kotlin
if (row.none { rowChord(it.text) } || !row.all { rowChord(it.text) || furniture(it.text) }) return null
```

so the row stays a line of text, and the lyric under it loses every chord. `[G]Sing [C]it [*twice]` comes back as two
lines, `G C twice` and `Sing it`. Only notes in parentheses (`(x)`, `(to v:)`) are taken among a row's chords today.
Anyone who prints a songbook with annotations and imports the PDF elsewhere (or back after losing the library) loses
the chords of every such line.

## Fix

For a sheet flagged `isCampfireExport`, a token of a chord row printed in italics (the export's annotation style,
`PrintRows`) is a note: written `[*text]` where it stands, like the parenthesized notes. `chordRow(text)` only sees
text, so pass the line's italic ranges in: `chordRow(line: Rendered)` knows `line.source.spans` and the rendered
positions; mark each token whose characters are all italic as a note before the `row.all { … }` test. Keep the
text-only path unchanged for other producers. A bold non-chord token (`x`) is the export printing `[x]` as a chord:
for a Campfire export, a bold token on a row that otherwise holds chords may be written back as `[x]` too
(recommended), or left out of scope.

## Tests

- A `ChordSheetCampfireLayoutTest` case: a bold row `G` · `C` with an italic `twice` over `Sing it`, flagged as an
  export, converts to `[G]Sing [C]it [*twice]`.
- In `PdfRoundTripTest`, drop the annotation stripping from `printable` (the `Regex("\\[(\\*[^\\]]*|x)]")`
  replacement and its KDoc sentence): corpus songs `14-odd-chords.cho` and `24-row-marks.cho` must then pass.
