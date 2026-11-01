<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Read the lyric-type rows a Campfire export sets right under its title, before the heading's gap, as the artist, however many and however long

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the spacing rule decides alone for an export line with a baseline (early return even when no row qualifies), since falling through to the old shorter-and-smaller rule still takes a short first lyric (`Hi there` under a longer title) as the artist; a converter and a round-trip case for it added.  
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

The export prints the artist in the lyrics' type right under the title (`PrintLayout.song`:
`heading += printRows.wrapped(it)`), wrapped onto more rows in a narrow column. The import takes it only in these cases:

```kotlin
val isCampfireArtist = lines.getOrNull(artist)?.isCampfire == true && kinds.getOrNull(artist + 1) == Kind.METADATA
if (named == null && kinds.getOrNull(artist) == Kind.LYRIC && (credit != null || isCampfireArtist || lines[artist].text.length < title.length &&
        lines[artist].source.spans.maxOfOrNull { it.size }?.let { it < (lines[first].source.spans.maxOfOrNull { span -> span.size } ?: 1.0) } == true)) {
    header += "{artist: ${headerValue(credit?.groupValues?.get(1) ?: lines[artist].text.trim())}}"
```

so an artist is lost, and comes back as a paragraph of lyrics, wherever

- it is longer than the title and no details row follows it (a song with no key, capo, tempo, or time):
  `{title: Song}{artist: The Chords}` → `The Chords` as a lyric (finding 04's second half);
- it wraps (4 columns, or a long name): `{title: Wagon Wheel}{artist: Old Crow Medicine Show}` in 4 columns →
  artist null and a paragraph `Old Crow Medicine` / `Show`; the demo Home on the Range and House of the Rising Sun in
  4 columns (with a details row) → `{artist: Traditional}` and a lyric `American`.

Probed with `PdfRoundTripTest`'s pipeline: the heading's rows are 1.45 × the font size apart (title to artist 19.6
points, artist to artist 17.4, at size 12 whose lyric spans read 14.52), while the first block starts a heading gap
(`headingGap = fontSize * 0.8f`) further down (artist to label 28.6, title to the first label 30.8). Measured in the
artist's own span size: 1.2–1.35 within the heading, 1.97 or more to what follows it.

## Fix

In `extractTitle`, for a Campfire export, take the rows after the title's last line that are lyrics and sit less than
1.6 of their own size under the row above (the heading's spacing, short of its gap) as the artist, joined, and let
that rule decide alone wherever the line has a baseline (every PDF line has one):

```kotlin
if (named == null && lines.getOrNull(artist)?.isCampfire == true && lines[artist].source.baseline != null) {
    val rows = generateSequence(artist) { it + 1 }.takeWhile { index ->
        val line = lines.getOrNull(index)
        val below = line?.source?.baseline
        val above = lines[index - 1].source.baseline
        kinds.getOrNull(index) == Kind.LYRIC && below != null && above != null &&
            below - above < (line.source.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.size } ?: 0.0) * HEADING_ROW
    }.toList()
    if (rows.isNotEmpty()) {
        header += "{artist: ${headerValue(rows.joinToString(" ") { lines[it].text.trim() })}}"
        omitted += rows
    }
    return
}
```

with `HEADING_ROW = 1.6` documented beside it (rows 1.45 × the font size apart, about 1.2–1.35 of a span's size; the
gap before the first block adds 0.8 × the font size). The existing rule after it then serves only sheets without
baselines; `isCampfireArtist` can go, since an export line always has one.

The early `return` matters: falling through to the existing rule when no row qualifies keeps a live bug. That rule's
last branch takes any lyric shorter than the title and smaller than it as the artist, so `{title: A rather long title}`
over an unlabelled paragraph opening `Hi there` (no artist, no details row) comes back as `{artist: Hi there}` at HEAD,
in every language and column count (probed), and still does with the plan's first version, since the spacing rule
rightly refuses the row (it stands a heading gap down) and hands it on.
The details row is `Kind.METADATA` and ends the run; a lyric of an unlabelled paragraph right after the title stands a
heading gap down and is never taken (checked: `{title: Song}` / `Hello world, a paragraph` stays a lyric).

With plan 06 (another writer's) landed, join the rows as it joins the title's: no space before a row it marks as a
hard cut (two word joiners), one otherwise. Land 06, 14, and 04 before this.

Checked with a patched copy of the converter: every `ChordSheetConverterTest` and `ChordSheetCampfireLayoutTest` case
passes, and over the corpus and both demos in 1–4 columns and five languages the only changes are the artists above
coming back whole. Re-checked by the challenge with the early return (together with plans 03, 04, 10, 11, and 14, in a
patched worktree, every `values*` folder, 1–4 columns and 2 landscape): the document goldens, `PdfRoundTripTest`, and
every `:chordpro` test pass, and no round trip gets worse.

Docs: `convert/CLAUDE.md`, after "the padding under wide chords is left out": "the lyric-type rows right under the
title, before the heading's gap, are the artist, however many".

## Tests

- `ChordSheetCampfireLayoutTest`, with `at(rows, line)` for baselines (verified: fails at HEAD, passes with the fix):

  ```kotlin
  fun page(vararg lines: ChordSheet.Line) = ChordSheetConverter.convert(ChordSheet(listOf(ChordSheet.Page(lines.toList())), isCampfireExport = true)).single()
  assertTrue("{artist: The Chords}" in page(at(0.0, text("Song", size = 19.0, bold = true)), at(1.35, text("The Chords")),
      at(3.3, text("Verse", size = LABEL, bold = true)), at(4.8, chords("C" to 0, "G" to 6)), at(6.1, text("Hello world"))))
  assertTrue("{artist: Old Crow Medicine Show}" in page(at(0.0, text("Wagon", size = 19.0, bold = true)), at(2.3, text("Wheel", size = 19.0, bold = true)),
      at(3.65, text("Old Crow Medicine")), at(4.85, text("Show")), at(6.8, text("Verse", size = LABEL, bold = true)),
      at(8.3, chords("A" to 0, "E" to 12)), at(9.6, text("Headed down south"))))
  assertFalse("{artist" in page(at(0.0, text("Song", size = 19.0, bold = true)), at(2.6, text("Hello world, a paragraph")), at(3.8, text("And more of it"))))
  // A first lyric shorter than the title, a heading gap down: at HEAD it is taken for the artist.
  assertFalse("{artist" in page(at(0.0, text("A rather long title", size = 19.0, bold = true)), at(2.25, text("Hi there")),
      at(3.45, text("And more of it")), at(4.65, chords("C" to 0)), at(5.95, text("Hello world"))))
  ```

  (the last fails at HEAD and with the fix's first version, verified; passes with the early return).
- `PdfRoundTripTest`: `{title: Wagon Wheel}{artist: Old Crow Medicine Show}` and `{title: Song}{artist: The Chords}`,
  each over a one-line verse, and `{title: A rather long title}` over the unlabelled paragraph `Hi there` /
  `And [C]more of it here`, printed in 1–4 columns; compare `summary`s (which include the artist). Each fails at HEAD.
