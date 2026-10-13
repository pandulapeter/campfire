# Leave the PDF's tuning line out of the selectable text, so a re-import does not read it as a lyric

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`;
only with decision option (b) below also `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintDocument.kt` (the `PrintLabels.chordTuning` KDoc),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`

## Problem

Where the diagrams are drawn for a tuning other than the standard one, the PDF prints "Tuning: Drop D" above each
song's diagrams (`PrintLabels.chordTuning`, set in `ExportScreen.kt`). `PrintLayout.kt` `chordDiagramRows` at 72d48deb7
adds it with the general text helper:

```kotlin
}.let { diagramRows -> labels.chordTuning?.let { wrapped(it, detailStyle) + diagramRows } ?: diagramRows }
```

`wrapped` builds `Part(it, style = style)`, whose `isSelectable` defaults to `true`, while the diagram names right under
it are `isSelectable = false` "since read back by the importer they would be a line of chords above the song"
(`ui/print/CLAUDE.md`). The tuning line has the same problem: the importer (`ChordSheetConverter`, `:chordpro`) knows the
labelled values of the playing row (`Capo|Key|Hangnem|Tempo|Tempó|Time|Ütemmutató|Artist|Előadó…`) but not "Tuning" or
"Hangolás", so a PDF exported in Drop D and imported again gets a lyric line "Tuning: Drop D" at the top of the song. A
probe at 72d48deb7 laid out a song with `chordTuning = "Tuning: Drop D"` and diagrams on: the texts were
`(Tuning: Drop D, isSelectable = true), (G, isSelectable = false)`.

## Fix

Mark the tuning line unselectable like the names under it: map the rows of `wrapped(it, detailStyle)` to copies whose
parts are `isSelectable = false` (or give `wrapped` an `isSelectable: Boolean = true` parameter and pass `false`).
`ui/print/CLAUDE.md`, Layout paragraph, where it says the diagram names are not `isSelectable`: say that the line naming
the tuning above them (`PrintLabels.chordTuning`, only for a tuning that is not the standard one) is not either, for the
same reason. (Plan 16 also edits `ui/print/CLAUDE.md` to mention that line; whichever lands second merges the sentence.)

### Decision: should the PDF name the instrument too?

`ExportScreen.kt` prints the line only for a non-standard tuning and never names the instrument
(`chordTuning = preferences?.chordTuning()?.takeIf { !it.isStandard }?.let { … }`). A mandolin's and a ukulele's diagrams
are both four-string grids, and a banjo's five strings or a ukulele's in standard tuning carry no label at all, so a sheet
read by somebody who did not set it can be read for the wrong instrument — the reason `ui/chords/CLAUDE.md` gives for the
tuning line in the first place.

- (a) Leave it: the line names a tuning only.
- (b) **Recommended:** name the instrument whenever it is not the guitar (the six-string or the twelve-string, which is
  drawn as the guitar) and not the keyboard (whose diagram is a keyboard), and the tuning where it is not the standard
  one: "Ukulele", "Mandolin · Tuning: GDGD", while the guitar in Drop D keeps today's "Tuning: Drop D" and the guitar
  in standard tuning prints no line. Build it in
  `ExportScreen.kt` from `instrumentLabel(preferences.instrument)` (`ui/instrument/InstrumentLabels.kt`) and the existing
  `song_details_chord_tuning` string, joined with " · " the way `instrument_no_chord_diagrams` already joins an instrument
  with what follows, so no new string is needed; rename the label's KDoc in `PrintDocument.kt` to "the line naming the
  instrument and the tuning the diagrams are drawn for", and the matching sentence in `ui/chords/CLAUDE.md` ("the PDF
  prints that line above every song's diagrams").
- (c) Name the instrument always.

The unselectable fix stands on its own whichever is chosen.

## Tests

`PrintLayoutTest.kt`: lay out a song with `PrintSettings(showChordDiagrams = true)`, one `PrintChord`, and labels whose
`chordTuning` is "Tuning: Drop D" (the test's `labels` with `chordTuning = …`): the text "Tuning: Drop D" is on the page
and `isSelectable` is false, and it is above the first diagram. Without diagrams (`showChordDiagrams = false`) it is not
printed.

## Manual check

Pick Drop D (Settings → Songs, tuning chip), export a song with chords as a PDF with Chord diagrams ticked, open it in
Chrome: the "Tuning: Drop D" line is drawn above the diagrams but cannot be selected or found with Ctrl+F. Import that
PDF back into Campfire: the new song has no "Tuning: Drop D" line. With option (b), export with the mandolin picked: the
line reads "Mandolin".
