<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Measure again only the chunks that draw a tune when a rendering arrives, not the whole song

**Challenged:** amended — the token carries plan 20's `minWidth` too (the chunk's minimum intrinsic width is cached in
its `SectionSizes`, and the narrowest page follows a window narrower than the narrowest column); it relies on
`NotationSystemPaint` staying a plain class (identity equality and hash: lane D's plans 06 and 07 change its fields,
never its kind); and the lane order puts it after plans 20, 21, and 14.

**Kind:** performance  ·  **Severity:** medium-high (measured)  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongLyrics.kt`,
`…/ui/songView/model/SongUnits.kt` (`UnitContent`), a new `…/ui/songView/model/UnitContents.kt` (the list of
`UnitContent` moved out of `SongLyrics`), `…/ui/songView/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/SectionSizesPoolTest.kt`,
a new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/UnitContentsTest.kt`

Lane N, after plans 12, 03, 05, 20, 21, and 14 (it calls `SongNotations` as plan 12 leaves it, and measures by the
`NotationRun.minWidth` plan 20 adds).

## Problem

`SongLyrics` renews the pool of section sizes whenever `notations` changes:

```kotlin
val sectionSizesPool = remember(fontScale, density, lyricsStyle, headerStyle, defaultLabels, notations) { SectionSizesPool<UnitContent>() }
```

`rememberSongNotations` returns a new `SongNotations` for every new `rendered` (every arrival, every swap of a stand-in),
so every unit of the song — lyrics included — is measured again from nothing, though only the chunks that draw a tune
changed height. Measured on song C: **each arriving rendering cost an extra 17–33 ms grid decide on Main**, and a
song's first open renders twice (once for the narrowest column, once for the placed one), so it pays this twice.
The comment above it and `songView/CLAUDE.md` document it ("the sizes pool renewed when a rendering arrives").

## Fix

1. Drop `notations` from the pool's `remember` keys.
2. Give `UnitContent` one more field, a token of what the unit draws of a tune, null for a unit that draws none, so
   the pool (which reuses sizes by content) hands fresh sizes to exactly the units whose tune drawing changed:
   for each `SectionItem.RunSlot` of `FoldableKind.VERBATIM` whose run is an ABC tune, the `NotationRun` the slot would
   draw (`notations.of(…)`, with plan 12's signature) reduced to what `SongNotationBlock`'s measure reads — its
   `systems` (compared by the list, whose elements are compared by identity, so one rendering is one value — and hashed
   by identity too: `NotationSystemPaint` must stay a plain class, never a data class, or every pool lookup would hash
   whole drawings), `unitSize`, `maxWidth`, and `minWidth` (plan 20: the minimum intrinsic width a chunk caches), or "drawn as text" where `of` returns null — and, for the chunk holding a run's or a
   whole-tune section's heading, whether `partPickerOf(…)` gives a picker (the heading's height). Use a small data class
   (e.g. `NotationMeasureToken`) or a list of them for a unit with several tune slots.
3. Build the `UnitContent` list in a function of its own in the new `UnitContents.kt`, taking the units, sections,
   folded sections, and `notations`, and call it from `SongLyrics`'s `sectionMeasurements` remember, now keyed by
   `notations` too (a new `SectionMeasurements` per rendering, which re-decides the grid — needed, the tune's height
   changed — but finds every other unit's heights in the pool). `SongLyrics` (~300 lines, past the 120-line limit)
   must end shorter or as long: the move takes the inline `units.itemRanges.mapIndexed { … UnitContent(…) }` out.
4. Update the comment over the pool ("The notation arriving measures everything again, once.") and the line in
   `songView/CLAUDE.md` ("…the sizes pool renewed when a rendering arrives") to say a rendering arriving measures again
   only the chunks that draw a tune.

Must not break: **a tune's slots answer their new height on the very pass the rendering arrives** — the token must
change whenever anything `SongNotationBlock` measures by changes (a stand-in's systems included, which `of` already
returns); the arrival is still placed at once, not glided (`SongNotations.arrival`, `NotationArrivalTest`); folds still
re-measure only their section (`foldedKeys` stays in `UnitContent`); a pinch still renews the pool through `fontScale`.

## Tests

- `UnitContentsTest`: a section of lyrics and a section with a tune give a null token for the lyric units and a
  non-null one for the tune's; a second `SongNotations` with the same rendering gives equal tokens, one with a new
  rendering for the tune changes only the tune units' tokens. (Build `SongNotations` as `NotationArrivalTest` /
  `NotationFixtures` do.)
- `SectionSizesPoolTest`: two lists differing only in one unit's token keep every other unit's `SectionSizes` instance.
- `SheetMusicReopenTest` (`tools/screenshots`) still passes, its "first rendering moves the song under it in one step"
  above all.
- Confirm the gain with the sweep's measurement method (untracked `:tools:screenshots` test with temporary log lines,
  deleted afterwards): on song C's first open, the intrinsic measurements after each rendering lands should only be the
  tune units', and the extra grid decide well under the 17–33 ms measured. Lane report only.
