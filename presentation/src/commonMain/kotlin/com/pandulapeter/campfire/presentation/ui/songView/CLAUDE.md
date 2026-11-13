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

The song renderer, drawn by song details and the editor's preview; `model/` builds what it draws, `grid/` places it.

### Rendering

- Tabs and grids are columns of characters that must line up. **Nothing on the page scrolls sideways.**
- **A song has one direction** (`SongLyricsModel.textDirection`), whatever the UI's: the body is laid out in it and
  every lyric line is a paragraph of it, so chords follow one another leftwards in an RTL song. Each chord hangs from
  its character in its run's direction: a Latin word in a Hebrew line has its chord right of its first character, its
  padding closed in by direction marks so the gap opens on that side. The first section, the step buttons' inset, and
  the interface's sentences keep the screen's direction.
- **Music is always LTR** (chord names, tab, grid, preformatted runs, engraved notation), drawn from the song's start
  edge as one block: in an RTL song a run moves right by the line's width less its widest row over all its slots
  (`runShiftAtLineEnd`, as the PDF's `atLineEnd`), so a run that fills or wraps barely moves and its slots keep their
  columns. Only the drawing moves; sizes and intrinsics are unchanged.
- A chord with a `{chordcolour}`, and a lyrics line's words with a `{textcolour}`, are drawn in it, moved towards black
  or white only as far as reading on the theme's surface needs (`writtenColorOn`); the PDF, a grayscale image, prints
  them black.
- Chords are drawn over the lyrics, not composed. Lyrics under a wider chord are padded with non-breaking spaces; a
  padded line may break only between two chords at a word boundary (no chord pulled back over the one before), and never
  before a chord row's mark (`x4`, `|`), which would lose what it repeats or ends.
- Markup is drawn as spans (a comment's after the transposition); padding moves the ranges with the words and stays
  unformatted.
- Because chords are drawn, a chorded line's content description is its ChordPro form (`[Am]There is a…`), a tab block's
  its lines; an engraved tune's is its title, its chords as the page names them, and its words, never its ABC source,
  led by the heading only where the tune names itself nothing.
- Each run of tab lines is one block, since its columns only line up measured together; so is each verbatim run, always
  preformatted, since a tied ABC line full of dashes would pass for a staff.
- An `abc` run is engraved once its rendering arrives (`ui/notation`), the text until then or if it fails. `SongLyrics`
  is handed its width at rest (`availableWidth`), whose columns tunes are engraved for, so a tune rendered before is
  notation on its first frame and a push hiding the rail engraves once. Headed "Sheet music" (screen and PDF): ABC is
  how it is written, which a reader need not know, and it may hold chords and several voices. It keeps the text's slots,
  one for each line and each 12 characters of every line (`runSlotCount`, more than it is ever engraved as): slot *n*
  draws staff system *n*, the last one the rest, which a column cannot cut; each answers its height for any width at
  once (scaled down in a narrower column until engraved for it); a rendering arriving measures again only the chunks
  that draw a tune (what they draw is part of their `UnitContent`).
- A staff's dashes and bar lines are strokes under its text (`TabStaffGeometry`, tested; the PDF rules the same):
  strings run bar to bar, broken by every other character; a bar joins the strings above and under it that have one in
  its column.
- Tablature is engraved at abc2svg's tab staff sizes, so it reads as one engraving with the sheet music; its monospaced
  columns only say where things go: frets in the lyrics' face's bold tabular figures, centred on their string and
  column; other lines from the column each word starts at, moved right only where it would touch the word before.
  Preformatted runs stay monospaced. A line of chord names in a tab run is drawn as the page's chords.
- Grids: bar lines align across lines (`alignedGridBars`, shared with the PDF); each bar is one text, since only one
  monospaced text keeps its columns. A too-wide grid line breaks between bars.
- A paragraph holding only one tab or grid run is headed "Tab" / "Grid" (screen and PDF).

### The Scales section

- While chords and Scale suggestions are on; kept in read only mode (a scale sheet writes nothing); none for a songbook
  or a song playing over `MAX_PLAYED_CHORDS` chords. Folded per song under `SCALES_FOLD_KEY`.
- Cut only between rows of chips, never between the lines one row wraps into, so a title heads all its chips; a chunk
  places only its own rows, since which line a chip lands on depends on the width being tried.
- How groups are named and which chips they keep (at most four a group, rarities only as the home scale) is pinned by
  `SongScaleGroupsTest`; the home names nothing, since most of the song is played over it, and a chorded part no scale
  holds is still named, with no chips. The chord sheet keeps the full list.
- The PDF prints it as text under the diagrams; its export option starts on, then as last left.

### Folding (keys in `model/`)

- Every section with a header folds, and every tab or grid run in one but a lone unnamed grid row. Saved per song in
  `UserPreferences.foldedSections`, the reader's: never in a setlist, an export, or sync; a rename carries it.
- A plain paragraph, and the rest of a section a chorus recall cut, get a chevron-only pill, named for screen readers by
  its first lyric. The preview folds nothing but draws that pill, so it lays out like details. A chorus has no pill and
  folds from its whole title row.
- A fold re-measures only its section. An unfolding run animates only opacity: a height still animating would be
  measured halfway.

### Layout (`SongSectionsLayout`)

- **A fold holds the columns in place for 900 ms** before re-centering, or the header just tapped would move and a
  second tap miss.
- A song scrolling by itself is an unmeasured single column cut a chunk a line, so its rows name every line
  (`screens/songDetails/autoScroll`); a staff system plays two bars a line, a tune's title none.
- The preview and a songbook (over `MAX_CUT_SECTION_COUNT` sections) keep a plain unmeasured single column, neither cut
  nor narrowed: the search would rerun every pinch frame, and typing would move pieces. They show tempo and time changes
  as lines.
- **Every row shorter than the screen is followed by space down to its bottom**, so a rest shows one row; only where the
  song scrolls anyway, never in the preview.
- **A strip** (turned by column, `grid/`) is laid out as wide as its columns and scrolls sideways: the scroll gives it
  no width, so it is handed the viewport's (`viewportWidth`). Its rows are its column starts from the song's start edge
  (`SongRows.isAcross`).
- A continuation piece shows no header. Chorus cards are drawn behind the chunks, one per piece; page dividers are drawn
  by the layout, not composed.
- **Motion**: a single change glides every section, card, and divider from its old place to its new (`SectionGlides`);
  continuous changes (pinch, resize, title row collapse, metronome panel) follow directly, only a new grid's jump
  glided. **Only places are animated, never sizes** (no `animateBounds`): content is arranged once at its new size, not
  re-wrapped every frame. One spring for all (`SongMotionSpec`). Controls, diagrams, and scale chips travel to a new row
  arrangement too, except during a pinch. **A tune's first rendering is data arriving**: that pass places everything at
  once, never springing the song during an enter transition.
- **A section is keyed by what it is, not what it shows** (`sectionKeysOf`), so it glides rather than being composed
  afresh; it is followed by its first baseline, so words a chord row grows over glide from where they were. A chunk or
  control coming with any other single change fades in where it lands.
- **A change of how the song is shown is staged** (`SongChange`, `songDetails/StagedSongFrame.kt`): what it takes away
  fades out while the page is held, then the new page glides, then what it brings fades in. All at once reads as chaos.
- Each section is a traversal group indexed in song order, or screen readers read across the columns.
