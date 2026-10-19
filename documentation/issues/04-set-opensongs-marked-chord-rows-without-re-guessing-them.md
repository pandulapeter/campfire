<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Set the chord rows an OpenSong song marks with a dot as chord rows, instead of guessing them again

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Challenged:** sound
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/OpenSongSong.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/OpenSongSongTest.kt`,
`data/source/local/implementation/CLAUDE.md`

Lane I, after 03 (both add to `ChordSheetConverter.kt`, in different places).

## Problem

OpenSong marks every chord row itself: a lyrics line starting with `.` is a chord row over the lyric line under it
(`OpenSongSong`'s KDoc). `OpenSongSong.Section.blocks` drops that knowledge and hands the block to the converter's
guess:

```kotlin
line.startsWith('.') -> block += line.drop(1).trimEnd()
…
written += ChordSheetConverter.convertFragment(text, String::normalizedToNfc)?.trimEnd() ?: ChordSheetLyrics.escaped(text)
```

`convertFragment` takes a row of a single chord for a chord row only with corroboration (a row of two chords elsewhere
in the text, a heading before it, or bold type), as `convert/CLAUDE.md` requires for pasted text ("a single chord … is
a chord … only with corroborating evidence"). A block of an OpenSong song whose rows each hold one chord has none, so
`convertFragment` returns null and the whole block is escaped as lyrics. Verified at HEAD:
`<song><title>Riverlight</title><lyrics>[V1]\n.G\n Walking down the road\n.C\n Singing all the way</lyrics></song>`
imports as

```
{title: Riverlight}
{start_of_verse: Verse 1}
G
Walking down the road
C
Singing all the way
{end_of_verse}
```

with the chords as lyric lines: not transposable, not drawn as chords, and the song's chord list empty. Slow hymns
and worship songs, OpenSong's main users, are full of one-chord-a-line blocks. The guess can also go the other way: a
lyric line that reads as a heading (` Chorus` at the end of a block) becomes a `{chorus}` recall inside the verse.

`data/source/local/implementation/CLAUDE.md` describes the current behaviour ("`.` chord rows are set over the lyric
rows under them by `convertFragment`").

## Fix

1. `ChordSheetConverter`: a public function that sets rows a format has marked, guessing nothing:

   ```kotlin
   /**
    * Lines of plain [text] a format marks itself (OpenSong's `.` rows), with the rows at [chordRows] (line indexes) set
    * into the lyric line under each the way [convertFragment] sets a chord row, or bracketed on a line of their own
    * where no lyric follows. Nothing is guessed: no other line is taken for chords, a heading, metadata, or tablature,
    * and a marked row holding anything but chords (and the playing note a row may end in) stays a line of text. Every
    * line that is not set is escaped, as [ChordSheetLyrics.escaped] does.
    */
   public fun setChordRows(text: String, chordRows: Set<Int>, normalize: (String) -> String = { it }): String {
       val lines = ChordSheet.ofPlainText(text).pages.single().lines.map { render(it, normalize) }
       val candidates = lines.map(::chordTokens)
       val kinds = lines.indices.map { index ->
           when {
               lines[index].text.isBlank() -> Kind.BLANK
               index in chordRows && candidates[index] != null -> Kind.CHORD
               else -> Kind.LYRIC
           }
       }
       return writeBody(lines, kinds, candidates, omitted = emptySet(), changes = emptyMap(), convertParentheses = false)
           .joinToString("\n")
   }
   ```

   No `expandLabels` (it would shift the indexes, and a marked row has no label), no `chordCandidates` guards (the
   bare-Latin-note and lettered-list guards exist because a row may be sung; a marked row is not), and `writeBody`'s
   `CHORD` branch already merges with the next `LYRIC` line or brackets the row alone. Put it next to
   `convertFragment`. The file is past its line budget already; this is a dozen lines and needs no split (the size check
   only fails a crossing).
2. `OpenSongSong.Section.blocks`: track the marked rows of the block and use the new function:

   ```kotlin
   val block = mutableListOf<String>()
   val chordRows = mutableSetOf<Int>()
   fun endBlock() {
       if (block.isEmpty()) return
       val text = block.joinToString("\n")
       written += if (chordRows.isEmpty()) ChordSheetLyrics.escaped(text)
           else ChordSheetConverter.setChordRows(text, chordRows, String::normalizedToNfc).trimEnd()
       block.clear()
       chordRows.clear()
   }
   …
   line.startsWith('.') -> if (line.drop(1).isNotBlank()) { chordRows += block.size; block += line.drop(1).trimEnd() }
   ```

   (An empty `.` line would otherwise be a blank inside the block.) Keep the comment on dropping the marker. The
   `convertFragment` import goes away from this file; OnSong keeps using `convertFragment`, since OnSong does not mark
   its rows.
3. `data/source/local/implementation/CLAUDE.md`, the OpenSong bullet: "`.` chord rows are set over the lyric rows under
   them as marked (`ChordSheetConverter.setChordRows`, nothing re-guessed), and a block with no chord row is
   `ChordSheetLyrics.escaped`."
4. `convert/CLAUDE.md`: one bullet after `convertFragment`'s: `setChordRows` sets the rows a format marks itself as
   they are marked, guessing nothing, for OpenSong.

## Tests

- `OpenSongSongTest` (`./gradlew :data:source:local:implementation:desktopTest`):
  - `rows of one chord each are set over their lyrics`: the song above → the text contains
    `{start_of_verse: Verse 1}\n[G]Walking down the road\n[C]Singing all the way\n{end_of_verse}`.
  - `a lyric line spelled like a heading stays a lyric`: `[V1]\n.G           D\n Down by the river\n Chorus` → the
    text contains `[G]Down by the [D]river\nChorus` and no `{chorus}`.
  - The existing tests (two verses and a chorus, numbered verses, unmarked lyric lines, lyrics only) stay green.
- `ChordSheetConverterTest` (`./gradlew :chordpro:desktopTest`): `marked chord rows are set as marked`:
  `ChordSheetConverter.setChordRows("G\nWalking down the road\nDo  Re\nSinging\nG   D", setOf(0, 2))` is
  `"[G]Walking down the road\n[Do]Sing[Re]ing\nG   D"`: single chords and a row of bare Latin notes are set because
  they are marked, and the unmarked `G   D` stays text.
