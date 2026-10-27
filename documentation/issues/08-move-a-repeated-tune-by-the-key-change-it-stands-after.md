<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Engrave a tune written again after a `{transpose}` in the key it stands in, on screen and in the PDF (a decision)

**Challenged:** amended — the running offset no longer moves inside a chorus recall (a `{transpose}` in the chorus it
repeats would have moved every later tune off the key `abcTunesOf` renders it under, leaving it text, on screen and in
the PDF); the PDF passes each group's start offset rather than keeping a running one in `PrintRows`, which
`PrintLayout.song` asks for some groups twice; a test pins that the model's offsets and `abcTunesOf` agree.

**Kind:** bug (documented behaviour) · **Severity:** low · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/AbcTuneOffsets.kt`,
`.../ui/notation/SongNotations.kt`, `.../ui/notation/NotationCache.kt` (plan 06), `.../ui/notation/NotationStandIns.kt`
(plan 07), `.../ui/songView/model/RenderSection.kt`, `.../ui/songView/model/SongRenderSections.kt`,
`.../ui/songView/SongSectionContent.kt`, `.../ui/print/PrintNotations.kt`, `.../ui/print/PrintRows.kt`,
`.../ui/print/PrintLayout.kt`, `.../ui/notation/CLAUDE.md`, `.../ui/print/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/AbcTuneOffsetsTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/RenderSectionsTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Written against the code as plans
05–07 leave it.

## Problem

A song that writes the same `abc` block twice, once before and once after a `{transpose}` (a melody line repeated for a
last chorus a semitone up, a common arrangement), draws the second copy in the first copy's key: its staff and its chord
symbols a semitone below the lyrics' chords around it. A player reading the staff plays the wrong key.

The offset is chosen per tune text, first occurrence wins:

```kotlin
// ui/notation/AbcTuneOffsets.kt
/** ... A tune written twice is moved as it first stands. */
internal fun abcTuneOffsets(blocks: List<ChordProBlock>): Map<String, Int> {
    ...
    is ChordProBlock.Section -> block.lines.groupIntoRuns().forEach { run -> run.abcTune()?.let { offsets.getOrPut(it) { offset } } }
```

and every lookup is by text alone: `rendered.renderings[tune]` in `SongNotations.of` (screen), and
`lines.abcTune()?.let { notations[it] }` in `PrintRows.verbatimRows` (PDF, fed by `printNotationsOf` in
`ui/print/PrintNotations.kt`, which renders `abcTuneOffsets(...)` the same way). The lyrics' chords, by contrast, are
moved by the offset where they stand: `ChordProChordRewriter.rewriteChords` walks the blocks with
`if (block is ChordProBlock.Transpose) offset = block.semitones`. The behaviour is documented, so changing it is the
user's call.

## Decision

- **A. Keep it** (documented: "A tune written twice is moved as it first stands"). No cost. Workaround for a user:
  make the copies differ by a byte (`X:2`).
- **B. Key every tune by its text and its offset** (recommended). Cost: one integer carried from the model to the run
  (`SectionItem.RunSlot`), one in the PDF's row builder, and the maps re-keyed; about 60 lines over the files listed,
  with tests. A tune repeated under the same offset still renders once; one repeated under two offsets renders twice,
  which is what it is. No file past its size limit (`PrintRows.kt` is 484 lines and `PrintLayout.kt` 446; each gains
  a few).

Recommended: **B**. The notation's whole contract (`ui/notation/CLAUDE.md`) is that the staff's chord names are the
page's; a staff a semitone off its own lyrics is a wrong page, and the workaround is undiscoverable.

## Fix (option B)

1. **`AbcTuneOffsets.kt`**: `internal data class AbcTune(val text: String, val offset: Int)` and
   `internal fun abcTunesOf(blocks: List<ChordProBlock>): Set<AbcTune>` (a `LinkedHashSet`, song order, each text and
   offset once), replacing `abcTuneOffsets`. KDoc: a tune written again under another key change is another tune.
2. **Model**: `SectionPart.Lines` (`ui/songView/model/RenderSection.kt`) gains `val tuneOffset: Int`, the semitones of
   the last `{transpose}` before its lines; `SectionItem.RunSlot` gains `val tuneOffset: Int`, set from its part in
   `buildSectionItems`. The offsets must be exactly the ones `abcTunesOf` walks, or a run's key is missing from the
   renderings and the tune falls back to text: `abcTunesOf` (like `ChordProChordRewriter.rewriteChords`) sees the
   song's top-level blocks only, so a `{transpose}` inside the chorus a `ChorusRecall` repeats moves nothing after the
   recall. So in `SongRenderSections.kt`:
   - keep `var tuneOffset = 0` across the top-level `forEach`, and after each group's branch has run, unless the group
     is a `ChorusRecall`, set it from the group's last `ChordProBlock.Transpose` piece, if any (the top-level
     `is ChordProBlock.Transpose ->` group included), whether or not the change draws anything;
   - `addSection` takes the offset its group starts under (`startOffset: Int`) and keeps a local `var offset`, set in
     its `is ChordProBlock.Transpose ->` piece branch before the `toRenderSection` call, building
     `SectionPart.Lines(lines, offset)`. The recall branch passes the running `tuneOffset` and its pieces' changes stay
     local, never reaching the song's. Where two halves are merged (`SectionPart.Lines(previous.lines + lines)`) keep
     `previous.tuneOffset`: an `abc` run never straddles the cut, since a delegated environment opens a section of its
     own (the parser closes the one before it) and a `{transpose}` inside it is a line of its text; say so in a comment.
   - Test the recall case (below).
3. **Screen**: `NotationRequest.tunes: Set<AbcTune>`; `RenderedNotations.renderings: Map<AbcTune, NotationRendering>`,
   `rerenderedTunes: Set<AbcTune>`; `NotationTuneKey` (plan 06) takes `tune.text` and `chords.movedBy(tune.offset)` as
   it does now; `notationStandInsOf` (plan 07) works on `AbcTune` lists. `SongNotations.of(run, tuneOffset)` looks up
   `AbcTune(run.abcTune() ?: return null, tuneOffset)`; `SectionRun` (`SongSectionContent.kt`) gains a `tuneOffset`
   parameter, passed `sectionItem.tuneOffset` at its one call site.
4. **PDF**: `printNotationsOf` returns `Map<String, Map<AbcTune, NotationDrawing>>`; `PrintRows.notations` takes that
   map. No offset is kept as state in `PrintRows`: `PrintLayout.song` calls `groupSegments` twice for the groups up
   to the first that prints (once to find it, once to place it), so a running value there would depend on the call
   order. Instead `PrintLayout.song` computes once, beside `groups`, the offset each group starts under (the same walk
   as the screen's: a group's last `Transpose` piece moves the groups after it, a `ChorusRecall` group moves nothing),
   and passes it to `groupSegments(group, width, startOffset)`; `sectionSegments` takes it and keeps a local offset set
   in its `is ChordProBlock.Transpose ->` branch (whether or not a key line prints), handing it to the `Lines` part;
   `verbatimRows` looks up `notations[AbcTune(tune, offset)]`. Chorus recalls never hold an `abc` run (the delegated
   environment closes the chorus), so `recallRows` passes its start offset through and nothing else. Put the walk in
   one `internal` function in `AbcTuneOffsets.kt` over `joinCutSections` groups, used by the PDF and, if it reads as
   plainly, by `SongRenderSections` too, so the screen and the page cannot disagree.
5. **Docs**: `ui/notation/CLAUDE.md`'s "A tune under a mid-song `{transpose}` is moved by it too (`abcTuneOffsets`)"
   becomes "… (`abcTunesOf`), each copy of a tune by the key change it stands after"; `ui/print/CLAUDE.md`'s
   `printNotationsOf` bullet says each copy under its own key change.

## Tests

- `AbcTuneOffsetsTest` → `` `a tune written again after a key change is moved by that one` ``: the existing song with
  the last copy of `X:1` placed after `{transpose: 2}` gives `{(X:1…, 0), (X:2…, 2), (X:1…, 2)}`; keep the existing
  case (same text under the same offset is one tune).
- `RenderSectionsTest`: `` `a run carries the key change it stands after` ``: two `abc` sections around a
  `{transpose: 1}` give `RunSlot.tuneOffset` 0 and 1; `` `a key change inside a recalled chorus moves no tune after
  the recall` ``: a chorus with `{transpose: 2}` between two of its lines, then `{transpose: 0}`, then `{chorus}`,
  then an `abc` section gives that run `tuneOffset` 0 (the recall repeats the chorus's `Transpose` piece, which must
  not move it to 2), and its `AbcTune` is in `abcTunesOf` of the same blocks (assert the two agree: that is the
  invariant). `PrintLayoutTest` gets the same song as a second case of its test below.
- `PrintLayoutTest`: `` `a tune printed twice around a key change prints each copy in its own key` ``: hand
  `layoutPrintDocument` two distinguishable drawings for `(tune, 0)` and `(tune, 2)` and assert the page's
  `PrintNotation`s reference each once, in that order.
