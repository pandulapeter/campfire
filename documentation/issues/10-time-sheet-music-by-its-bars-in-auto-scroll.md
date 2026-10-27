<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Count an engraved staff system as its bars, two to a line, when auto-scroll times a song, and a title system as none

**Challenged:** amended — a system counts its *bars*, not its bar lines: a bar line standing before any music of its
system (`|:`, `[|`, or a plain `|` opening the source line, all of which abc2svg draws) closes no bar and is left out,
which the draft counted (a four-bar line written `|:…:|` timed as three lines, not two). Probed in node on real
abc2svg output. One field (`NotationSystem.bars`, a music system with no bar line counting one, a system without music
none) replaces `barLines` plus `drawsMusic`, whose clef-only empty staff would have played a line;
`withChordNames` must carry the field.

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationDrawing.kt`,
`.../ui/notation/NotationChordNames.kt` (`withChordNames` rebuilds each `NotationSystem`),
`.../ui/notation/NotationDrawingParser.kt`, `.../ui/notation/SongNotations.kt`, new
`.../ui/notation/NotationPlayedLines.kt`, `.../ui/songView/SongNotationBlock.kt` (`inSlot` moves),
`.../ui/songView/model/SongUnits.kt`, `.../ui/songView/SongLyrics.kt`, `.../ui/notation/CLAUDE.md`,
`.../ui/songView/CLAUDE.md`, `.../ui/screens/songDetails/autoScroll/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationDrawingParserTest.kt`,
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationPlayedLinesTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongUnitsTest.kt`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Written against the code as plans
05–09 leave it: renderings hold their drawing and painted systems; if plan 08 landed, a tune is looked up by its text and
`RunSlot.tuneOffset`.

## Problem

In the scrolling layout, a song's time is shared out over its lines (`ui/screens/songDetails/autoScroll/CLAUDE.md`:
"Time is spent on lines"), a line being two bars at the song's tempo:

```kotlin
// autoScroll/AutoScrollTrack.kt, ownSecondsPerLine
BARS_PER_LINE * beatsPerBar.coerceAtLeast(1) * SECONDS_PER_MINUTE / bpm.coerceAtLeast(1)
...
private const val BARS_PER_LINE = 2
```

A line is counted per chunk by `SongUnits.of` (`ui/songView/model/SongUnits.kt`), and every slot of a run counts one:

```kotlin
playedLineCount += kinds?.takeUnless { isSectionFolded }?.let { range.count { item -> it[item] == SectionItemKind.CONTENT || it[item] == SectionItemKind.RUN } } ?: 0
```

then `AutoScrollTrack.of` drops the slots with no height:
`val playedLines = IntArray(tops.size) { index -> if (spans[index] > 0) rows.playedLines[index] else 0 }`.

An engraved tune is drawn one staff system a slot (`SongNotationBlock`: "slot *n* draws staff system *n*, the last one
the rest"), so each system counts as one line, two bars. A system holds four to eight bars (a phone's column to the
560 dp desktop one), so the staff passes the reading line two to four times faster than it is played, exactly where a
player reading notes needs the time most. A tune's header system (its title, composer, in a medley each tune's) has
height and so counts a line too, though nothing is played there. A `{duration}` song is skewed the same way, its time
shared over a line count the tunes inflate.

## How to count bars (verified)

- **From the drawing (recommended).** abc2svg draws every bar line of a system as `M<x> <y>v<-h>` subpaths of one
  `<path class="bW" d="…">` (thin) and one `<path class="bthW" d="…">` (thick), built in its `out_bars`
  (`abc/implementation/src/jvmSharedMain/resources/abc2svg/abc2svg-1.js`: `sb+='M'+sx(x).toFixed(1)+' '+…+'v'+…`,
  then `output+='<path class="bW" d="'+sb+'"/>'`; a coloured or dotted bar is a `bW` path of its own). Its CSS is
  `.bW{stroke:currentColor;fill:none;stroke-width:1}` and `.bthW{…stroke-width:3}`. A bar line is drawn once per staff,
  and a double, final, or repeat bar is two strokes 3–6 units apart, so the bar lines of a system are the distinct x
  positions of those subpaths, strokes closer than a few units being one bar line. That is the system as drawn: lines
  abc2svg wrapped to fit the width, several voices or a grand staff, inline key changes, and `[|]` all resolved.
- **A bar line opening a system closes no bar.** abc2svg draws `|:`, `[|`, and a plain `|` written at the start of a
  source line, right after the clef, key, and time signature (`|:"D"d2fd AdfA|"G"B2dB "A"AFDF:|` gives bar lines at
  x 63.9/68.4, 438.3, 792.0/796.5: three bar lines, two bars). So the bars of a system are its bar lines less a first
  one with no music glyph to its left, music being any glyph of abc2svg's font but its clefs and time signatures
  (U+E050–U+E09F), accidentals (U+E260–U+E26F, a key signature), and repeat signs (U+E040–U+E04F); its note heads
  are U+E0A4 and so on, rests U+E4E5 and so on. A pickup before the first bar line counts as a bar.
- **Probed** (node through `campfire-abc-host.js`, `../plans-ec/lib.js`'s header; the challenge's
  `challenge-n2/b4.js`): a two-part reel of four bars a line, each part `|:…:|`, gives 4 + 4 at width 800, 2 + 2 + 4
  at 400, and 1 + 1 + 2 + 1 + 1 + 2 at 250 (abc2svg's own wrapping): 8 bars every time, where bar lines alone give
  10. `[|CDEF GABc|]` gives 1; `CDEF::GABc|` 2 (`::` is two thick strokes 6 apart); `A|CDEF|GABc|` 3 (pickup
  counted); a grand staff (`%%score {RH | LH}`) of three bars gives 3, each bar line drawn at the same x on both
  staves; two voices on two staves 2; `M:none` with no bar line 0 bar lines; a tune's title system draws no music
  glyph and no bar line.
- **From the ABC text.** Rejected: a source line is not a system (abc2svg wraps a line too wide for the page), and
  counting `|` means skipping chord symbols, decorations (`!…!`), inline fields, comments, `|]`/`||`/`:|:` pairs, and
  counting each voice (`V:`) again; the result could only be spread evenly over the systems.

Repeats are not unrolled either way: the scroll passes a repeated passage once, at the pace it is written, as it does a
chorus line marked `x4`.

## Fix

1. **The parser counts bars.** In `NotationDrawingParser.kt`'s reader, per system (reset in `read(root)` with the
   other lists):
   - in `readPath`, where the element's classes include `bW` or `bthW` (constants beside the reader, named for
     abc2svg's classes), collect the subpaths' x positions through the context's transform (`context.transform.mapX`);
   - in `readText`'s music branch, where the code point is known, keep the smallest x (the mapped one the glyph is
     placed at) of a glyph that is music (`isMusicGlyph(codePoint)`, below). Not in the branch for glyph runs inside
     words (a header's tempo note), which plays nothing.

   `NotationSystem` gains `val bars: Int` (required, no default; `withChordNames` in `NotationChordNames.kt` builds
   each system again and must pass it on), from pure, tested helpers in new `ui/notation/NotationPlayedLines.kt`:

   ```kotlin
   /** The x of every `M x y` move of abc2svg's bar path data. */
   internal fun barStrokeXsOf(pathData: String): List<Float>

   /** Whether [codePoint] of abc2svg's music font is music played, rather than a clef, a time or key signature, or a repeat sign. */
   internal fun isMusicGlyph(codePoint: Int): Boolean

   /**
    * The bars a staff system plays, from abc2svg's bar strokes at [barXs] and the first music glyph at [firstMusicX]
    * (null: the system draws no music, a tune's title and composer, words printed after it, an empty staff): strokes
    * closer than [BAR_STROKE_GAP] units are one bar line (a double, final, or repeat bar), a first bar line with no
    * music before it (`|:`, `[|`, a `|` opening the line) closes no bar, and a system of music with no bar line closing
    * one (a tune in free time) plays one.
    */
   internal fun barCountOf(barXs: List<Float>, firstMusicX: Float?): Int
   ```

   with `BAR_STROKE_GAP = 8f` (abc2svg's thin-thick and repeat bars sit 3–6 units apart, `::` 6; two bars of music
   are never closer than a note head and its spacing, well over 8). Path data the regex cannot read adds nothing,
   never fails the drawing.

2. **What a system plays**, in the same file:

   ```kotlin
   /** How many lines of the song a staff system of [bars] takes to play: two bars to a line, as a lyric line is timed (`ownSecondsPerLine`). */
   internal fun playedLinesOf(bars: Int) = (bars + 1) / 2

   /** The lines the piece at [slot] of a run engraved as systems playing [systemLines] plays (see `SongNotationBlock`'s slots). */
   internal fun notationSlotLines(systemLines: List<Int>, slot: Int, isLastSlot: Boolean) = systemLines.inSlot(slot, isLastSlot).sum()
   ```

   By content rather than "the first system is the header", since a medley run holds one header per tune. Move
   `inSlot` from `SongNotationBlock.kt` into this file as `internal fun <T> List<T>.inSlot(slot: Int, isLastSlot: Boolean)`,
   so the drawing and the count cut a run alike.

3. **`SongNotations` answers per tune.** Add `val systemLines: Map<String, List<Int>>` (tune → each drawn system's
   `playedLinesOf(system.bars)`, from `rendering.drawing.systems`, the same filtered list the slots draw), built once
   per `rendered` (not per `unitSize`, so it is equal across a pinch's frames; a data `Map`). With plan 08, key it by
   `AbcTune`. A plan 07 stand-in is drawn under the new text, which has no entry, so the run counts a line a slot for
   the few frames until its own rendering arrives; that is fine.

4. **`SongUnits.of` takes a slot's lines.** Add `runSlotLines: (SectionItem.RunSlot) -> Int = { 1 }`, and count a
   `RUN` item as `runSlotLines(section.sectionItems[item] as SectionItem.RunSlot)` instead of 1 (`CONTENT` stays 1).
   Update its KDoc (`playedLineCount` "a piece of a run of tablature or …") to say an engraved system plays its bars.
   In `SongLyrics`, key the `units` `remember` on `notations.systemLines` too and pass

   ```kotlin
   runSlotLines = { slot ->
       notations.systemLines[slot.run.abcTune()]?.let { notationSlotLines(it, slot.slot, isLastSlot = slot.slot == slot.slotCount - 1) } ?: 1
   }
   ```

   A run still drawn as text (before its rendering, or failed) keeps one line a row, as a tab does. `AutoScrollTrack`
   needs no change: a slot with height and no lines (the title system) is passed at the song's average speed, as a
   heading is.

5. **Docs.** `autoScroll/CLAUDE.md`'s "`playedLines`: lyrics and tab or grid lines" gains "and an engraved staff
   system's bars, two a line (none for a title)"; `ui/songView/CLAUDE.md`'s auto-scroll bullet likewise;
   `ui/notation/CLAUDE.md`: the parser counts each system's bars (`bW`, `bthW` strokes; a bar line before any music
   closes none) for the scroll's timing.

## Tests

- `NotationPlayedLinesTest` (commonTest): `` `a double bar is one bar line` `` (`barCountOf([10, 50, 53], 5f)` → 2),
  `` `a bar line opening a system closes no bar` `` (`[63.9, 68.4, 438.3, 792.0, 796.5]` with music from 81.4 → 2;
  the same with music from 29 → 3, a pickup), `` `a tune in free time plays a bar a system` `` (`[]`, 29 → 1),
  `` `a system without music plays nothing` `` (`[]`, null → 0, and a bar line with no music → 0),
  `` `a system of eight bars plays four lines` `` (`playedLinesOf`), `` `bar strokes are read from abc2svg's path data` ``
  (`"M10.0 40.0v-24.0M50.5 40.0v-24.0"` → `[10.0, 50.5]`), `` `clefs, signatures, and repeat dots are not music` ``
  (U+E050, U+E084, U+E262, U+E043 false; U+E0A4, U+E4E5 true), `` `the last slot plays every system from there on` ``.
- `NotationDrawingParserTest`: `` `a system counts its bars once across its staves` ``: a system with a `bW` path of
  strokes at the same x on two staves, a `bthW` stroke beside the last, a leading thick-thin pair before its first
  note head, and a music `<text>` holding a clef and note heads gives `bars` as the bars drawn; a title system's
  `bars` is 0.
- `SongUnitsTest`: `` `a run's slot plays the lines it is given` ``: `SongUnits.of(..., runSlotLines = { 3 })` over a
  section with a tab run gives `playedLineCount` 3 for each of its chunks.
