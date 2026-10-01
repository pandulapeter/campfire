# Lay out a chord or annotation wider than the column without blank rows or runaway padding

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

## Problem

Two things go wrong for a long annotation (`[*Slowly, with the whole room singing along …]`) in a narrow column:

1. `padLyricsToFitChords` is given the annotation's full width, so the lyric after it is padded by more than a column
   and the next word and chord land alone at the far end of a later row.
2. The wrap check fires even at the start of a row, adding an empty chord row above the annotation:

```kotlin
if (x + chordWidth > columnWidth) { chordY += options.fontSize * 1.3f; x = 0f }
…
chordX = x + chordWidth + measure(" ", options.fontSize, false)
```

and `chordX` advances by the unwrapped width, so the chord after a wrapped one always jumps to a new row.

With chords hidden the padding stays behind as an all-space row.

## Fix

- Pass `chordWidths` to `padLyricsToFitChords` as `minOf(width, columnWidth)`.
- Only move down when `x > 0f`.
- After a wrapped name, advance `chordX` from the width of its **last** piece.
- Compute the padding only from the chords that are visible (it already uses `visible`; make sure a line whose
  remaining text is blank after hiding is dropped by the existing `return@lineLoop`, comparing after trimming
  U+00A0/U+200B too).

## Tests

`PrintLayoutTest`: a line `[*<annotation three columns wide>]Lyrics here [G]follow` at two columns: no row with zero
parts precedes the annotation, "follow" starts on the row directly after "Lyrics here" or on the same one, and every
part's `x + measure(text) <= columnWidth`.

## Manual check

Export a song with a sentence-long annotation at two columns; no empty rows around it.
