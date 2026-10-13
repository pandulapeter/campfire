# Correct the docs on where the instrument chips are, what withTuning does, and the PDF's tuning line

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyricsModel.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt` (KDoc only)

## Problem

Since the instrument and tuning became two chips heading the song's first section, four places
still describe the old single chip in the Chords section, or a `withTuning` that no longer behaves that way, and the PDF notes leave a line out:

1. `ui/chords/CLAUDE.md`, "The diagrams are drawn for the tuning the player has": "One tuning per instrument
   (`UserPreferences.tunings`, written by `withTuning`, which picks the tuning's instrument too, …". `withTuning`
   (`ui/instrument/Instruments.kt`) now only tunes the instrument picked:
   ```kotlin
   internal fun UserPreferences.withTuning(tuning: InstrumentTuning) = when (tuning) {
       !in instrument.tunings -> this
       instrument.tunings.first() -> copy(tunings = tunings - instrument.id)
       else -> copy(tunings = tunings + (instrument.id to tuning.id))
   }
   ```
   (`ui/CLAUDE.md` already says so: "`withTuning` tunes the instrument picked".)
2. `ui/chords/CLAUDE.md`, the next paragraph: "The Chords section's chip names a tuning that is not the standard one after
   its instrument ("Guitar · Drop D"), the chord sheet says it under the title ("Tuning: Drop D") and the PDF prints that
   line above every song's diagrams". There is no Chords section chip any more: the tuning has its own chip after the
   instrument's at the top of the first section, which names every tuning, the standard one as "Standard tuning" in full
   (`components/InstrumentChips.kt`).
3. `SongLyricsModel.kt`, the `hasChords` KDoc: "…which [chords] does not say where there was no tuning to collect them for
   (an instrument without shapes): the section is still there then, as the instrument chip alone." Without a tuning there
   is no Chords section at all; what stays is the pair of chips at the top of the metadata section
   (`withChordsSection`, `RenderSection.Metadata.hasInstrumentChips`).
4. `SongDetailsScreen.kt`, `rememberChordDiagrams` KDoc: "An instrument without shapes still gets one, with no tuning to
   draw in, so that the section keeps the chip that changes it." Same: it keeps the chips at the top of the first section.
5. `ui/print/CLAUDE.md` never mentions the tuning line (`PrintLabels.chordTuning`), which the Layout paragraph's
   description of the diagrams should name.

## Fix

1. `ui/chords/CLAUDE.md`: "written by `withTuning`, which tunes the instrument picked and ignores a tuning that is not
   one of its own, read by `tuningOf` …".
2. `ui/chords/CLAUDE.md`: replace the sentence with: "The tuning chip after the instrument's at the top of the song's
   first section names the tuning, the standard one as "Standard tuning", the chord sheet says one that is not the
   standard one under its title ("Tuning: Drop D"), and the PDF prints that line above every song's diagrams, so …"
   (keep the rest). If plan 13's option (b) has landed, say the PDF's line names the instrument too.
3. `SongLyricsModel.kt`: "…(an instrument without shapes): the instrument and tuning chips still head the song's first
   section then, since they are what brings the diagrams back."
4. `SongDetailsScreen.kt`: "…so that the first section keeps the chips that change it."
5. `ui/print/CLAUDE.md`, Layout paragraph, after the sentence on the diagrams' names: "Above a song's diagrams a line
   names the tuning where it is not the standard one (`PrintLabels.chordTuning`), so a sheet is not read for the wrong
   strings." (Plan 13 adds that it is unselectable; merge the two into one sentence if it landed first.)

Plan 11 renames `hasInstrumentChips`; if it has landed, write `instrumentChips` in item 3. Plan 15 edits the code under
the KDoc of item 3; the two do not overlap.

## Tests

None: comments and docs only.

## Manual check

None: read the edited paragraphs against `Instruments.kt`, `InstrumentChips.kt`, `ChordCells.kt`, and `PrintLayout.kt`.
