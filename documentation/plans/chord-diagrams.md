<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Chord diagrams — implementation plan

Written 2026-10-06 against `5550057cb`. How every chord of a song is fingered, on the guitar, the ukulele or the
keyboard, as a section of diagrams at the top of the song. Nothing about it reaches the network, and no asset is
shipped for it.

## 1. What is being built

1. **A Chords section at the top of the song details screen**, after the transposition, capo, tempo and time: one small
   diagram per chord the song uses, in the order they are first played, named as the page names them.
2. **Variations**: a Chord shapes sheet, opened from the section's header, shows the song's chords larger and steps
   each through the other ways it can be played. The one a chord is left on is the player's own from then on, for that
   chord on that instrument, in every song.
3. **An instrument choice in Settings → Songs**: Guitar, Ukulele or Keyboard.
4. **A Chord diagrams switch in Settings → Features**, after Chords. Off takes the section away.
5. **The song's own `{define}` directives are read**, which today are parsed and dropped: a shape the file gives a chord
   is the one drawn for it in that song.
6. **A definition follows its chord through a transposition**: the reader's, the file's own `{transpose}` and the
   editor's transpose action all move the shape along the neck with the name. Today the last of them leaves the line
   naming a chord the song no longer has.
7. **The editor writes and shows them**: a Chord shape button that writes the line for the chord at the caret, already
   filled in with a shape to change, a field that marks a line it cannot read, and a preview that draws every
   definition as it is typed.

### What is already there, and what is not

- `ChordProChordNames.isChordName` is the one rule for what counts as a chord, and it only *recognizes* one: nothing in
  the app knows which notes `F#m7b5` is made of. That reading is the first thing to write (§2.1).
- **Nothing in the song takes a new gesture.** A tap anywhere on it turns the page (`stepOnTap`), and the chords of a
  line are drawn rather than composed, so a chord that answered a press would have to be found by its position and
  would take taps from the page turn. The plan stays out of both: the diagrams are in the section, and the variations
  in a sheet opened from its header.
- `{define}` and `{chord}` are known directives that the parser drops; `ChordProNotation.convertText` already renames
  the chord they name. The transposition deliberately does not (`rewriteChordNamesInText`'s `renameDefinitions`), since
  it "would rename a definition without moving the fingering that follows it". Moving the fingering is §2.4.
- `ChordProTabTransposer` is the precedent for that: it moves the frets of a tab by the transposition or by its
  octave, whichever stays on the fingerboard.
- The editor's first toolbar row writes into the header through `ChordProHeader` wherever the caret is, and the kinds a
  song may say twice (tag, language, link) are the model for a button that can be pressed again and again.
- The song's first section (`RenderSection.Metadata`) is the model for the new one: a whole, uncuttable section that
  flows through the same rows and columns as the lyrics and scales with them.
- The cloud folder's `preferences.json` was written so that settings other than the songs' can join it (see Sync in the
  root `CLAUDE.md`), which is where the chosen variations go (§4.3).

### Variations: what I agree with, and where I would go further

Saving the player's variation per chord is right, and it should be **one choice per chord and instrument for the whole
library**, not per song or per setlist: which F somebody plays is a habit of their hands, unlike a transposition, which
is the band's reading of one song. Three details make it hold up:

- **The shape is stored, not its number** (`x 3 2 0 1 0`, not "variation 2"), so a later version that adds or reorders
  shapes moves nobody's choice, and a shape drawn by hand in some future chord editor fits the same field.
- **Two spellings of one chord share the choice**: `C#m7`, `Dbm7` and `C#min7` are one chord (§2.1's `id`).
- **The exception per song is the file's own `{define}`**, which is what ChordPro has for "in this song the G is played
  this way". It travels with the file as a tag does, and wins in that song over the habit.

### Defaults this plan assumes — veto any of them before the work starts

1. **On by default**, like every other feature, and introduced by a What's new message. The section costs the first
   page of every song some height, which is why it folds (default 2).
2. **The section's fold is one preference for every song** (`isChordSectionFolded`), unlike a verse's fold, which is
   kept per song: somebody who knows their chords folds it once, and has it one tap away in every song.
3. **Variations are chosen in a sheet**, opened by a button at the end of the section's header, and nowhere on the
   page itself: the cells take no press, so a tap on the section turns the page like a tap anywhere on the song. The
   button is not there in Read only mode, which takes the controls off the page.
4. **The section is part of the song**, scrolling away with its first page, rather than a panel pinned in the app bar
   the way the metronome's is. Past that page a song shows no diagram: a chord in the text showing its own when
   pressed was considered and left out (§10).
5. **The capo moves the keyboard's diagrams and not the fretted ones**: the chords on the page are the shapes fretted
   above the capo, and a keyboard has none, so its diagram is the chord that sounds (§5.2).
6. **Standard tunings only**: `E A D G B E` and re-entrant `G C E A`. Right-handed diagrams.
7. **The editor's preview draws the definitions and nothing else**: it shows what is being written, and the app's own
   shapes are not written. The PDF gets no diagrams in this version.
8. **The chosen variations are synced** with the library's other overrides. Step 9 of §7 is that, and can be left out
   without touching anything before it.
9. **A definition the reader's transposition moved is drawn only while a hand can hold it** (§3). The other octave is
   tried first (§2.4), and only a shape left needing a fifth finger or passing the fifteenth fret in both steps aside
   for the player's own shape of the chord it became. The editor's transposition writes the moved line regardless, so
   that transposing back restores it.
10. **A definition is typed, with the editor's help, and not drawn on a fingerboard**: the button fills the line in,
    the preview draws it, the field marks one it cannot read. The fingerboard is still §10's.

## 2. `:chordpro`

All of it pure, stateless objects, like the rest of the module. No new module: what a chord name means has to agree
with `ChordProChordNames` about what a chord name is, and the shapes are read in ChordPro's own `{define}` syntax.

### 2.1 `ChordProChords` — what a chord name means

- `model/Chord(root: Int, intervals: List<Int>, bass: Int?)`: pitch classes, the intervals in semitones above the root,
  sorted, the root's `0` among them unless the name omits it. `id` is the root as a sharp and the intervals
  (`C#:0.3.7.10`, with `/G#` for a bass), which is what a stored choice is keyed by.
- `ChordProChords.parse(name: String, notation: ChordNotation): Chord?` walks the name the way `isChordName` does and
  shares its token lists, so the two cannot drift apart: null exactly where `isChordName` is false. It takes the
  notation the name is shown in (`H`, and the Latin and Nashville ones when they arrive), a parenthesized chord and a
  lowercase minor root, since it is handed what the page draws.
- The reading, in the order the name is walked: the triad (`m`, `min`, `mi`, `-`; `dim`, `°`; `aug`, `+`; `sus`, `sus2`,
  `sus4`; `5`); the number (`6`, `69`, `7`, `9`, `11`, `13`, each bringing the ones under it; `maj`, `M`, `Δ` making the
  seventh major; `dim7` and `°7` a diminished one; `ø` the half diminished); `add`; the alterations (`b5`, `#5`, `b9`,
  `#9`, `#11`, `b13`, bare or in parentheses); `alt`; the omissions (`no3`, `omit5`); the bass after `/`.
- `ChordProChords.noteNames(chord, notation, preferFlats)`: the notes as names, for the sheet and a screen reader.

### 2.2 `ChordVoicings` — how it is played

```kotlin
enum class ChordInstrument(val id: String, val tuning: List<Int>) { GUITAR, UKULELE, KEYBOARD }

sealed interface ChordVoicing {
    /** Low string first, the fret each is stopped at, 0 open and null muted; the finger on each where it is known. */
    data class Fretted(val frets: List<Int?>, val fingers: List<Int>?) : ChordVoicing
    /** The keys pressed, in semitones above the C the diagram starts at; the bass of a slash chord apart from them. */
    data class Keys(val notes: List<Int>, val bass: Int?) : ChordVoicing
}

object ChordVoicings {
    fun default(chord: Chord, instrument: ChordInstrument): ChordVoicing?
    fun all(chord: Chord, instrument: ChordInstrument): List<ChordVoicing>
    fun read(shape: String, instrument: ChordInstrument): ChordVoicing?   // a stored choice
    fun write(voicing: ChordVoicing): String
}
```

**Where the shapes come from** is the decision that shapes the rest. A bundled third-party database gives every chord a
checked fingering, and is two thousand shapes of somebody else's data to ship, to download with the web build and to
keep a licence notice for, in one tuning, for the chord names it happens to list. Finding every shape by search needs
no data and covers any chord, but it is the search that decides which C is shown first, and a search that puts an
unfamiliar C first is wrong to everybody. So it is both, each for what it is good at:

- **A table of the shapes everybody knows**, written by hand in the `{define}` syntax and read by the parser of §2.3:
  about seventy open position chords for the guitar with their fingerings, the E, A and D shaped barre chords of each
  common quality as shapes that move up the neck, and about a hundred shapes for the ukulele. These always come first.
- **A search for everything else**, and for the variations after the table's: every way to stop the strings inside four
  frets, at each position up to the twelfth, that sounds only notes of the chord and all of the ones that make it that
  chord (the fifth may go first, then the root, where there are more notes than strings), with the bass or the root on
  the lowest string that sounds (not on the re-entrant ukulele), no muted string between two that sound, and no more
  than four fingers, a barre counting as one. Ranked by position, by how many strings sound and by the stretch; a
  shape the table already has is not listed twice. A few thousand candidates a chord, which is nothing.
- **The keyboard needs neither**: the notes from the root up, at most five, and the inversions as its variations; a
  slash chord's bass an octave below.

`default` is a table lookup wherever the table has the chord, so the section never waits for the search; `all` runs it,
and is only asked for by the Chord shapes sheet. Searched shapes carry no fingering, and are drawn without finger
numbers.

**This is not every shape, and no list could be.** The search is complete for its own rules, which is all that "every
variation" can honestly mean: what counts as playable is a matter of hands — a thumb over the neck, a five fret
stretch, a muted string in the middle — and rules loose enough for all of those bury the shapes people play under
hundreds that nobody does. And the shape a song wants is often no voicing of the chord's name at all: a `G` and a `C`
that keep the same two notes ringing on top, a rootless jazz voicing, a chart that writes `D` over what is fingered as
`Dsus2`, a tuning that is not the standard one. Those are what a `{define}` is for (§2.3), which is drawn as the file
writes it, without asking whether it spells the name. They are the reason to read definitions, and not yet a reason to
build an editor for them (§10).

### 2.3 `ChordProDefinitions` — the song's own shapes

- `model/ChordDefinition(name: String, instrument: ChordInstrument, voicing: ChordVoicing, movedBy: Int = 0)`, in
  `ChordProMetadata.definitions`, in file order, the last one for a chord and instrument winning. `movedBy` is the
  frets a transposition moved the shape by (§2.4), zero for one that is as the file writes it.
- `{define: name base-fret N frets … fingers …}` and `{chord: …}` with a shape are read: frets relative to the base
  fret, `x`, `N` and `-1` muted. `{define: name keys 0 4 7}` is the keyboard's, relative to the root. The instrument is
  the selector where there is one (`{define-ukulele: …}`, `-guitar`, `-keyboard`, `-piano`, which the parser stops
  dropping for these two directives) and otherwise the one with as many strings as the definition has frets. Anything
  else (`copy`, `display`, a string count no instrument has) declares nothing.
- To `ChordProHighlighter` the chord a definition names is a `CHORD` token inside the directive's value, so the editor
  draws it as it draws the chords, and a definition whose shape cannot be read (a fret that is no number, more
  `fingers` than `frets`, `frets` with nothing after it) is one `INVALID` token, decided by the function that reads
  it, as an unreadable `{capo}` is. A definition that is only not Campfire's to draw (a name that is no chord, a banjo's
  five strings, a `copy`) is neither: it is valid ChordPro that the page has no use for.
- `ChordProSerializer` writes them back, or `parse(serialize(parse(x))) == parse(x)` stops holding.
- The model's names stay in the standard notation, like the rest of the model. `Chord`, `ChordVoicing` and
  `ChordDefinition` join `gradle/compose-stability.conf`.

### 2.4 Definitions and the transposition

`ChordProDefinitions.transposed(definition, semitones, rename): ChordDefinition`, one function for the model and for
the text:

- **The name** is renamed like any chord, by the transposer's own `rename`, so it is spelled as the song's chords are.
- **A fretted shape moves along the neck by as many frets**, every string that sounds, the open ones too, which is
  what a barre or a capo does to a shape: the notes are exactly the chord's, transposed.
- **A shape with an open string never goes down.** Transposed down by any amount, it moves up by the rest of the
  octave at once, which is the same chord: what `ChordProTabTransposer` already does to a tab with an open string,
  and is pinned there for every amount down.
- **For every other shape the octave is what keeps it playable.** Up by the transposition and down by the rest of the
  octave are the same chord, so both are looked at: one that would take a fret off the neck is out, and of the rest
  the one a hand can hold is taken (`isHoldable`: no more than four fingers, a barre counting as one, within the first
  fifteen frets), the lower one where both can be held or neither can. So a shape high on the neck comes down rather
  than running off its end, and a barre moved down far enough becomes an open shape with a finger to spare. This is
  where a chord parts from a tab, which stays with the move it was asked for while every fret is on the fingerboard: a
  solo written at the twelfth fret belongs there, and a chord is the same chord an octave lower. The lower one never
  needs more fingers than the higher, which is why there and back lands on the shape it started as.
- **The fingering follows where the move is a barre coming or going**: the open strings a move stops become the first
  finger's barre and every other finger moves one on; strings that come to rest open again lose their finger and the
  others move one back. A shape that would need a fifth finger keeps no fingering.
- **A keyboard's `keys`** are relative to the root and do not change.
- `movedBy` adds up, so there and back is zero, and the definition it started as.

Where it is applied:

- **On the model** (`ChordProTransposer`'s walk, which the viewer renders from): `ChordProMetadata.definitions` are
  moved by what the whole song is moved by, the file's opening `{transpose}` and the reader's transposition together.
  A modulation further down does not move them again; a chord after it is matched by the name it is shown under.
- **In the text** (`transposeText`, the editor's transpose action): a `{define}` or `{chord}` line with a shape is
  rewritten where it stands — the name, the base fret, the frets and the fingers, each in its place — and every other
  character of the line is kept (a selector, a `display`, the spacing), as the rest of that function keeps the file's
  formatting. The base fret is written as a diagram would draw the shape. A `{chord: Am}` that only names a chord, and
  both names of a `copy`, are renamed. A line the function cannot read is left byte for byte, as a tab that fits in no
  octave is. `transposedOffset` keeps a caret on a definition line on that line.

### 2.5 For the editor

- `define` joins `ChordProHeader.repeatableMetadata`, last in the header: `metadataInsertionIndex` puts a new one after
  the last definition, or at the end of the header where there is none. **`ChordProPrettifier` must format a file that
  holds definitions exactly as it does today**, since an import compares formatted texts and a library file that came
  out differently would be asked about as a conflict; that is pinned by a test before the kind is added to anything
  Prettify reads, and decides whether the kind goes into `metadataOrder` or beside it.
- `ChordProDefinitions.line(name, voicing): String` writes a definition the way the parser reads it:
  `{define: G base-fret 1 frets 3 2 0 0 0 3 fingers 2 1 0 0 0 3}`, `{define: G keys 0 4 7}`. No selector, since the
  number of strings already says which instrument it is for.
- `ChordProDefinitions.rangeOf(text, chord, instrument)`: where the line defining a chord is, and its frets or keys in
  it, for a caret to be sent there.
- `ChordProSyntax.chordAt(text, offset): String?`: the chord of the brackets the caret is in or touching.

### 2.6 Tests (`commonTest`)

- `ChordProChordsTest`: a table of about a hundred names and their notes; every name a generated corpus gets past
  `isChordName` parses, and nothing else does; German names; enharmonic and respelled names share an `id`.
- `ChordVoicingTablesTest`: **every shape of the tables sounds exactly the chord it is filed under**, which is what
  catches a mistyped fret in a few hundred lines of data; fingerings name as many fingers as frets are stopped.
- `ChordVoicingsTest`: the search's rules as invariants over every chord of the corpus on both fretted instruments; a
  golden list of the first shape of the forty chords every songbook uses; the keyboard's inversions; the same answer
  twice; `write` and `read` round trip; a chord with no shape answers an empty list rather than a wrong one.
- `ChordProDefinitionsTest`: the syntax, the selectors, the malformed ones, the serializer's round trip, `line` read
  back by the parser, the highlighter's tokens, `chordAt` and `rangeOf`.
- `ChordDefinitionTransposerTest`: every shape of the tables, moved by each of the eleven transpositions, still sounds
  the chord its new name spells; the octave chosen (a shape with an open string up for every amount down, otherwise the
  one a hand can hold, the lower of two, never one off the neck); there and back restores the shape from every
  transposition, and the fingering wherever it never needed a fifth finger on the way; `keys` untouched. In the text:
  every character outside the name, the base fret, the frets and the fingers survives; a line that cannot be read is
  left alone; the caret stays on its line.
- The header's and the prettifier's tests grow: where a definition is inserted, and that a file with definitions is
  formatted as it was before this plan.
- A `desktopTest` that draws a contact sheet of every table shape into `CAMPFIRE_CHORD_QA_DIR` where that is set, the
  way `CAMPFIRE_PRINT_QA_DIR` does for the PDF: a test can say a shape is the right chord, and only eyes can say it is
  the shape a guitarist expects.

## 3. Which shape is shown

One pure function in `:presentation` (`ui/chords/ChordSelection.kt`, tested), asked by the section and by the sheet:

1. **The song's definition** for that chord and instrument, as the model carries it: as the file writes it where
   nothing moves the chords, and moved with them otherwise (§2.4). A shape as written is drawn whatever it asks of a
   hand, since asking for the unusual is what a definition is for. A moved one (`movedBy` not zero) is drawn only
   while it `isHoldable`, in the octave §2.4 chose for exactly that; where neither octave is, this rule answers nothing
   and the next one does. The keyboard's always apply.
2. **The player's stored shape** for the chord's `id` on that instrument, where it still reads as a shape of that
   instrument.
3. **`ChordVoicings.default`**.

A chord drawn from the song's definition has no stepper in the sheet, where its row says "Defined in this song"
instead, and by how many frets it was moved where it was: the song says how it is played, and the editor is where that
is changed.

## 4. Data

### 4.1 `:data:model`

`UserPreferences` gains, with `UserPreferencesDocument`, the mappers and their tests:

- `areChordDiagramsEnabled: Boolean` (true);
- `chordInstrument: ChordInstrument` (`GUITAR`; an enum of its own with an `id`, mapped to `:chordpro`'s in
  `:presentation` the way `Notation` is, since `:data:model` does not see `:chordpro`; an id this version does not know
  reads as the guitar);
- `chordVoicings: Map<String, Map<String, String>>`, by instrument id and then by chord `id`, the value what
  `ChordVoicings.write` gives. An instrument id this version does not know is kept as it is;
- `isChordSectionFolded: Boolean` (false).

None of them is exported; only the third is synced.

### 4.2 Use cases

None is new. `:presentation` calls `:chordpro`'s objects directly where it renders, as it does `ChordProTabWrapper`, and
the preferences are written through `UpdateUserPreferencesUseCase`.

### 4.3 Sync (step 9)

`preferences.json` in the cloud folder gains a member beside `songs`, at the same `version`:

```json
{"version": 1, "songs": {}, "chords": {"guitar": {"F:0.4.7": "x x 3 2 1 1"}, "keyboard": {"C:0.4.7": "1"}}}
```

`SyncedPreferencesDocument` merges it three ways, value by value, as it does a song's fields: two devices that chose
for different chords both keep their choice, a choice beats a reset, two choices for one chord keep this device's. It
is one of this version's own fields in `localDocument`; an instrument or a value this version cannot read passes
through. No entry is ever dropped for a song leaving the library, since none belongs to one. `localChanges` schedules a
run for a change to the map, and not for the step's own write. `SyncedPreferencesTest` grows with it.

## 5. `:presentation`

### 5.1 Settings

- **Features**: a **Chord diagrams** switch after Chords (`setChordDiagramsEnabled`), described as the diagrams at the
  top of a song. Disabled, not hidden, while Chords is off, as the chord spelling rows are.
- **Songs**: an **Instrument** `SettingsSubsection` after the accidentals, a `SegmentedChoice` of Guitar, Ukulele and
  Keyboard (`setChordInstrument`), disabled while either switch is off.

### 5.2 The section

- `prepareSongLyrics` collects the song's chords, away from the main thread with the rest of the model
  (`ui/chords/SongChords.kt`, pure and tested): every chord of the lyric lines, the grid cells (each chord of a `C~G`),
  the brackets of comments and labels and the chord rows of a tab, once each by the name shown, in the order they first
  appear, each with its `Chord` and its `ChordVoicings.default` for the instrument in `SongLyricsInputs`. Annotations,
  `N.C.` and anything `ChordProChords` does not read are not chords. A file with more than 48 different ones is a
  songbook and gets no section.
- `RenderSection.Chords(cells)`, each cell a name, an instrument and a shape, put after the `Metadata` section by
  `SongLyrics` where it is handed `chordDiagrams` (the song details screen with the feature and the chords on; the
  editor's preview hands it its own, §5.5). Whole and uncuttable like `Metadata`, placed after `LayoutBudget`, measured
  and keyed by its content, so a chosen variation, which changes no size, lays nothing out again.
- **Headed "Chords" by a pill that folds it**, as every section is, written to `isChordSectionFolded`
  (`toggleChordSectionFold`), in Read only mode too. Outside Read only mode the pill is followed by a **Chord shapes**
  button, an icon as tall as the pill and growing with it, which opens the sheet of §5.3 and stays while the section is
  folded.
- Under it a `FlowRow` of cells: the chord's name in the chords' style and the second accent color over its diagram.
  It wraps and never scrolls sideways, since a song may be read with a pedal. Everything in it grows with `fontScale`.
  A cell is sized so that six fretted diagrams fit across the smallest phone's column, to be settled on the device
  (§8); the keyboard's are wider, three across.
- The cells take no press: a tap on them turns the page, like a tap anywhere on the song.
- **With the keyboard and a capo**, the diagram is of the chord that sounds — the page's chord raised by the capo
  (`effectiveCapo`) — and the cell names both, the page's and after it, muted, the one that sounds. The fretted
  instruments draw the page's chord whatever the capo.
- A chord with no shape on the instrument keeps its cell, with its name over an empty diagram frame.

### 5.3 The Chord shapes sheet

`DialogType.ChordShapes`, a `CampfireBottomSheet` like the song's other sheets: titled after the button that opens it,
naming the song under the title, and taken down with the song.

- **It lists the chords of the section**, in its order, in cells that flow into as many columns as the sheet is wide:
  the chord's name, its diagram at about twice the section's size, with finger numbers where the shape has them, its
  notes as names in the reader's notation, and, where there is more than one shape, the app's own `Stepper` reading
  `2 / 5`, going around like the transposition's, highlighted while the shape is the player's own and reset by a tap on
  its value (`setChordVoicing(instrument, id, shape?)`).
- **A step is written at once**, as the page's own steppers write, so the sheet has no Save and its close button
  cancels nothing. The diagram crossfades from shape to shape, in the sheet and in the section behind it.
- A chord drawn from the song's definition has §3's line in the stepper's place.
- With the keyboard and a capo it shows what the section shows: the chord that sounds, under both names.
- `ChordVoicings.all` is asked per cell as it is composed, which is §2.2's search for one chord.
- Only the header holds still: the cells scroll, and fade at the top, as in every sheet.
- It is a dialog of the view model's like the rest, so Back, Escape, the browser's history and the required update
  screen need nothing of their own.

### 5.4 The diagram

`ui/components/ChordDiagram.kt`, drawn on a `Canvas` from `ui/chords/ChordDiagramGeometry.kt`, which is pure and
tested, and which a PDF could draw from later.

- **Fretted**: the strings and four frets (five where the shape spans them); the nut as a heavy line at the first
  position, and otherwise the number of the lowest fret shown beside it; a dot per stopped string, the root's in the
  second accent color; a barre as a bar across the strings it holds, found from the fingering where there is one and
  otherwise where the lowest stopped fret is held on two strings or more with no open string between them; `×` and `○`
  above the nut.
- **Keyboard**: two octaves from C, the pressed keys filled, the root's in the second accent color, a slash chord's
  bass ringed an octave below.
- Colors are read while drawing, so the theme's cross fade costs no composition. Its content description says the chord
  the way it would be dictated: the name and the frets from the lowest string, or the name and its notes.

### 5.5 The editor

- **A Chord shape button**, the last of the toolbar's first row, with what a file opens with, and like Tag and Language
  never out of reach. It writes one line into the header through `ChordProHeader.insert`, wherever the caret is, as one
  step of the undo history (`chordShapeInsertion`, pure and tested):
  - for **the chord at the caret** (`chordAt`), or with the name left to be typed where the caret is at none;
  - filled in with **the shape the player would be shown** for that chord on the instrument chosen in Settings (§3
    without its first rule), by `ChordProDefinitions.line`: changing three numbers takes no knowledge of the syntax,
    and writing the line from nothing takes all of it;
  - with the caret left on the frets (or the keys), selected;
  - and **no second line for a chord that has one** for that instrument: the caret goes to the frets of the one there
    is (`rangeOf`), the way a `{tempo}` the header already names is selected rather than written again.
- **The field is in the reader's notation**, a definition's name with the rest, and the file in the standard one:
  `convertText` already renames it both ways.
- **The field marks a definition it cannot read** in the error colour, and draws the chord a good one names as a chord
  (§2.3's tokens, through `ChordProOutputTransformation`).
- **The preview has a Chords section of its own**: every definition the text holds, in file order, each on the
  instrument it is written for and under the name the field gives it — and none of the app's own shapes, whatever the
  two switches and the instrument in Settings say, since the preview shows what is being written. It is where a fret
  typed wrong is seen. It folds nothing and takes no press, like the rest of the preview, and the file's own
  `{transpose}` moves these shapes as it moves the preview's chords. A song that defines nothing has no such section.
- **The transposition stepper moves the definitions** with the chords it rewrites (§2.4), in the same step of the undo
  history, and back again to the text it started from.
- The overflow menu and the song details editing menu get nothing new: a definition is changed where it is seen, in
  the text.

### 5.6 View model and strings

`setChordDiagramsEnabled`, `setChordInstrument`, `setChordVoicing`, `toggleChordSectionFold`, each one write of the
preferences, and `DialogType.ChordShapes` among the song's sheets. Every string in both languages; a sentence that
carries a chord's name is read with `textResource`.

### 5.7 Tests (`desktopTest`)

`SongChordsTest` (the order, the kinds of line, a recalled chorus counted once, what is left out, the cap),
`ChordSelectionTest` (§3's order, a moved definition a hand can and cannot hold, a stored shape of the wrong
instrument, the keyboard with a capo), `ChordShapeInsertionTest` (what the editor's button writes for a caret in a
chord, in none and in one already defined) and `ChordDiagramGeometryTest` (the window of frets, the barre, the keys).

## 6. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Chord diagrams switched off | No section, the instrument row disabled. The stored choices stay. |
| Chords switched off | The same, and the switch itself is disabled. |
| Read only mode | The section is shown and folds; the button that opens the sheet is not there. |
| A song from an archived setlist | As outside Read only mode: the shape of a chord is not the setlist's to protect. |
| A tap on the section | A page turn, as anywhere else on the song; only the header's pill and button take one. |
| The reader's transposition, a `{transpose}` in the file, a modulation further down | The chords on the page are the ones collected, so the section lists what is fretted, each key's chords once. |
| A definition, and the reader's transposition or the file's opening `{transpose}` | Moved with its chord and drawn, the sheet saying by how many frets. |
| A definition with an open string, transposed down by any amount | Moved up by the rest of the octave at once, as a tab is; transposed back up, it is the open shape again. |
| A definition high on the neck, transposed up past the fifteenth fret | Moved down by the rest of the octave instead. |
| A moved definition that needs a fifth finger, or leaves the first fifteen frets, in both octaves | Not drawn: the player's own shape for the chord it became, with the stepper. |
| A definition, and a modulation further down | Not moved again: a chord after it is matched by the name it is shown under. |
| The editor's transposition | Rewrites every definition it can read with the chords, and transposing back writes the line it was. |
| A definition for another instrument than the one chosen | Kept, moved and drawn in the preview like the rest; not used on the page. |
| `{define: X copy Y}`, a `{chord: Am}` with no shape | Declares no shape; a transposition renames its names. |
| A definition whose name is no chord | Left as it is by everything; the preview draws it under that name. |
| The Chord shape button with the caret in a chord already defined | No second line: the caret goes to the one there is. |
| The Chord shape button with the keyboard chosen | Writes a `keys` line. |
| German notation | Names are shown and read in it; choices and definitions are keyed in the standard one. |
| `[*annotation]`, `N.C.`, a `[Break]` in brackets | Not a chord: not listed. |
| A chord with no shape on the instrument | Its cell shows the name over an empty frame, and the sheet its notes too. |
| A slash chord on the ukulele | Drawn as the chord, since the fourth string is not the lowest; the sheet's notes still name the bass. |
| `C#` and `Db` both written in one song | Two cells, as the page names them, with one shape and one choice between them. |
| A stored shape this version's tables do not list | Drawn as it is stored, and listed first among the variations. |
| A song with one chord | A section of one cell. |
| A song with no chords | No section, as there is no transposition control. |
| More than 48 different chords | No section, and so no sheet. |
| A song cut by `LayoutBudget` | The section lists the chords of what is laid out. |
| The section taller than the screen | Paged through like any stretch that cannot be cut. |
| The song deleted or renamed with the sheet open | Closed with the song, or carried to its new name, like its other sheets. |
| Sync, export, import, device backup | The choices go where the preferences go, and into the cloud folder's `preferences.json`. A `{define}` goes where its file goes. |
| An older version on another device | Leaves `chords` in the cloud folder's document untouched, as that document promises. |
| Baseline profile journey | Opens a song, so it draws the section; nothing to add. |

## 7. Order of work

Each step builds and tests green on its own, one commit each.

1. `ChordProChords` and its tests.
2. `ChordVoicings`: the keyboard, the search, then the two tables, with their tests and the contact sheet. The tables
   are typed from what every chord chart agrees on, not copied from a database, and the sheet is looked over before the
   step is done. This is the largest step, and the one whose result is a matter of taste.
3. The four preferences, the Features switch and the instrument row.
4. `ChordDiagram`, `SongChords`, `RenderSection.Chords` and the fold, on the desktop. **Looked at on the smallest
   screen before going on** (§8): if the section is too tall there, its cell size is what gives.
5. The Chord shapes sheet and the button that opens it, with the stepper writing the choice.
6. The song's definitions read (§2.3): the parser, the model, the serializer, the highlighter, and §3's first rule for
   a song that nothing moves.
7. The definitions and the transposition (§2.4): on the model, with §3's rule for a moved shape and the sheet's line,
   and then in the text, which changes what the editor's transpose action writes for a file that has any.
8. The editor (§2.5, §5.5): the prettifier's test first, then the header's kind, the button and the preview's section.
9. The synced choices (§4.3).
10. Docs: the root `CLAUDE.md` (Features, a Chord diagrams bullet under Conventions, the Sync bullet about
    `preferences.json`, the test list), `chordpro/CLAUDE.md` (what the parser drops, what the transposition and the
    notation do with a definition, the header's kinds), `presentation/CLAUDE.md` (the editor's toolbar and preview
    among the rest), `data/model`, `data/repository/implementation`; the README's feature list.

## 8. Checks owed by hand

- **The smallest screen** (360 × 640 dp), upright and on its side: the height of the section for a song of four, eight
  and sixteen chords, on all three instruments, at the smallest and the largest text size.
- **The pages**: the demo songs and a few long ones with the section shown and folded, upright and in columns, so that
  the magazine layout still fills its pages and a pedal still reaches every line.
- **The sheet**: on the smallest phone and in a wide window, for a song of four chords and of thirty; a step followed
  by the section behind it; the button's place beside the pill at the smallest and the largest text size, and that a
  tap meant for the page turn does not land on it.
- The three instruments by a player of each: the first shape of the common chords, and that the variations are ones
  somebody would play.
- German notation, a capo with the keyboard, a song of forty chords.
- **Moved definitions**: a song with a few, read at every transposition from −5 to +6 by a guitarist, for whether the
  moved shapes are worth drawing or the rule that sets one aside (default 9) should be stricter.
- **The editor**: the button with the caret in a chord, beside one, in none and in one already defined, on each of the
  three instruments; the preview following a fret as it is typed, and a line gone wrong marked in the field; the
  transposition stepper up and back down leaving the text as it was, one undo step each; a definition typed with an
  `H` in German notation, and what Save writes; a file with definitions prettified, exported and imported again, to see
  it is still taken for the same song.
- A screen reader over the section and the sheet.
- Two devices choosing shapes for different chords and for the same one, then syncing.

## 9. Outside the repository

- `campfire-website`: the feature list, and a support answer for where the diagrams come from and how to change one.
- The store listings: a line and perhaps a screenshot, at the next release that has new ones.
- A What's new message, by the prepare-release skill.
- No permission, no network and nothing kept anywhere new: the privacy policy and every store's form stay as they are.

## 10. Not in this plan

- **A chord editor**: drawing a shape on a fingerboard instead of typing its frets, and keeping a library of one's
  own. A stored choice is already any shape rather than one of the app's, and a definition is already written, read,
  moved and drawn, so this is a screen to add and not a format to change.
- **A chord in the text showing its diagram when it is pressed**, held or hovered over. Left out as costing more than
  it gives: the chords of a line are drawn rather than composed, so a press has to be found by its position on four
  kinds of line, and a tap anywhere on the song already turns the page.
- Writing a `{define}` from the song details screen ("use this shape in this song"), which the sheet could offer with
  the editor's own line writer (§2.5).
- Moving a definition again at a modulation, so that a chord after a `{transpose}` further down keeps the shape of the
  chord it was written as.
- A different variation per song or per setlist, beyond what a `{define}` says.
- Left-handed diagrams, and other tunings and instruments (drop D, a baritone ukulele, mandolin, banjo, bass). An
  instrument is a tuning and a table, so each is data once somebody asks.
- Diagrams in the PDF, and the app's own shapes in the editor's preview. The geometry is pure so that the PDF can
  draw from it.
- `{chord}` drawing a diagram in the middle of the song, where the file puts it.
- Hearing a chord. The tuner's tone output (`documentation/plans/tuner.md`) is what would play it.
- Finger numbers for the shapes the search finds.
