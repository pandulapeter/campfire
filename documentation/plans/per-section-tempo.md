<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Tempo and time signature changes inside a song — implementation plan

Written 2026-10-06 against `fd4294459`. The metronome plan left this out on purpose ("the app does not know where in
the song the band is"). Paging is what tells it: a change starts a page of its own, so the page being read names the
tempo and the time signature the click plays.

## 1. What is being built

1. **A song may carry `{tempo}` and `{time}` anywhere in its body**, each one a change from where it stands. The header's
   are still the song's own, and still what the lists, the app bar's first reading and the Song defaults sheet show.
2. **The editor offers Tempo and Time signature again and again**, into the header first and at the caret after that,
   and Prettify keeps both kinds of line tidy.
3. **On the song details screen a change starts a new page**, headed by one line naming the tempo and the time
   signature from there on. A click that is playing follows the page being read.
4. **With the Metronome feature switched off none of it exists**: no line, no forced page, the song laid out as today.

### What is already there

- The parser already treats a later `{key}`, `{tempo}` or `{time}` as "a change mid-song" and reads past it
  (`ChordProParser.ChangeableValue`); `ChordProMetadataFields.set` already keeps such lines and leaves an empty header
  line where a cleared value would otherwise be taken from the body; the highlighter already marks an unreadable one.
- `ChordProBlock.Transpose` is the precedent for a directive that becomes a block and cuts the section it stands in.
- `SongRows.stepSections` already names every stop of a page by the section it starts with.
- `Metronome.update(pattern, restartBar = true)` is what paging to another song already does.

### Defaults this plan assumes — veto any of them before the work starts

1. **Tempo and time only.** A `{key}` in the body stays what it is today (read past, button offered once). The request's
   "key signature" is read as the time signature.
2. **A change takes effect where it is written.** Inside a section it cuts the section there, and the rest of it starts
   the new page as a continuation (chevron pill, no second heading), the way a `{chorus}` recall cuts one. It is not
   snapped to the section's start.
3. **A song whose header names no tempo still takes its first body `{tempo}` as its own** (today's rule, which reads a
   file that keeps its metadata at the bottom), so that line is no change. Prettify moves it into the header (§3.2).
4. **A `{chorus}` recall is played in whatever is in force where it is recalled.** A change written inside the chorus
   is not repeated by the recall, as a `{transpose}` is not. To change it for a recall, write the lines before `{chorus}`.
5. **An override scales the changes.** The stepper, tap tempo, a setlist's entry and the library's override still hold
   one number, the song's opening tempo. A later tempo keeps its ratio to the file's opening one (file 120 → 60, played
   at 110 → 55), rounded and held to 30–300. Where the file names no opening tempo there is no ratio, and a later
   tempo is played as written. Nothing new is stored: no setlist field, no synced preference.
6. **The line on the page is read only** and always names both values, as the click plays them: `90 BPM · 3/4`, the
   time signature 4/4 where the song never named one. No stepper on it.
7. **The click moves at the moment the page turn starts, from beat one**, as it does for the next song of a setlist.
8. **A song that fits one screen is still cut into pages by a change.** That is the cost of the page being the signal.
9. **The PDF prints the line in place and breaks nothing**; the editor's preview shows it in place and is never paged.

## 2. `:chordpro` — the model and the parser

### 2.1 `ChordProBlock.Timing`

```kotlin
/**
 * `{tempo}` or `{time}` after the song has begun: from here on it is played at [tempo] in [time], both as the file
 * writes them and both complete, the one that did not change carried over, null where the song never named one.
 */
data class Timing(val tempo: String?, val time: String?) : ChordProBlock
```

Complete rather than a delta, so every reader (the page, the PDF, the click) takes one block and needs no walk back.
Add it to `gradle/compose-stability.conf`.

### 2.2 `ChordProParser`

- A small `Timing` tracker next to `Transposition`, fed from `handleDirective` for `tempo`, `time` and their `{meta: …}`
  forms **before** `metadata.consume`, which still runs (it decides the song's own values).
- A directive is a **change** when it stands in the body, is readable (`ChordProTempo.parse` / `ChordProTime.parse`),
  is not the line `ChangeableValue` takes the song's own value from (default 3), and its parsed value differs from the
  one in force — compared as numbers, so `{time: C}` after `4/4` is nothing. An empty or unreadable one is nothing.
- A change is emitted with `section.addBlock(ChordProBlock.Timing(…))`, so it cuts a running section exactly as
  `Transpose` does. **A second change with no line of the song between it and the first replaces the first** (a
  `{tempo}` directly followed by a `{time}` is one block): check `blocks.lastOrNull() is Timing` after the cut logic.
- `withChorusesRecalled`: filter `Timing` out of a recalled chorus and add it to the `takeWhile` of `commentsEnding`,
  next to `Transpose`. `recallWeight` needs nothing (`else` branch).
- `scan` / `summarize` / `ChordProSummaryCache` are untouched: the library still reads the opening values only.

### 2.3 `ChordProSerializer`, `ChordProTransposer`, `ChordProNotation`

- The serializer keeps the running tempo and time (starting from the metadata) and writes `{tempo: …}` and/or
  `{time: …}` for whichever a block changes, inside the environment where the block cut one, as for `Transpose`.
  `parse(serialize(parse(x))) == parse(x)` has to hold for a change between sections, inside one, inside a tab, and
  for a headerless song.
- `rewriteChords` and every other `when` over `ChordProBlock` pass it through; the compiler lists them all.

### Tests (`ChordProParserTest`, `ChordProSerializerTest`)

Change between sections; inside an explicit section, an implicit paragraph, a tab; `{tempo}` + `{time}` merged; a
blank line between the two; unchanged value (`C` vs `4/4`, `120` vs `120 bpm`) emits nothing; unreadable and empty
values; header with an empty `{tempo}` and a body change (opens at none, changes later); headerless song (first body
value is the song's, a second is a change); trailing change after the last line; a change inside a chorus and its
recall; `{meta: tempo 90}`; a directive with a selector suffix.

## 3. `:chordpro` — writing the lines

### 3.1 `ChordProHeader` and `ChordProSyntax.metadataInsertionIndex`

- `ChordProHeader.changeableMetadata = setOf("tempo", "time")`: what a song may say again further down.
- **Bug to fix on the way**: `metadataInsertionIndex` puts a new line "after the last directive of its own kind,
  wherever that is", which for a song with a body `{tempo}` and no header one is the middle of the song. For the
  changeable kinds only lines before `bodyStartIndex` count.
- New `ChordProHeader.insertChangeable(text, name, caretOffset, prefix, suffix): Insertion`, pure and tested:
  - the header has no line of the kind → today's `insert` (the main value goes to the top first);
  - the header has an empty line of it → nothing is inserted, the caret goes into that line;
  - the header names a value and the caret is in the body → a line of its own at the **start of the caret's line**
    (never splitting a lyric line the way `insertAtSelection` does), the caret between the halves;
  - the header names a value and the caret is in the header, or the song has no body yet → nothing is inserted, the
    header line's value is selected, since a second header line would be read past.
  `Insertion` gains a selection end (or an empty `text` with a range) for the two cases that insert nothing.

### 3.2 `ChordProPrettifier`

Today body directives stay where they are, and that stays true. Three additions:

1. **The song's own value goes to the header.** Where the header has no line of `tempo` (or `time`) and the body has a
   readable one, the first readable one is, by default 3, the song's own: it is moved into the header in
   `metadataOrder`. Meaning is unchanged, and afterwards every file states its main values at the top. Not from inside
   a delegated environment, where braces are that program's text. Needs a pre-pass for the (at most two) line indices.
2. **A run of changes is one group**: `{tempo}` / `{time}` lines outside every environment with only blank lines
   between them are written together, tempo first, no blank between.
3. **The group heads what follows it**: a blank line before it, none between it and the next line (a `{start_of_…}`
   or lyrics) — except while an implicit paragraph or legacy heading section is running, where a blank line would
   close the section the change only cuts (the existing `cutsRunningSection` reasoning, extended to these two names).
   Inside an environment nothing moves: interiors keep their whitespace.

`prettifiedOffset` has to keep the caret on a hoisted line; add the case to its tests. Every import runs Prettify, and
`comparable` formats both sides, so an older library file still matches its re-import.

### 3.3 `ChordProMetadataFields`, `ChordSheetConverter`

- `ChordProMetadataFields.set` needs no change; add tests that pin "Song defaults edits the header line and leaves
  every change in the body" now that the body lines mean something.
- `ChordSheetConverter` hoists every `Tempo:` / `Time:` label into the header today, so a second one is lost. Keep the
  first of each in the header and write a later one in place, before the line that followed it. That is also what
  makes Campfire's own PDF (§5.4) come back with its changes.

## 4. Editor

- `EditorToolbar.isEnabled`: `metadataName in ChordProHeader.changeableMetadata` is always enabled, like the three
  repeatable kinds. The KDoc of `metadataInsertions` and of `isEnabled` say why these two differ from `{key}`.
- `TextFieldState.insert`: the two changeable kinds go through `ChordProHeader.insertChangeable` with the caret's
  offset; everything else is unchanged.
- The preview needs nothing of its own beyond §5.1: it shows the line in place, whatever the Metronome switch says
  ("it shows what is being written"), in the file's own numbers, and is never paged.
- `DeclaredMetadataCache` is unaffected (it still answers per kind).

## 5. `:presentation` — the page

### 5.1 Render sections (`SongLyrics.kt`)

- `RenderSection.Timing(tempo: String, time: String)`, a whole, uncuttable, unfoldable section of one unit, the twin
  of `RenderSection.Metadata` in read only form. It is drawn by the text line `SongMetadataSection` already draws for
  read only mode (extract that row: chord size and weight, content colour, `song_details_tempo` and
  `song_details_time` joined by `KEY_SEPARATOR`), with one new string in both languages as its content description
  ("From here: %1$s").
- `toRenderSections` gains `showsTiming: Boolean` (`prepareSongLyrics`, `SongLyricsInputs`): false with the Metronome
  switch off on the details screen, always true in the editor's preview.
  - **false**: `Timing` is one more cut `joinCutSections` joins over and `isSectionCut` knows, exactly like
    `Transpose`. The page is byte for byte today's.
  - **true**: a `Timing` is the second cut that is *not* joined over (after the recall): the group ends before it, the
    continuation after it is headed `UNNAMED_SECTION_HEADER`, and a `RenderSection.Timing` is emitted in its place.
- **A marker with no lines after it is dropped**: one followed by another marker with no `RenderSection.Lines` between
  them (lyrics-only mode took the tab away, two changes around a comment), and one at the end of the song. So is one
  that `LayoutBudget` cut off from its section.
- The values shown: the tempo through `ChordProTempo.parse` and `MetronomePattern.coerceBpm`, the time through
  `ChordProTime.parse` and `TimeSignature`, 4/4 where null — the way `withMetadataSection` reads the header's.
- `ChordProSong.withTempo(bpm)` (`SongTempo.kt`) rewrites the blocks' tempos too, by default 5, through one pure
  `sectionBpm(sectionFileBpm, songFileBpm, playedBpm)` that §6 shares. The page and a setlist's PDF then both show
  what is played, as the header line does.

### 5.2 Forced pages (`SongLyrics.kt`, `SectionGrid.kt`)

Each stretch between two markers is laid out as a small song of its own, by the algorithms that exist, and the grids
are put end to end. Nothing inside `flowIntoRows`, `flowLikeAMagazine` or `flowIntoPages` changes.

- `SongUnits` carries `timingStarts`: the section index of every marker, 0 first.
- `SectionGrid.kt`: `fun List<SectionGrid>.concatenated(): SectionGrid` — rows offset by the rows before, `columns`
  as they are, `columnCounts` / `wideRows` / `joinsPrevious` appended, `sharesKeyline` kept (every segment is laid out
  at one width, so they agree). A segment's first row never joins the previous one, so it starts a page by itself.
- `SongSectionsLayout.searchGrid(totalWidth)`: today's body becomes `searchSegment(from, until, fitHeight)`, its
  lambdas offset by the segment's first section and unit (`sectionStarts` sliced and rebased, the `flowIntoRows` grid
  expanded to units inside the segment). With one segment it is called once, as today. With several, each segment is
  searched against the page's height (`maxRowHeightPx`, not `availableHeightPx`), the unit grids are concatenated, and
  the result is `SearchedGrid(grid, fits = false, height = Int.MAX_VALUE)`: such a song is always stepped through, so
  it keeps the step button inset and every page is padded to the viewport by `arrange` as today.
- **No segments** where nothing is paged anyway: `!canCutSections`, no viewport height, or more than
  `MAX_CUT_SECTION_COUNT` sections (a songbook). The markers are then lines in the one column.
- The marker must never be half of a side-by-side pair in `flowIntoPages`: `isNarrowSection` is false for it.
- Each segment picks its own column count, so a short bridge may sit in one column between two-column pages. Accepted;
  it is what a short song between two long ones does.

Everything downstream follows from the grid: dividers, `SongRows`, the step buttons, the dots, `ReadingAnchor`.

### 5.3 Tests

`RenderSectionsTest` (on/off, mid-section cut, dropped markers, lyrics-only, a marker inside a chorus card),
`SectionGridTest` (`concatenated`), `SectionPagingTest` / `SectionCuttingTest` (a segment boundary always starts a
page; a marker is never paired), `SongTempoTest` (`sectionBpm`, `withTempo` over blocks), `SongMetadataTest`.

### 5.4 PDF (`PrintLayout.kt`)

`blockRows` prints a `Timing` as one `detailStyle` row (`Tempo: 90   Time: 3/4`, the labels the heading uses), under
`options.showMetadata`, kept with the block after it the way a heading is, and breaking no column or page. The text
layer carries it like any row. `PrintLayoutTest` gets the row and the "not orphaned at a column's end" case.

## 6. `:presentation` — the click follows the page

- `SongTiming(tempo: Int?, time: TimeSignature?)` in `ui/metronome`, the file's values of one marker.
  `MetronomeContext.Song` gains `timing: SongTiming? = null`, null being the song's opening.
- `metronomePatternOf`: with a timing, the tempo is `sectionBpm(timing.tempo, song.tempo, effectiveTempo.bpm)` (the
  effective tempo itself where the timing names none) and the signature is the timing's, 4/4 where null. The accents
  are looked up for that signature as today.
- `isMetronomeContextMoved` already answers true for any context that is not a rename, so a new timing restarts the
  bar (default 7). The renaming hold compares file names only and needs nothing.
- **Which timing a page is on** (`RowSnapping.kt`, pure, tested):
  `timingIndexAt(offset, rows: SongRows, timingSections: List<Int>)` — the stop at or above `offset` (within
  `POSITION_TOLERANCE`), its `stepSections` entry, the last marker at or before that section; -1 before the first.
  The page reads it as derived state from where the scroll is **headed**: `SongStepper.origin` (a step's target), and
  `RowSnapFlingBehavior` exposes its snapped fling target the same way; a finger still dragging changes nothing until
  it lets go. Before the first layout it is the song's opening.
- `SongDetailsScreen` keeps a `pageTimings` map beside `pageScrollStates`, written by each composed page, and the
  `snapshotFlow` that reports `pagerState.targetPage` reports the pair: `onSongDetailsPageChanged(destination,
  fileName, timing)`, stored next to `songDetailsTargetSongs`. A song paged **back** to is put at its end, so it
  reports its last timing, and the click lands on that rather than on the opening tempo.
- `MetronomePanel`'s `BeatRow` takes its signature from the context's timing, so its blocks are the bar being played;
  the length change is already animated (`8c58498e0`).
- The app bar's tempo (`SongHeaderNote`, "the tempo the click would play at") follows the page too, crossfading. Song
  cards, the setlist's running time and the Song defaults sheet keep the opening values.
- Read only mode changes nothing here: markers, pages and the following click all stay.

Tests: `MetronomePatternsTest` (timing, scaling, no opening tempo, range clamp), `MetronomeContextTest` (a timing
change is a move), `RowSnappingTest` (`timingIndexAt`: first page, a page taller than the screen, a songbook stepped
by sections, the end of the song).

## 7. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Metronome switched off | No marker, no forced page, cut sections drawn whole again. The file is untouched. |
| Chords switched off, metronome on | Markers and pages stay; a marker whose stretch was all tablature is dropped. |
| A change restating the value in force | Nothing: no block, no page. |
| Two changes with only a comment between | One page; the later values count. |
| A change after the last line | Nothing. |
| A change in the middle of a chorus | Two cards, a page turn between them. A recall repeats the chorus whole. |
| A folded section after a marker | The marker and its page stay; the section is its header. |
| Tap tempo or the stepper on page one | Sets the opening tempo; later pages scale (default 5). |
| Song defaults → tempo cleared, body still changes it | Header keeps an empty `{tempo}` (already so): opens at the default, changes later. |
| Click not playing | Pages are forced all the same, so pressing play moves nothing. |
| Paging to the next song / back to the previous | Opening timing / the last timing of that song. |
| Editor's Tempo button with the caret in a tab | The line cuts the run, like a comment there. |
| A songbook past 200 sections | Plain column, markers as lines, the click follows the marker scrolled past. |
| Sync, export, import | The lines are text of the song file; nothing else to carry. |
| Demo songs | Unchanged, so every installation still holds the same files. |

## 8. Order of work

Each step builds and tests green on its own, one commit each.

1. §2: the block, the parser, the serializer, the pass-throughs.
2. §3: header insertion and the `metadataInsertionIndex` fix, Prettify, the converter.
3. §4: the toolbar.
4. §5.1 and §5.4: markers on the page, in the preview and in the PDF (no pages forced yet).
5. §5.2: forced pages.
6. §6: the click, the panel, the app bar.
7. Docs: the root `CLAUDE.md` (Metronome: "the first `{tempo}` and `{time}` count…"; How a song is played),
   `chordpro/CLAUDE.md` (parser, Prettify, header, metadata fields, converter, model), `presentation/CLAUDE.md`
   (render sections, the layout, the toolbar, what a click plays for), `metronome/api` if `TimeSignature` grows.

## 9. Checks owed by hand

- Desktop, two columns: a three-part song (4/4 at 120, 3/4 at 90, back) — pages, markers, the pedal keys, the dots.
- Phone upright and on its side: the single column pages, a change mid-verse, a change before a long tab.
- A click through the page turns: restarts on one, the beat row's length follows, the notification's text follows.
- Setlist: an overridden tempo scaling the changes; paging back into the last timing of the previous song.
- Metronome switched off and on with the song open: the reader stays near their place (`ReadingAnchor` is by section
  index, and the markers shift those).
- Editor: both buttons in all four caret cases; Prettify hoisting a headerless tempo with the caret on that line.
- PDF export and re-import of a song with changes.

## 10. Not in this plan

- `{key}` changes mid-song as something the app shows or offers.
- A stepper on a marker, or overrides stored per stretch.
- A count-in or a gradual tempo change (rit., accel.).
- A hint in the Song defaults or About the song sheet that the song changes tempo later.
- Following the click by bars instead of by pages.
