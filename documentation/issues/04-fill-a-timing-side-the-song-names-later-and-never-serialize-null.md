# Fill a change's missing tempo or time with the song's own value named later, and never serialize `null`

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — named the click fix the time side brings (a null time played 4/4 on that page), checked that a filled tempo scales exactly as a null one did under an override, and recorded the constraint on plan 21 (it must clamp the change's tempo too, or a filled out-of-range tempo is scaled wrong).
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializer.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProBlock.kt` (KDoc only),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSerializerTest.kt`, `chordpro/CLAUDE.md`

## Problem

A song whose own tempo (or time) is written *below* a change of the other value — the first readable `{tempo}` of a
song whose header has none is the song's own, wherever it stands — parses to a `ChordProBlock.Timing` with `null` on
that side, while `ChordProMetadata.tempo` holds the value. Verified at 1c52e5347 with
`{title: X}\n{time: 4/4}\n\nla\n{time: 3/4}\nlo\n{tempo: 90}\nli`: `metadata.tempo = "90"`, blocks
`[Section(la), Timing(tempo=null, time=3/4), Section(lo, li)]`.

Two things follow:

1. `ChordProSerializer` writes the header's `{tempo: 90}` first and then compares the block with it
   (`ChordProSerializer.kt:112-114`):

   ```kotlin
   fun timing(block: ChordProBlock.Timing) = buildList {
       if (ChordProTempo.parse(block.tempo) != ChordProTempo.parse(tempo)) add("{tempo: ${block.tempo}}")
       if (ChordProTime.parse(block.time) != ChordProTime.parse(time) || isEmpty()) add("{time: ${block.time}}")
   ```

   `null` differs from `90`, so it writes the literal `{tempo: null}`:
   `{title: X}\n{tempo: 90}\n{time: 4/4}\n\nla\n{tempo: null}\n{time: 3/4}\nlo\nli`, and
   `parse(serialize(parse(x))) != parse(x)`. (`ChordProSerializer` is only called from tests today, hence low.)
2. Prettify moves that `{tempo: 90}` into the header (`hoistedTimings`), after which the same file parses to
   `Timing(tempo=90, time=3/4)` — so Prettify changes what the file parses to, and the page's timing line reads "3/4"
   before and "90 BPM · 3/4" after. The click already plays both the same (`SongTempo.kt`'s `sectionBpm` plays a
   `null` section tempo at the song's opening one), so `90` is what the model should say.

## Fix

Options:

- **A (recommended):** at the end of `parseAsWritten`, on `keyedBlocks` before `withChorusesRecalled` (`return ChordProSong(metadata = declared, blocks = withChorusesRecalled(keyedBlocks))`), replace a
  `null` side of every `ChordProBlock.Timing` with the song's own value when it is readable
  (`declared.tempo?.takeIf { ChordProTempo.parse(it) != null }`, the same for `time`). A `null` there only ever means
  "no value of that kind had been seen yet", and the song's own value is what is in force from the start, so this
  cannot turn a block into a no-op (the side that changed was non-null already). Then also harden the serializer:
  write a side only when it is non-null and differs, and in the `isEmpty()` fallback write whichever side is
  non-null (`block.time ?: …`), never the string `null`; with A no parsed song reaches that case, but a hand-built
  model can.
- B: serializer only — skip a `null` side. That removes the `{tempo: null}` text, but the round trip still fails
  (the header's `{tempo: 90}` makes the reparsed block `Timing(90, 3/4)`), and Prettify still changes the parse.

Update the `ChordProBlock.Timing` KDoc ("null where the song never named one") and the matching phrase of the
`ChordProParser` entry in `chordpro/CLAUDE.md` ("null where the song never named one") to "null where the song names
none, even further down".

What the fill changes downstream (checked against every consumer of `ChordProBlock.Timing`):

- **Tempo, the click and the page.** `SongTempo.kt`'s `withTempo` scales a block's tempo by `sectionBpm(block, songFile, played)`.
  Before, a `null` side gave `playedBpm`; after, the filled side equals `songFileBpm`, and
  `sectionBpm(x, x, played) = x * played / x = played` exactly, so an override plays the same. Without an override the
  page's line now reads the song's tempo (held to 30–300 by `toRenderSection`) and the click plays it, which is what
  `effectiveTempo` played before. Plan 21 must clamp the change's own tempo as well as the opening one, or a filled
  `{tempo: 400}` would be scaled by the clamped 300 (see plan 21); land 21 as amended.
- **Time, the click.** `SongDetailsScreen.kt`'s `toSongTiming` reads a `null` time as 4/4 (`RenderSection.Timing`'s
  `COMMON_TIME`), so today a stretch after a tempo-only change of a song that names its `{time: 3/4}` at the bottom
  clicks 4/4 there while the opening clicks 3/4. With the fill it clicks 3/4 — part of this fix; mention it in the
  manual check.
- The PDF's timing line (`PrintLayout.timingRows`) names the filled side, as the page does. Nothing compares parsed
  models across files (the import compares prettified text), so nothing else moves.

This plan and plan 03 both edit `ChordProParser.kt` (different functions); land 03 first. 03 compares raw values before
the fill, which is right: the fill never changes whether a block is a change.

## Tests

- `ChordProParserTest`: `{title: X}\n{time: 4/4}\n\nla\n{time: 3/4}\nlo\n{tempo: 90}\nli` has exactly
  `Timing(tempo="90", time="3/4")`; and `ChordProParser.parse(ChordProPrettifier.prettify(x)) == ChordProParser.parse(x)`
  for it. `{title: X}\n\nla\n{time: 3/4}\nlo` (no tempo anywhere) keeps `Timing(tempo=null, time="3/4")`.
- `ChordProSerializerTest`: add that input and `{title: X}\n{tempo: 120}\n\nla\n{tempo: 90}\nlo\n{time: 3/4}\nli` to
  `a tempo or time change survives serializing`, and assert the serialized text of each contains no `null`.
  Serializing a hand-built song with `Timing(tempo = null, time = "3/4")` and no metadata tempo writes `{time: 3/4}`
  and no `{tempo`.

## Manual check

Open a song that names its tempo at the bottom, below a `{time}` change, on the song details
screen; the timing line names the song's tempo too, before and after running Prettify in the editor. With the click
playing, a song with `{tempo: 100}` in its header, a `{tempo: 120}` further down and `{time: 3/4}` at the bottom
clicks three beats a bar on the page after the change too.
