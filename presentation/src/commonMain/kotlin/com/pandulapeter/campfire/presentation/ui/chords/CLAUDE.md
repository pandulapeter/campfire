<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/chords

Chord diagrams. Nothing is shipped or fetched: `:libs:chordpro` reads what notes a name means and finds shapes (a
hand-typed table first, a search for the rest).

### Instruments

- Guitar (and the twelve-string, drawn as a guitar), ukulele, mandolin, banjo, and keyboard draw diagrams; the rest
  draw none, as if Chord diagrams were off. The instrument pill heads the first section of every song with chords, even
  an instrument without shapes, so the diagrams can be brought back.
- **A chord is drawn as it is fingered** (`ui/playing/CLAUDE.md`): `SongChord.chord` is the sounding chord (notes,
  degree, scales), named as it sounds or, where the reader names shapes, as its shape (key headings too);
  `shapeChord` is the shape played above the capo, by which a shape is looked up, picked, and stored. A `{define}` is
  moved down by the capo as a transposition moves one, so a file's shape comes back to where it was written under any
  capo, moved by the reader's transposition alone.
- The diagram draws the shape where it is played, frets numbered from the nut; the capo is a muted bar across the
  window's first row where the shape sits right above it, its open strings ringed on the bar (they ring at the capo),
  bar and string clipped out inside each ring so the card or page shows through; outside the window where it is higher.
  The keyboard has no capo.

### Which shape a chord is drawn with (`selectShape`, tested)

1. The song's own `{define}` / `{chord}` for that instrument (and tuning, below).
2. The player's choice, **one per chord and instrument for the whole library** (a habit of the hands, not a reading of
   one song), under the tuning's key, stored as the shape rather than its index and keyed by the chord's notes so every
   spelling shares it; synced. Used only while it still reads as a shape of that instrument.
3. The app's first shape.

- A definition for another instrument is neither used nor translated (a guitar shape is another chord on a ukulele;
  `chordpro/voicings/CLAUDE.md`).
- A definition follows its chord through every transposition in whichever octave a hand can hold; one left needing a
  fifth finger gives way to the player's shape.
- **A definition is a standard tuning shape**: ChordPro gives `{define}` no tuning, so it is drawn only where a standard
  shape sounds right, else offered behind a button in the chord's sheet.

### Tunings

- One tuning per instrument, device-only; an unknown or foreign id is standard. **Every tuning the tuner knows for an
  instrument with shapes is one the diagrams know** (`InstrumentsTest`, strings included).
- **A chord name always names the chord that sounds**: half step down, an A is drawn `x 1 3 3 3 1`. Player choices made
  in a tuning are kept under its own key.
- On a tuning change the page keeps its cells until chords are re-collected off the main thread, so it crossfades once,
  never drawing old shapes on new strings. The player's tuning's table is read ahead of the first song's first page.

### The Chords section

- **Grouped by key** (`groupedByKey`, tested): where chords are in more than one key, the home key's first, then each
  key a key change reaches in order, every group starting a row under a "Key: A♭" line set as a label, not a chord (the
  PDF's too). With or without degrees, never in a songbook.
- Every chord once however spelled, in first-played order, read *before* notation is applied (a numbering's step means
  nothing without its key), named as the page names it. **More than `MAX_SONG_CHORDS` (48) is a songbook: no section.**
- A page's first model is built without the shapes only the search finds (empty frames); those follow off the main
  thread, keeping the same sections so only the Chords section re-measures.
- **Cut between rows like any section** (slots, `ChordRows.kt`, tested). Rows are balanced (nine where six fit is five
  over four), the PDF's too.
- **Each cell names its chord's degree** in Roman numerals (`V7`, `♭VII`), counted from the scale suggestions' home,
  else the sounding `{key}` by the capo chart rule, else none. A chord first played after a key change, directed or
  written out, counts from the key it moved to, as a numbering names it; a chromatic chord by its step (`II7`, never
  `V/V`); none in a numbering. The degree stands on a line over the name (three quarters size, normal weight), a line
  every cell keeps where any has one. Only here and the PDF's diagrams: lyrics and the chord sheet keep their names.
- Folded per song (`CHORDS_FOLD_KEY`), read only mode included.
- **Every cell opens its chord's sheet** outside read only mode (an import preview's too). The press highlight waits
  for the tap timeout (immediate for a mouse), so a scroll started on a diagram never lights it; the tap is consumed so
  it doesn't turn the page. A screen reader gets one custom action per cell.
- A cell whose chord changes shape (picked, retuned, capo moved) crossfades; a searched shape arriving into an empty
  frame, or a cell now another chord, does not.
- Diagrams: at least four frets, up to 24; the root in the second accent; a keyboard of two to four octaves.

### The chord sheet

- Finds the chord by name in the song as the page plays it, and closes if it is gone. Shapes are searched before it
  opens, so it slides up whole; a stored shape the app doesn't list goes first.
- A tap writes the player's choice at once; picking the app's first shape removes the entry. No reset button. A chord
  the song defines shows its definition alone, not pickable.
- Under a capo the title names the chord as the page does, a line under it the shape and capo (`G shape, capo 2`) or
  what it sounds as where the title names the shape (`Sounds as A, capo 2`), the capo alone in a numbering; every shape
  is drawn above the capo, a pick stored for the shape's chord. Its scales open with the capo. The Chords tab's sheet
  does the same under the tabs' capo.
- Both chord sheets end in the scales on the chord's root holding its tones, while Scales and Scale suggestions are on.
  **One hop only**: a chord sheet from a scale sheet offers no scales, a scale sheet from a chord sheet no chords.
