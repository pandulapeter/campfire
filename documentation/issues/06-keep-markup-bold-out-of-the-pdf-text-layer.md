<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep markup's bold out of the PDF's text layer, so a bold lyric word read back from Campfire's export stays a word, not a chord

**Kind:** bug (regression, unreleased)  ·  **Severity:** low  ·  **Platforms:** all (PDF export, then PDF import)
**Challenged:** sound
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDocument.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLyricPieces.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriter.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintPdfWriterTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`

Land after plan 03 (same files). If 03 landed as recommended (one `PrintText` per fragment, `selectableText` writing
every cluster in the text's own style), the code change below is already made: keep only the tests and the doc line.

## Problem

`chordpro/.../convert/CLAUDE.md` promises "It reads back Campfire's own PDF export". Since 14fbf0152 (in no release
tag yet), a bold lyric piece is its own `PrintText` in `PrintStyle(bold = true)`, and the text layer writes it in a bold
font (`PrintPdfWriter.textFont`: `FontStyle(glyph.style.bold, …)`, font name `CampfireTextBold…`, `/FontWeight 700`),
which `data/formats`' `PdfFonts` reads back as `bold`. The converter takes a bold run that is a chord name as an inline
chord (`ChordSheetConverter.inline`, the `styledRuns` loop: `if (!chordLine && chord(span.text.trim()))`), and
`convertSong`'s `styled` check lets such a run make the whole page a chord sheet.

A probe fed the converter the spans the export's text layer produces, under the export's bold title and artist rows:

| Song line | Read back as |
|---|---|
| `There is <b>a</b> house in New Orleans`, song with no chords (or chords hidden) | `There is [a] house in New Orleans` |
| `And it's been the ruin of many <b>a</b> poor boy`, no chord row over it, chords elsewhere | `… many [a] poor boy` |
| `I <b>Am</b> the one` | `I [Am] the one` |
| a fully bold lyric line, under a chord row or not | lyrics, intact |
| `They call the <b>Rising Sun</b>` | lyrics, intact |
| `<b>a</b>` inside a line under a chord row | lyrics, intact (merged with the row) |

So the word is lost from the lyric and becomes a chord whenever a bold word that is also a chord name (`a`, `A`, `Am`,
`E`, `D`, a Latin `Do` / `La` …) stands in a line with no chord row over it. The title and styled-chord-line heuristics
(`isTitle`, `isStyledChordLine`) did not misfire in the probe.

## Fix

Fix the export, not the converter: Campfire sets every chord on a row of its own and in bold, so bold is the export's
own chord signal, and a converter rule loose enough to let bold words through would stop reading other songbooks'
bold inline chords (`reads styled inline chords`). The text layer is invisible and only selected and copied as plain
text, so it loses nothing by carrying markup's bold as regular weight.

Without plan 03: give `PrintStyle` a `val textLayerBold: Boolean = bold` (KDoc: the weight the invisible text is
written in; markup's bold is drawn but written regular, since the importer reads bold as a chord), have
`PrintPdfWriter.textFont` use `glyph.style.textLayerBold` instead of `bold`, and leave `styledPieces` as is: its
`base.copy(bold = …)` keeps the base style's `textLayerBold = false`, while the chord, title, label, and grid styles,
built with `bold = true`, keep writing bold. With plan 03: nothing to change (step 4 there).

`CLAUDE.md` (ui/print), beside the `isSelectable` bullet: "Markup's bold is drawn but written into the text layer in
the regular weight: read back, a bold word that is a chord name would become a chord."

## Tests

- `./gradlew :presentation:desktopTest`: in `PrintLayoutTest`, `There is <b>a</b> house` (a song with no chords)
  produces no text the text layer would write bold (with 03: the single text's style is not bold; without: no text
  with `textLayerBold`), while the title and a chord still do. In `PrintPdfWriterTest`, a `PrintPdfText` in a markup-bold
  style writes a font with no `Bold` in its name and `/FontWeight 400` (skip this one if 03 removed the bold style from
  the text layer's input altogether).
- `./gradlew :chordpro:desktopTest`: in `ChordSheetConverterTest`, the export's read-back pinned as the text layer now
  writes it: a bold 19-pt title, an 11-pt artist, a `Verse` label, and `There is a house in New Orleans` all regular
  converts to the title, artist, and that lyric unchanged. (The cross-module round trip, export → `PdfTextExtractor` →
  converter, cannot be one test: `PrintPdfWriter` is internal to `:presentation`, which does not see `:data:formats`.)
