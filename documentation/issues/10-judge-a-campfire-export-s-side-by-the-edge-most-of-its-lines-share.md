<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Judge which side a Campfire export's song is set from by the edge most of its lines share, not by its outermost line

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the round-trip test must print the Italian section labels too (`printLabelsOf` keeps English `sections`, and the Truck Driver case passes at HEAD without `Ritornello`); the Hebrew half verified as written.  
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayout.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PdfRoundTripTest.kt`

## Problem

Two failures, one cause (traced with a debug copy of `ChordSheetCampfireLayout`):

1. Finding 10. `{title: Truck Driver Change}{key: C}` over two choruses (`[C]Love is [F]all a[G]round`,
   `[D]Love is [G]all a[A]round`), printed in Italian, comes back with `{comment_box: Tonalità: C}` and no key.
2. Finding 09 (c). Corpus `17-hebrew-mixed.cho` in Romanian, 4 columns: the first lyric `שלום Hello world` comes back
   as `{comment_box: …}`, its chords `[C] [G]` on a line of their own.

`isFlushRight` takes the column's edges from its outermost plain lines:

```kotlin
val left = column.minOf { it.second }
val right = column.maxOf { it.third }
val flushLeft = column.count { it.second - left <= TOLERANCE }
val flushRight = column.count { right - it.third <= TOLERANCE }
return if (flushRight != flushLeft) flushRight > flushLeft else { /* the letters' vote */ }
```

1. In a song of nothing but choruses, every lyric is indented 8 points behind the chorus bar; the details row alone
   starts at the margin. `left` is the details row's start, which only it shares, while the two equally long chorus
   lyrics share `right`: 2 > 1, the left-to-right song is judged flush right, and the details row is then tested as a
   box by its right edge, which in Italian happens to end 4.57 points left of the `Ritornello` labels' (5 ± 0.75 is a
   box). One chorus, or a verse first, ties or tips the count the other way.
2. In the right-to-left song, the Romanian details row `Tonalitate: G`, left-to-right text set from the right, ends
   0.97 points past the column's edge (the drift of its glyph positions grows with its length; English `Key: G` ends
   0.2 past). `right` is that row's end, so only it is flush right (1), against one line flush left: a tie, which this
   song's letters (32 Latin, 32 Hebrew) give to left-to-right, and the first lyric, 5.7 points in from the chord row
   under it, is a box by its start.

## Fix

Take each edge as the one most of the column's lines share, and count the lines within the tolerance on either side
of it:

```kotlin
// The edge the most lines share: a line of left-to-right text set from the right may end past it, and a song of
// choruses has a details row alone at the margin.
fun shared(edges: List<Double>) = edges.maxBy { edge -> edges.count { abs(it - edge) <= TOLERANCE } }
val left = shared(column.map { it.second })
val right = shared(column.map { it.third })
val flushLeft = column.count { abs(it.second - left) <= TOLERANCE }
val flushRight = column.count { abs(right - it.third) <= TOLERANCE }
```

(`import kotlin.math.abs`). Applies only to Campfire's export (`roles` is called for nothing else). Checked with a
patched copy (together with plans 03, 04, 05, 09, 11, and 14): every `ChordSheetCampfireLayoutTest` and
`ChordSheetConverterTest` case passes; over the corpus and both demos in 1–4 columns and five languages the changes
are these two, `21-modulation` in Italian (the same comment box, now `{key: C}`), and only improvements elsewhere.

Docs: no `CLAUDE.md` line names this rule; update the `isFlushRight` KDoc ("more of its plain lines end at the edge most
of them share than start at the one most of them share").

## Tests

`ChordSheetCampfireLayoutTest` (both verified: fail at HEAD, pass with the fix):

- `a song of choruses is read from its left, its details row no box`:

  ```kotlin
  val chorus = MARGIN + 8.0
  val song = convert(
      text("Tonalità: C", size = LABEL, width = (chorus + 10 * GLYPH - 5.0 - MARGIN) / 11),
      text("Ritornello", x = chorus, size = LABEL, bold = true),
      ChordSheet.Line(text("C", x = chorus, bold = true).spans + text("F", x = chorus + 8 * GLYPH, bold = true).spans),
      text("Love is all around", x = chorus),
      text("Ritornello", x = chorus, size = LABEL, bold = true),
      ChordSheet.Line(text("D", x = chorus, bold = true).spans + text("G", x = chorus + 8 * GLYPH, bold = true).spans),
      text("Love is all around", x = chorus),
  )
  assertTrue("{key: C}" in song)
  assertFalse("comment_box" in song)
  ```
- `a details row ending past a right-to-left column's edge leaves its lyrics unboxed`:

  ```kotlin
  val lines = listOf(
      text("Tonalitate: G", x = RIGHT + 0.97 - 13 * GLYPH, size = LABEL),
      ChordSheet.Line(text("C", x = RIGHT - 16 * GLYPH - 5.0, bold = true).spans + text("G", x = RIGHT - GLYPH, bold = true).spans),
      ChordSheet.Line(listOf(ChordSheet.Span("שלום Hello world", RIGHT - 16 * GLYPH, RIGHT, BODY))),
      ChordSheet.Line(listOf(ChordSheet.Span("Rock and roll שרים", RIGHT - 18 * GLYPH, RIGHT, BODY))),
  )
  assertEquals(listOf(null, null, null, null), ChordSheetCampfireLayout.roles(lines, listOf(false, true, false, false), BODY))
  ```

`PdfRoundTripTest`: the Truck Driver Change song above printed with the Italian labels, and `17-hebrew-mixed.cho` with
the Romanian ones in 4 columns; compare `summary`s. `printLabelsOf` localizes only the details row: its `sections` stay
the English `DefaultSectionLabels`, and with `Chorus` printed instead of `Ritornello` the Truck Driver case passes at
HEAD in every column count (probed). So the Italian case must also print the section labels of `values-it` (extend
`printLabelsOf` to read the section strings into `sections`, or copy it with `chorus = "Ritornello"`) and be checked to
fail at HEAD before the fix lands. The Hebrew case fails at HEAD as `printLabelsOf` stands (probed: `Verse: [C][G], …`
in Romanian, 4 columns) and passes with the fix.
