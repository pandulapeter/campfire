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
- A line's markup, and a comment's (stripped into `RenderSection.Comment.formatting`, after the transposition), is drawn
  as spans of its text (`formattedText`); padding under chords moves the ranges with the words and leaves the padding
  itself unformatted.
- Because chords are drawn, a chorded line's content description is its ChordPro form (`[Am]There is a…`), and a tab
  block's its lines, so screen readers read the chords.
- Each run of `ChordProLine.Tab` is one `SongTabBlock`: its columns only line up measured together.
- A staff's dashes and bar lines are strokes under its text (`TabStaffGeometry`, tested): strings run from bar to bar,
  broken by every other character, and a bar joins the strings above and under it that have one in its column. Only
  the screen draws them so far; the PDF prints the characters.
- Grids: `alignedGridBars` (`GridColumns.kt`, shared with the PDF) aligns bar lines across lines; each bar is one text,
  since only one monospaced text keeps its columns. A too-wide grid line breaks between bars (`SongGridLine`).
- A paragraph holding nothing but one tab or grid run is headed "Tab" / "Grid" (screen and PDF).

### The Scales section

- `RenderSection.Scales`, after the Chords section (else after the metadata section, else first), while chords and
  Scale suggestions are on (`ScaleSuggestions`, null otherwise); read only mode keeps it, since a scale sheet writes
  nothing; none for a songbook (over `MAX_CUT_SECTION_COUNT` sections) or a song playing over `MAX_PLAYED_CHORDS`
  chords. One fold for every song (`UserPreferences.isScaleSectionFolded`).
- Cut only between its rows of chips (a group's, then one per chord of it worth a scale), never between the lines one
  row wraps into, so a title always heads all of its chips. Each row has one slot per chip
  (`RenderSection.Scales.rowStarts`), and a chunk places only its own rows (`ScaleChipRows`), since which line a chip
  lands on depends on the width being tried.
- Its groups are `SongScaleSuggestions`' (see `chordpro/scales`): the home, then at most three moments between the
  other keys, the sections on a centre of their own, and the chords worth a scale of their own, labelled by
  `songScaleGroupsOf` (tested). The home names nothing, most of the song being played over it, and a moment names its
  sections. A chorded section no row holds (a bridge nothing fits, a fourth key past the three moments, a part in
  another key too short to be one) ends the section as a row of its own, named by its headings, with no chips and
  `song_details_scales_unheld` under the title; the home is then named by its sections too, since it no longer covers
  the song. A song with no home names each group by its sections' page headings: a kind it holds whole said once
  ("Verse"), split kinds by number ("Verse 3"), headless parts as the rest of the song. A kind is said once only where
  the group holds every chorded section of it; a heading another group (or none) also has is numbered by occurrence
  ("Verse (3)"), several headless groups are named by their first words, and a named group with headless parts says
  so.
- At most four chips a group, after the first two only common scales and the hexatonics (a rare home scale leads, and
  a vamp keeps its chord's two textbook scales, but a rare fourth chip never follows; the chord sheet keeps the full
  list), and one chip a chord (a common scale where one holds the chord), titled "Over E7" ("Over F♯7 and F♯" where
  one scale serves several) in every song; the Scales tab and the chord's sheet have the rest.
- The PDF prints it as lines of text under the diagrams, with Scale suggestions in the export's options (on at first,
  like every other option of what is printed, then as the user last left it).

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
  column, neither cut nor narrowed: the search would rerun on every pinch frame, and typing would move pieces.
  They show tempo and time changes as lines.
- **Every row shorter than the screen is followed by space down to its bottom** (`minRowPitch`, `minLastRowHeight`), so
  a rest shows one row; only where the song scrolls anyway, never in the preview.
- A continuation piece shows no header. Chorus cards are drawn behind the chunks, one per piece. The page dividers
  are drawn by the layout over both, at the main pass's tops, not composed.
- **Motion** (`SectionMotion`): a single change springs with `animateBounds`; continuous changes (pinch, resize, title
  row collapse, metronome panel) follow directly, only a new grid's jump glided (`SectionGlides`). The playing controls,
  the chord diagrams, and the scale chips travel to a new arrangement of their rows during those too (`PlacementMotion`),
  except a pinch, which resizes them every frame (`isTextScaling`).
- `animateBounds` measures with `Constraints.fixed`, which cannot hold 262,143 px: animated sections are measured no
  taller than half that (`maxAnimatedSectionHeight`).
- Each section is a traversal group indexed in song order, or screen readers read across the columns.
