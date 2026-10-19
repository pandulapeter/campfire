<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/songView

The song renderer (`SongLyrics` and its tree), drawn by the song details screen and the editor's preview. `model/`
builds what it draws, `grid/` places it.

### Rendering

- Tabs and grids are monospace: columns of characters that must line up. **Nothing on the page scrolls sideways.**
- Lyrics take their direction from content (`TextDirection.Content`), so chords of an RTL line hang leftwards.
- Chords are drawn over the lyrics, not composed. Lyrics under a wider chord are padded with non-breaking spaces
  (`padLyricsToFitChords`); a padded line may break only between two chords at a word boundary, so no chord is pulled
  back over the one before.
- Because chords are drawn, a chorded line's content description is its ChordPro form (`[Am]There is a…`), and a tab
  block's its lines, so screen readers read the chords.
- Each run of `ChordProLine.Tab` is one `SongTabBlock`: its columns only line up measured together.
- Grids: `alignedGridBars` (`GridColumns.kt`, shared with the PDF) aligns bar lines across lines; each bar is one text,
  since only one monospaced text keeps its columns. A too-wide grid line breaks between bars (`SongGridLine`).
- A paragraph holding nothing but one tab or grid run is headed "Tab" / "Grid" (screen and PDF).

### The Scales section

- `RenderSection.Scales`, after the Chords section (else the first section), while chords and Scale suggestions are on
  (`ScaleSuggestions`, null otherwise); read only mode keeps it, since a scale sheet writes nothing. One fold for every
  song (`UserPreferences.isScaleSectionFolded`).
- Cut between its rows of chips like the Chords section between its rows of diagrams: each group has one slot per chip
  (`RenderSection.Scales.groupStarts`), and every chunk composes its group's chips but places only its own rows
  (`ScaleChipRows`), since which row a chip lands in depends on the width being tried.
- Its groups are the catalogue's (see `chordpro/scales`), labelled by `songScaleGroupsOf` (tested): one group names
  nothing; otherwise each group lists its sections' page headings, a kind it holds whole said once ("Verse"), split
  kinds by number ("Verse 3"), headless parts as the rest of the song. At most five chips a group.

### Folding (keys in `model/`)

- Every section with a header folds, and so does every tab or grid run inside one (`FoldedRuns`). Saved per song in
  `UserPreferences.foldedSections`: it is the reader's, so it never reaches a setlist, an export, or a sync run; a
  rename carries it, a deletion drops it.
- A plain paragraph, and the rest of a section a chorus recall cut, get a chevron-only pill (`UNNAMED_SECTION_HEADER`),
  named for screen readers by `firstLyric`. The editor's preview folds nothing but draws that pill empty, so it lays out
  like the details screen. A chorus has no pill and folds from its whole title row.
- A fold re-measures only its own section (`UnitContent.foldedKeys`). An unfolding run animates only opacity: a height
  still animating would be measured halfway.

### Layout (`SongSectionsLayout`)

- **A fold holds the columns in place for 900 ms** (`ColumnCenteringHold`) before re-centering, or the header just
  tapped would move and a second tap miss.
- The editor's preview and a songbook (over `MAX_CUT_SECTION_COUNT`, 200 sections) keep a plain, unmeasured single
  column, neither cut, narrowed, nor fitted: the search would rerun on every pinch frame, and typing would move pieces.
  They show tempo and time changes as lines.
- **Every row shorter than the screen is followed by space down to its bottom** (`minRowPitch`, `minLastRowHeight`), so
  a rest shows one row; only where the song scrolls anyway, never in the preview.
- A continuation piece shows no header. Chorus cards are drawn behind the chunks, one per piece. The page dividers
  are drawn by the layout over both, at the main pass's tops, not composed.
- **Motion** (`SectionMotion`): a single change springs with `animateBounds`; continuous changes (pinch, resize, title
  row collapse, metronome panel) follow directly, only a new grid's jump glided (`SectionGlides`).
- `animateBounds` measures with `Constraints.fixed`, which cannot hold 262,143 px: animated sections are measured no
  taller than half that (`maxAnimatedSectionHeight`).
- Each section is a traversal group indexed in song order, or screen readers read across the columns.
