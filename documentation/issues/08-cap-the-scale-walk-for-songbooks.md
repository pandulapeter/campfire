# Skip the scale groups for a songbook, past the section count that already stops cutting its sections

**Challenged:** sound — the cutoff (`<= MAX_CUT_SECTION_COUNT` computes, over it none) matches songView/CLAUDE.md's
"a songbook (over `MAX_CUT_SECTION_COUNT`, 200 sections)" and is tested at both sides of it. `LayoutBudget` is an
`object`, so the constant goes inside it as `const val` (read as `LayoutBudget.MAX_CUT_SECTION_COUNT` or imported).

**Kind:** performance  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/LayoutBudget.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/grid/SongGridSearch.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/RenderSectionsTest.kt`

## Problem

`prepareSongLyrics` works the groups out for the whole song, with no cap:

```kotlin
// SongLyricsModel.kt:123
scaleGroups = if (shouldShowChords && suggestsScales) songScaleGroupsOf(standardSong, labels) else emptyList(),
```

It runs inside the model a page builds **in place, on the main thread**, for the page on screen and its neighbours
(`buildsModelInPlace`, "so a page never opens empty"). Its neighbour in the same function, `songChordsOf`, gives up past
`MAX_SONG_CHORDS` (48), and the layout gives up cutting sections past `MAX_CUT_SECTION_COUNT` (200, private in
`grid/SongGridSearch.kt:491`, "A file of more than this is a songbook rather than a song"). The scale walk has neither,
and is not even bounded by `LayoutBudget`, since it reads `standardSong`, not the fitted sections.

`SongScaleGroups.of` grows faster than linearly: every part tries to join each group with `fitOf(group.parts + part)`,
which flattens the group's chords and asks `ScaleChords.scalesContaining` per candidate root. Measured at HEAD on the
desktop JVM (warm, a songbook of four-section songs in eight keys, six chords a section, eight groups):

| Sections | `SongScaleGroups.of` |
|---|---|
| 50 | 9 ms |
| 100 | 27 ms |
| 200 | 38 ms |
| 400 | 92 ms |
| 800 | 341 ms |

A phone is several times slower than this desktop, and 800 three-line sections is well inside `LayoutBudget`'s 3,000
lines, so a concatenated songbook can hold its first frame for a second or more. Plans 01–02 add a score and up to four
fits per candidate, which makes each step dearer, not cheaper. No real song comes near 200 sections.

## Fix

Options:
- (1) **recommended:** no Scales section for a songbook. Move `MAX_CUT_SECTION_COUNT` out of `SongGridSearch.kt` into
  `LayoutBudget.kt` as `internal const val MAX_CUT_SECTION_COUNT = 200` with its KDoc (it is the same "songbook, not a
  song" budget; `SongGridSearch` imports it), and in `prepareSongLyrics` compute the groups only where
  `standardSong.blocks.count { it is ChordProBlock.Section } <= MAX_CUT_SECTION_COUNT`. A songbook already loses the
  multi-column layout at that count; a scale suggestion for "the whole songbook" says nothing anyway.
- (2) a chord-count cap like `MAX_SONG_CHORDS`: the cost is in parts × groups, not in distinct chords, so a count of
  chords guards the wrong thing.
- (3) compute the groups off the main thread like the searched shapes (`searchesShapes` /
  `SongLyricsModel.withSearchedShapes`, run in `RememberSongLyricsModel` on `Dispatchers.Default`): correct for any
  size, but the Scales section would then appear a moment after the page, which the UI rules forbid ("Data arriving …
  is fixed at the source so the first frame is right"), and it adds a second pass for every song to save a songbook.

The section count is the parsed song's sections, not `units.sectionStarts` (which the grid counts after cutting): close
enough for a threshold whose point is "not a song", and available before anything is laid out.

Update `songView/CLAUDE.md`'s "The Scales section" with one clause: "none for a songbook (over
`MAX_CUT_SECTION_COUNT` sections)", and the layout bullet's mention of `MAX_CUT_SECTION_COUNT` keeps pointing at the
constant by name.

## Tests

Presentation `RenderSectionsTest` (which already calls `prepareSongLyrics`):
- `a songbook has no scales section` — `prepareSongLyrics` of a song of `MAX_CUT_SECTION_COUNT + 1` chorded verses
  with `suggestsScales = true` has empty `scaleGroups`; with `MAX_CUT_SECTION_COUNT` verses it has one group.
