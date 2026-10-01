# Do not reserve an empty lyric row under a chord-only line

**Kind:** output quality  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

## Problem

Every fragment gets a lyric row, even when the line has chords and no words:

```kotlin
val lyricY = if (parts.isEmpty()) 0f else chordY + options.fontSize * 1.3f
parts += Part(fragment, y = lyricY, size = options.fontSize)
val rowHeight = lyricY + options.fontSize * 1.45f
```

An intro like `[G] [C] [D] [G]` therefore takes two rows, leaving a double gap before the verse, and a wrapped
chord-only line prints as chords, blank, chords, blank (live run: `demo-rising-p1`, `hard-continuous-2col-p2`).

## Fix

When the fragment is blank (after padding: only spaces, U+00A0 and U+200B) and the row has chords, add no lyric `Part`
and make the row `chordY + fontSize * 1.45f` tall. A blank fragment with no chords keeps today's row (it is an empty
lyric line the song asked for).

## Tests

`PrintLayoutTest`: a section with one chord-only line followed by a lyric line — the lyric's y is exactly one chord row
(`fontSize * 1.45f`) under the chords' y, and no `PrintText` with blank text is emitted for the chord line.

## Manual check

Export the demo song with the instrumental intro; the intro is one row and the gap under it matches other lines.
