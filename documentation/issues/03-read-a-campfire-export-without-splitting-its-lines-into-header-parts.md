<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Read a Campfire export without splitting its first lines into header parts, so a chord row opening with an annotation stays one row

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** amended — the 1–4 columns claim is 1–3 (4 columns loses the annotation for another reason, unchanged by this fix); the round-trip test prints 1 and 2 columns, since 3 already passes at HEAD.  
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

`{title: Song}` / `{start_of_chorus}` / `[*Everybody] [D7]Home, [G]home on the [C]range` / `{end_of_chorus}`, printed
in one column (any language) and imported, comes back as

```
[*Everybody] [D7] [G]
Home, home on the [C]range
```

and the demo Home on the Range's chorus does the same wherever it starts a page. The PDF is right: the chord row is one
line (`Everybody` italic at x 50.5, `D7` 118.0, `G` 158.5, `C` 232.7) over one lyric whose padding puts `H` at 118.0.

Cause (traced with a debug copy of the converter): `convert` runs every page through `ChordSheetHeaderParts.split`
before anything else:

```kotlin
ChordSheetMarginLabels.split(ChordSheetHeaderParts.split(page.lines)).map { placed -> … }
```

which splits any of a page's first five lines at a gap of three ems until it meets a chord row, judged by its text
alone:

```kotlin
if (read > MAX_LINES || ChordSheetConverter.isChordRow(text)) inHeader = false
if (!inHeader || line.spans.any { it.isMonospace }) listOf(PlacedLine(line)) else parts(line)
```

`Everybody D7 G C` is no chord row by its text (the italic note is only told apart by its type, which `isChordRow`
does not see), so the row, the third line of the page, is cut at the 65-point gap between `G` and `C` into
`Everybody D7 G` and a header part `C`; the first is written as a chord row alone and the `C` set into the lyric.

Campfire's export never prints a header in parts (its heading is the title, the artist, and the details row, all set
from one edge, their values three spaces apart), so the split only ever does harm there.

Not this plan: in the same demo, `{chorus: Last chorus, slowing down}` comes back as a verse. That is the documented
label rule (`convert/CLAUDE.md`: a label opens the kind its leading heading word names, "a verse under its own name
otherwise"); the export's chorus indent cannot tell it apart where the recall starts a page (every line on that page is
indented alike), so it is left as is.

## Fix

In `ChordSheetConverter.convert`, skip the header-part split for a Campfire export:

```kotlin
val placedLines = if (sheet.isCampfireExport) page.lines.map { PlacedLine(it) } else ChordSheetHeaderParts.split(page.lines)
ChordSheetMarginLabels.split(placedLines).map { placed -> … }
```

Another producer's PDF and plain text read as now. Checked with a patched copy of the converter: every
`ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest` case passes, the case above and both demo charts come back
whole (`[*Everybody][D7]Home, [G]home on the [C]range`) in 1–3 columns, and nothing else in the corpus changes
(re-checked with a patched worktree over the corpus and both demos in 1–4 columns and all eight label languages: only
improvements). In 4 columns the annotation is lost at HEAD and with this fix alike (`Chorus: [D7]Home, …`, in the demo
too): a different cause, the chord row overflowing the narrow column, not this plan's; leave it to plan 07's overflow
marking or a finding of its own.

Docs: `convert/CLAUDE.md`, the header-parts bullet ("a header line printed in left, centre, and right parts …") gains
"(never in Campfire's own export, which prints none)".

## Tests

- `ChordSheetCampfireLayoutTest`: `a chord row opening with an annotation stays one row over its lyric`, with the
  file's helpers (verified: fails at HEAD with `[*Everybody] [D7] [G]` / `Home, home on the [C]range`, passes with the
  fix):

  ```kotlin
  val chorus = MARGIN + 8.0
  assertEquals(
      "{title: A song}\n\n{start_of_chorus: Chorus}\n[*Everybody][D7]Home, [G]home on the [C]range\n{end_of_chorus}\n",
      convert(
          text("Chorus", x = chorus, size = LABEL, bold = true),
          ChordSheet.Line(
              text("Everybody", x = chorus, italic = true).spans + text("D7", x = chorus + 11 * GLYPH, bold = true).spans +
                  text("G", x = chorus + 17 * GLYPH, bold = true).spans + text("C", x = chorus + 29 * GLYPH, bold = true).spans,
          ),
          text(" " + " ".repeat(10) + "Home, home on the range", x = chorus),
      ),
  )
  ```

  (the `G`–`C` gap is eleven glyphs, over three ems; check the exact trailing newline against `convert`'s output).
- `PdfRoundTripTest`: print the song above in 1 and 2 columns (portrait and landscape) and compare `summary`s, as the
  corpus test does. Fails at HEAD in each (`[Everybody][D7][G] / Home, home on the [C]range`); 3 columns already passes
  at HEAD in English, and 4 columns fails for the other cause above, so neither belongs in this test.
