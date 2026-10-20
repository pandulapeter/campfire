<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Chorded parts no scale row holds are named, not silently covered by the home

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroups.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongScaleGroupsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/SongScalesSection.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`

Written at HEAD `0a0babf27`. Lane U (cut after lanes G and S have merged). Presentation only: the engine's
`SongScaleGroups.playsChords(block)` is public already, and nothing in `chordpro` changes.

## Problem

`songScaleGroupsOf` returns one untitled row whenever the engine returns one group:

```kotlin
    // A song one group fits names nothing, and neither does a home, most of the song being played over it.
    fun untitled(index: Int) = SongScaleGroup(
        label = null,
        …
    )
    if (groups.size == 1) return listOf(untitled(0))
```

and a home row is untitled in every song (`if (group.isHome) return@mapIndexed untitled(index)`). The engine drops
chorded sections it cannot answer: a bridge no scale holds two thirds of (`SongScaleGroups`: "one no home scale holds
two thirds of is left out"), and, in `SongScaleSuggestions.rowsOf`, every group that is neither the home nor one of the
three moments chosen (a fourth key). The page then shows "C major, C major pentatonic, …" with no section named, and the
player believes it covers the bridge. Probe at HEAD, `{key: C}` verse `C F G C …`, chorus `F G C Am …`, bridge
`C F# Bb E Ab D`, chorus recalled:

```
## 09 Bridge no scale holds (C)
  [HOME] [Verse, Chorus, Recall] C: major, major_pentatonic, major_hexatonic, bebop_dominant (+1)
```

The bridge is in no row, and the row has no title. The same happens to the fourth key of the test `a song gets at most
three moments, however many keys it visits` (its `Outro` in A♭ is shown nowhere, the home untitled), and, until lane G's
plans land, to a chorus modulated beyond what the home holds.

## Fix

In `songScaleGroupsOf`, after `val numbered …` and `val groups …`:

1. Compute the chorded blocks no shown group holds, by identity as the file compares blocks everywhere:

   ```kotlin
   val missing = numbered.filter(SongScaleGroups::playsChords).filter { block -> groups.none { group -> group.blocks.any { it === block } } }
   ```

   `SongScaleSuggestions.of` returns the very block instances of the song it is given (`song.copy(blocks = numbered)`),
   so `===` against `numbered` is right.

2. Where `missing` is empty, nothing changes. Where it is not:
   - the single-group shortcut (`if (groups.size == 1) return listOf(untitled(0))`) and the home shortcut
     (`if (group.isHome) return@mapIndexed untitled(index)`) are skipped, so the home row is named by its sections like
     any other group and carries `isHome = false` (the viewer titles a row only where `!isHome`; the KDoc of
     `SongScaleGroup.isHome` says it is "what most of the song is played over, which no title names", which is no longer
     true of it);
   - one more row is appended for the missing blocks, named by their headings through the same rules (a kind said once
     where it holds every chorded section of it, `numberedAmong(played, blocks)` otherwise, `opening()` for several
     headless groups, `hasUnnamed`), with `scales = emptyList()`, `exceptions = emptyList()`, `rootName` the first
     group's (never read for a row without chips), `isHome = false`.

   To share the naming, lift the body of the `mapIndexed` lambda that computes `names`, `label`, `joinsSeveral`,
   `hasUnnamed`, and `opening` into a local function `rowOf(blocks: List<ChordProBlock>, rootName: String,
   scales: List<Scale>, exceptions: List<SongScaleException>)`, called for each group and once for `missing`;
   `headers`/`headless` then count the missing row as one more group (so two headless groups are told apart by their
   first words as today).

3. The row's text. A row with no chips says why, with a new string in both locales, in the group commented
   `<!-- Over the scales for one chord the scales above it leave out, e.g. "Over E7". -->` of the Scales section:

   ```xml
   <!-- Under the heading of the parts of a song that fit none of the scales shown for it, where the row has no scale chips. -->
   <string name="song_details_scales_unheld">No scale above holds these chords</string>
   ```

   ```xml
   <string name="song_details_scales_unheld">A fenti skálák egyike sem illik ezekhez az akkordokhoz</string>
   ```

   "No scale above" is true both of a bridge nothing holds and of a fourth key that fell past the moment budget (whose
   chords a scale does hold, just none that is shown).

4. The viewer. `ScaleChipRows` draws nothing for a row with no chips: `greedyRowStarts` returns an empty array for no
   cells, `slotRows(0, …)` is empty, the title is not measured, and the height is 0. `RenderSection.Scales.rowStarts`
   already gives such a row one slot (`row.scalesOf(groups).size.coerceAtLeast(1)`), so the only change is in
   `SongScalesSection`, inside `section.rows.forEachIndexed`, where today every row calls `ScaleChipRows(…)`: where
   `row.scalesOf(section.groups).isEmpty()`, compose instead a `Column(modifier = Modifier.fillMaxWidth().padding(bottom =
   trailingGap))` with `ScaleChipsTitle(title = title!!, textAlign = TextAlign.Start)` (`title` is never null for the
   first slot of a group row: `SongScaleGroup.title(other)` always returns a string) and, `SCALE_CHIPS_TITLE_GAP` below
   it, a `Text` of `stringResource(Res.string.song_details_scales_unheld)` in `MaterialTheme.typography.bodyMedium` and
   `MaterialTheme.colorScheme.onSurfaceVariant`, the title's colour. Compute `trailingGap` once above the branch (the
   `when` that is passed to `ScaleChipRows` today). A private Composable `UnheldScalesRow` in `SongScalesSection.kt`
   keeps the main body flat; it takes `modifier`, `title`, and `text`. Nothing else in the layout changes: the row has
   one slot, so it is cut, headed, and animated as a one-line row of chips is.

Expected: the probe chart above shows a row "Verse, Chorus" with the C major chips and a row "Bridge" reading "No scale
above holds these chords"; the five-key test song shows "Verse" (C major), "Bridge", "Solo", "Middle" as today and
"Outro" with the text. A song one row fits whole stays untitled, as every test in the file pins.

## Tests

In `SongScaleGroupsTest` (presentation), with its `groups(text)` helper, `verse`, `chorus`, and `labels`:

- `` `a part no scale holds is named, and so is the rest of the song` ``:
  `groups("{key: C}\n" + verse + chorus + "{start_of_bridge}\n[C]x [F#]x [Bb]x [E]x [Ab]x [D]x\n{end_of_bridge}\n" + chorus)`
  → `map { it.label } == listOf("Verse, Chorus", "Bridge")`, `last().scales.isEmpty()`, `last().exceptions.isEmpty()`,
  `none { it.isHome }`, and `first().scales.isNotEmpty()`.
- `` `a key past the moment budget is named with no scales` ``: five sections of one key each, as the engine's test
  `a song gets at most three moments, however many keys it visits` builds them: `{start_of_verse}` of
  `C F G C C F G C C F G C C F G C`, `{start_of_bridge}` of `D G A D`, `{start_of_solo}` of `E A B E`,
  `{start_of_verse: Middle}` of `Bb Eb F Bb` (a label that is not the kind's name takes no part in the numbering, so
  the first verse stays "Verse"), and `{start_of_outro}` of `Ab Db Eb Ab` → five rows, `map { it.label } ==
  listOf("Verse", "Bridge", "Solo", "Middle", "Outro")`, `last().scales.isEmpty()`, and every other row's
  `scales.isNotEmpty()`.
- The existing `a song one scale fits is one group named nothing` and `a song with a home names only its moments`
  (every chorded section held) stay and must pass.

`CLAUDE.md` (`ui/songView`, "The Scales section"): after "The home names nothing, most of the song being played over
it, and a moment names its sections." add "A chorded section no row holds (a bridge nothing fits, a fourth key past the
three moments) ends the section as a row of its own, named by its headings, with no chips and
`song_details_scales_unheld` under the title; the home is then named by its sections too, since it no longer covers the
song."
