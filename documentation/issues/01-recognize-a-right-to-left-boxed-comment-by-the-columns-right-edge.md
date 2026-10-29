<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Recognize a boxed comment of a right-to-left song's PDF by its right edge against the lines around it, never a lyric by another line's left edge

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayout.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetCampfireLayoutTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md` (only if it describes the box test)

**Challenged:** amended — the side is decided by the song's layout (alignment of its lines, else their letters), not by the candidate line's own letters (an English `{comment_box}` in a Hebrew song is printed flush right too); the right-edge test mirrors today's left-edge test against other lines' ends rather than the column's largest end (an RTL chorus is indented 8 pt on the right, so a box inside one ends 13 pt in); the right edge is the largest `end` of the non-blank spans, not the last span's; the box test needs no column grouping (other columns' edges are far away and cannot match 5 pt); only the side vote is taken per column.

## Problem

Campfire's own PDF is read back by `ChordSheetCampfireLayout.roles` (reviewed commit 54985be95). A boxed comment is
recognized by its left edge sitting 5 pt right of some other line's left edge:

```kotlin
spans.none { it.isBold || it.isItalic || it.isMonospace } && starts.any { other ->
    other != null && start - other in BOX_INSET - TOLERANCE..BOX_INSET + TOLERANCE
} && starts.none { other -> other != null && start - other in TOLERANCE..BOX_INSET - TOLERANCE } -> Role.BOX_COMMENT
```

`starts` are left edges. Since 62c663120 a right-to-left song is printed flush right: `PrintRows.wrapped` /
`formattedRows` put each row at `x = width - measure(...)` whenever the *song's* style is right to left
(`lyricStyle.direction` is `ChordProSong.textDirection`, whatever the row's own letters), and `boxedRows` lays the
comment out in `width - 10f` and shifts it by 5, so its *right* edge is 5 pt in from the edge of the lines around it.
In a Hebrew or Arabic song the left edges of lyric lines depend only on their lengths, so any plain lyric line whose
left edge happens to fall 4.25–5.75 pt right of another line's (with none 0.75–4.25 pt right of it) is imported as
`{comment_box: …}`, and the chord row above it is left on its own line. With 20–30 rows a page this hits roughly one
lyric line in ten. A real boxed comment of an RTL song is never recognized. The fixtures (`campfire-hebrew.pdf`, two
lyric lines; `campfire-hebrew-columns.pdf`, lines of one width) do not exercise it.

Facts the fix must respect (`PrintRows.kt`, `PrintLayout.kt`):

- The side is the song's, not the line's: an English `{comment_box: x2}` or an English lyric in a Hebrew song is flush
  right as well. Testing each line's own letters (`writingDirectionOf(line)`) would keep both bugs for those lines.
- A right-to-left chorus is laid out in `width - CHORUS_INDENT` (8 pt) with no shift, so its lyrics end 8 pt in from the
  verse lines and a box inside it ends 13 pt in: the box is 5 pt in from its *neighbours*, not from the column's
  largest right edge.
- With `startSongsOnNewPage` off and one column, one page may hold the end of a left-to-right song and the start of a
  right-to-left one (only multi-column layouts force a page when the direction turns).
- An RTL row arrives as one span (`ActualText`, `pieces`) whose `start`/`end` cover its glyphs; a line may still have
  several spans, and reading order is not left-to-right order, so the right edge is the largest `end`, not the last
  span's.

## Fix

In `roles`, decide for each line which side its song is laid out from, then run the matching test:

1. **Split the page into songs** at title lines (every non-blank span bold, not monospace, and at least
   `bodySize * 1.3`; the export's title is 1.6× the body). Lines before the first title belong to the song the page
   continues.
2. **Side of a segment**: flush right when more of its plain lines (no bold, italic, or monospace span) end within
   `TOLERANCE` of the segment's largest right edge than start within `TOLERANCE` of its smallest left edge; on a tie
   (one line, or a page of lines of one width), by `writingDirectionOf` over the joined text of those lines (from
   `chordpro/language`, the one vote). For a two-column segment, take the extremes among the lines that overlap the
   candidate horizontally, so each column is judged by its own edge.
3. **Left-to-right segment**: today's test, unchanged (it is what LTR songs are pinned by).
4. **Right-to-left segment**: the exact mirror, over right edges (`ends` = for each line the largest `end` of its
   non-blank spans, or of its spans as `starts` does if a trailing space is drawn at the edge — check what the export
   writes for an RTL lyric's trailing space before choosing): `other - end` within `BOX_INSET ± TOLERANCE` for some
   other line, and no other line with `other - end` within `TOLERANCE..BOX_INSET - TOLERANCE`. This is relative to
   other lines exactly as the LTR test is, so a box in an RTL chorus (13 pt in, chorus lyrics 8 pt in) is found and a
   chorus lyric (8 pt from the verse lines) is not taken for one.

The same no-bold/italic/monospace condition applies to both sides. Update the KDoc of `ChordSheetCampfireLayout` ("a
boxed comment's text 5 points in from the column") to say "in from the lines around it, on the song's side".

## Tests

In `ChordSheetCampfireLayoutTest`:

- An RTL page of Hebrew lyric lines flush right with varied lengths, one of them starting exactly 5 pt right of
  another's start, gets no `BOX_COMMENT`.
- An RTL line whose right edge is 5 pt in from the others' gets `BOX_COMMENT`, in Hebrew and in English (`x2`) letters.
- An RTL chorus (lines ending 8 pt in) gets no `BOX_COMMENT`; a box inside it (13 pt in) gets one.
- A page holding the end of an English song, a title, and a Hebrew song: each part is judged on its own side.
- The existing LTR box cases still pass.

If practical, extend `DocumentGoldenTest` with a Hebrew PDF that has a boxed comment, a chorus, and uneven lines
(`generate_hebrew_fixtures.py` documents how the fixtures are made).
