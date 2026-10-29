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

- Tabs and grids are written in columns of characters that must line up. **Nothing on the page scrolls sideways.**
- **A song has one direction** (`SongLyricsModel.textDirection`, from `:chordpro`'s `ChordProSong.textDirection`),
  whatever the UI's: `SongLyrics` provides it as `LocalLayoutDirection` over the body (columns, sections, chord
  diagrams) and every lyric line is a paragraph of it (`TextDirection.Rtl` / `Ltr`), so chords follow one another
  leftwards over every line of an RTL song. Each hangs from its character in its run's direction (`chordLeft`): a Latin
  word or a number in a Hebrew line has its chord to the right of its first character, and its padding closed in by
  direction marks (`bidiMarkFor`) so the gap opens on that side. The first section keeps the screen's direction (`screenLayoutDirection`), as do the step
  buttons' inset (`isInsetAtLeft`) and the interface's sentences (`sentenceStyle`, `TextDirection.Content`).
- **Music is always LTR**: chord names and annotations, tab, grid, and preformatted runs (laid out under an LTR
  `LocalLayoutDirection`), and engraved notation (`NotationPainting`), drawn from the song's start edge as one block:
  in an RTL song a run moves right by the line's width less its widest row over all its slots (`runShiftAtLineEnd`,
  `atLineEnd` for grids), as the PDF's `atLineEnd` does, so a run that fills or wraps barely moves and its slots stay
  in their columns. Only the drawing moves; sizes and intrinsics are unchanged.
- Chords are drawn over the lyrics, not composed. Lyrics under a wider chord are padded with non-breaking spaces
  (`padLyricsToFitChords`); a padded line may break only between two chords at a word boundary, so no chord is pulled
  back over the one before, and never before a chord row's mark (`x4`, `|`), which would lose what it repeats or ends.
- A line's markup, and a comment's (stripped into `RenderSection.Comment.formatting`, after the transposition), is drawn
  as spans of its text (`formattedText`); padding under chords moves the ranges with the words and leaves the padding
  itself unformatted.
- Because chords are drawn, a chorded line's content description is its ChordPro form (`[Am]There is a…`), and a tab
  block's its lines, so screen readers read the chords; an engraved tune's is its title, its chords as the page names
  them, and its words (`NotationReading`), never its ABC source, led by the heading only where the tune names itself
  nothing.
- Each run of `ChordProLine.Tab` is one `SongTabBlock`: its columns only line up measured together. So is each run of
  `ChordProLine.Verbatim`, always preformatted (`isPreformatted`), since a tied ABC line full of dashes would pass for a
  staff.
- An `abc` run is engraved once its rendering arrives (`SongNotationBlock`, see `ui/notation`), and is the text above
  until then or if it fails. `SongLyrics` is handed its width at rest (`availableWidth`), which the tunes are engraved
  for, so a tune rendered before is notation on its first frame and a push hiding the rail engraves once. It is headed "Sheet music", on screen and in the PDF: ABC is how it is written, which a
  reader need not know, and it may hold chords and several voices (a piano part), not only a melody. It keeps the
  text's slots: slot *n* draws staff system *n*, the last one the rest, so the section is cut between systems; each
  slot answers its height for any width at once (the systems at the text size's unit, scaled down to a narrower
  column), and the sizes pool is renewed when a rendering arrives.
- A staff's dashes and bar lines are strokes under its text (`TabStaffGeometry`, tested): strings run from bar to bar,
  broken by every other character, and a bar joins the strings above and under it that have one in its column. The
  PDF rules the same strokes (`PrintTabEngraver`).
- Tablature is engraved (`TabStaffLines`, screen and PDF) at abc2svg's tab staff sizes (`TabEngraving`), so it reads
  as one engraving with the sheet music; its monospaced columns only say where things go: frets in the lyrics' face's
  bold tabular figures (string names regular), centred on their string and columns; its other lines in the lyrics'
  face from the column each word starts at, moved right only where it would touch the word before it. Preformatted runs stay monospaced, at the lyrics' spacing. A line of chord
  names in a tab run, engraved or not, is drawn as the page's chords (`isChordLine`).
- Grids: `alignedGridBars` (`GridColumns.kt`, shared with the PDF) aligns bar lines across lines; each bar is one text,
  since only one monospaced text keeps its columns. A too-wide grid line breaks between bars (`SongGridLine`).
- A paragraph holding nothing but one tab or grid run is headed "Tab" / "Grid" (screen and PDF).

### The Scales section

- `RenderSection.Scales`, after the Chords section (else after the metadata section, else first), while chords and
  Scale suggestions are on (`ScaleSuggestions`, null otherwise); read only mode keeps it, since a scale sheet writes
  nothing; none for a songbook (over `MAX_CUT_SECTION_COUNT` sections) or a song playing over `MAX_PLAYED_CHORDS`
  chords. Folded per song, under `SCALES_FOLD_KEY` in `UserPreferences.foldedSections`.
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
- A song that scrolls by itself is an unmeasured single column too, but cut a chunk a line so its rows name every line
  (`SongRows.playedLines`, see `screens/songDetails/autoScroll`); an engraved staff system plays its bars, two a line,
  a tune's title none (`SongUnits.of`'s `runSlotLines`, from `SongNotations.systemLines`).
- The editor's preview and a songbook (over `MAX_CUT_SECTION_COUNT`, 200 sections) keep a plain, unmeasured single
  column, neither cut nor narrowed: the search would rerun on every pinch frame, and typing would move pieces.
  They show tempo and time changes as lines.
- **Every row shorter than the screen is followed by space down to its bottom** (`minRowPitch`, `minLastRowHeight`), so
  a rest shows one row; only where the song scrolls anyway, never in the preview.
- A continuation piece shows no header. Chorus cards are drawn behind the chunks, one per piece. The page dividers
  are drawn by the layout over both, at the main pass's tops, not composed.
- **Motion** (`SectionMotion`): a single change glides every section, card, and row divider that moved from its old
  place to its new one (`SectionGlides`); continuous changes (pinch, resize, title row collapse, metronome panel) follow
  directly, only a new grid's jump glided. **Only places are animated, never sizes** (no `animateBounds`): a section is
  laid out at its new size at once, so its content is arranged once rather than at every size on the way, which
  re-wrapped chips and controls on every frame. Everything moves on one spring (`SongMotionSpec`). The playing controls,
  the chord diagrams, and the scale chips travel to a new arrangement of their rows during those too (`PlacementMotion`),
  except a pinch, which resizes them every frame (`isTextScaling`). **A tune's first rendering is data arriving**: the
  pass that lays it out places every section, card, divider, and row at once (`SectionGlides.follow`'s `arrival`, and
  `SongSectionChildren`'s `placesAtOnce` for that composition), never springing the song during an enter transition.
- **A section is keyed by what it is, not what it shows** (`sectionKeysOf`: a lines section by its fold key), so it
  glides rather than being composed afresh; it is followed by its first baseline, so words a chord row grows or shrinks
  over glide from where they were. A chunk or a first-section control that comes with any other single change fades in
  where it lands (`fadingIn`, `GroupedRows`).
- **A change of how the song is shown is staged** (`SongChange`, driven by `songDetails/StagedSongFrame.kt`): what it
  takes away fades out while the page before it is held (`leavesWith`, the chords drawn over the lyrics, the controls,
  the pill), then the new page glides into place, then what it brings fades in. Leaving, moving, and arriving at once
  read as chaos.
- Each section is a traversal group indexed in song order, or screen readers read across the columns.
