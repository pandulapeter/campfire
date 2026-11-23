<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# Work out each ABC tune's text, and its spoken description, once per run instead of once per slot

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium (songs holding ABC tunes)  ·  **Platforms:** all
**Files:** (`ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`)
`ui/songView/model/RenderSection.kt` (`SectionItem.RunSlot`, `buildSectionItems`),
`ui/songView/model/UnitContents.kt`,
`ui/songView/SongLyrics.kt` (`runSlotLines`),
`ui/songView/SongSectionContent.kt` (`SectionRun`, the section heading's part picker),
`ui/notation/SongNotations.kt` (`of`, `placeText`, `partPickerOf` taking an `AbcTune`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/RenderSectionsTest.kt`,
`ui/songView/model/CLAUDE.md` only if a sentence there names how slots find their tune

## Problem

A tune is cut into many slots (`runSlotCount`: one per line and per 12 characters of every line), each a chunk the
layout composes, and every slot recomputes the whole tune's text. The measurement of an ABC song's push frame found 440
`SectionRun` / 439 `SongNotationBlock` instances, 65 MB allocated, GC of 4–20 ms inside the frame, and leaf samples of
`joinTo` 11%, `NotationReading.describedAs` 8%, `SongNotations.abcTune` 6%.

`buildSectionItems` (`RenderSection.kt`) gives every slot the same `run`:

```kotlin
List(slotCount) { slot -> SectionItem.RunSlot(run = run, kind = kind, slot = slot, slotCount = slotCount, runFoldKey = runFoldKey, tuneOffset = part.tuneOffset) }
```

and `List<ChordProLine>.abcTune()` (`ui/notation/SongNotations.kt`) joins all of it each time it is asked:

```kotlin
return joinToString(separator = "\n") { (it as ChordProLine.Verbatim).text }
```

It is asked per slot, in at least four places, each a new `String` whose hash is then computed afresh for the
`AbcTune(text, offset)` map lookups:

- `SongLyrics.kt`, building `units`: `slot.run.abcTune()?.let { systemLines[AbcTune(it, slot.tuneOffset)] }`
- `UnitContents.kt`, `notationOf`: `val tunes = slots.mapNotNull { slot -> slot.run.abcTune()?.let { it to slot.tuneOffset } }`,
  then `notations.of(tune, tuneOffset)` and `notations.partPickerOf(tune, tuneOffset)` per slot
- `SongSectionContent.kt`, `SectionRun` (composed per chunk): `val tune = remember(run) { run.abcTune() }`
- `SongSectionContent.kt`, the heading: `notations.partPickerOf(it.run.abcTune(), it.tuneOffset)`

That is O(slots × tune length) per pass, several passes on the push frame.

And `SectionRun` builds the screen reader's description in every chunk:

```kotlin
val description = remember(notationRun.reading, heading, chordsTemplate, wordsTemplate) {
    notationRun.reading.describedAs(heading, chordsTemplate, wordsTemplate)
}
```

though `SongNotationBlock` uses it only on the first slot:

```kotlin
.then(if (slot == 0) Modifier.semantics { contentDescription = description }.onSizeChanged { run.onPlaced(it.width) } else Modifier)
```

(Text-drawn runs have the same shape on a smaller scale: `remember(run) { run.map { it.monospaceText.orEmpty() } }` per
chunk, then `textMeasurements.tabRows(lines, ...)` hashing the whole list for its key; fold it in if it is free.)

## Fix

1. **Carry the tune on the model, built once per run.** In `buildSectionItems`, compute `val tune = run.abcTune()?.let { AbcTune(it, part.tuneOffset) }`
   once per run, beside `runFoldKey`, and give every `RunSlot` of that run the same instance as a new field
   `val tune: AbcTune?` (null for any run that is no ABC tune). Its `String` is then one instance whose hash is computed
   once. Equality is unchanged in meaning (it is derived from `run` and `tuneOffset`). `songView.model` already imports
   `ui.notation` (`UnitContents.kt`), so no layer is crossed. The model is built on Default (or once in place), so the
   join moves off the measure pass.
2. **Read it everywhere a slot's tune is needed**: `systemLines[slot.tune]` in `SongLyrics`, `slot.tune` in
   `notationOf`, the heading's `partPickerOf(slot.tune)`, and `SectionRun` takes `tune: AbcTune?` as a parameter
   instead of `remember(run) { run.abcTune() }` (its caller at `SongSectionContent.kt`'s `SectionRun(` call has the slot).
   Change `SongNotations.of`, `placeText`, and `partPickerOf` to take `AbcTune?` (they build one from `(tune, tuneOffset)`
   first thing today); `PrintRows.kt` and `AbcTuneOffsets.kt` keep calling `abcTune()` once per run, which is fine.
3. **Describe once.** Options:
   - (a) compute `description` only where it is used: `if (0 in shownSlots) remember(...) { describedAs(...) } else null`,
     `SongNotationBlock` taking `description: String?` (set only for slot 0, which is already the only one that sets it);
   - (b) memoize on the rendering: a `lazy` description per `NotationReading` instance keyed by `(heading, templates)`.
   Recommended: (a), the smallest change; at most one chunk per run composes slot 0.
4. Optional, if it stays small: give text-drawn runs the same treatment (`lines` built once per run on the model) so
   `tabRows`' key hashes one shared list instance.

## Tests

In `RenderSectionsTest` (commonTest), with a long ABC tune (say 60 lines of notes, so the run has many slots):

- every `SectionItem.RunSlot` of one run carries the very same `tune` instance (`assertSame` across slots), equal to
  `AbcTune(run.abcTune()!!, tuneOffset)`;
- a non-ABC run's slots carry `tune == null`;
- the existing `abcRunsOf` helper then reads `slot.tune` and its assertions (tunes per key change) still pass.

The description change lives in a Composable and has no pure seam; the slot-0-only rule is kept by `SongNotationBlock`'s
signature (a nullable description), which says so.

**Measure** (after plan 01): `./gradlew :tools:screenshots:benchmark`, push to song details for the ABC song (warm, and
reopened): frame 1 should lose most of the `joinTo` / `abcTune` / `describedAs` share (≈25% of its samples), allocation
per open should drop sharply from 65 MB, and the in-frame GC pauses with it.
