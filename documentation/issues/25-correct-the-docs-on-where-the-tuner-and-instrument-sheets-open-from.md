# Correct the KDoc on where the tuner and instrument sheets are opened from, and on what can close the Chords tab's sheet

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the note on `DialogType.kt` named an uncommitted change that has since landed in `153886ec6`;
the quoted KDocs still match HEAD, and the fix is unchanged.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/TunerSheet.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogType.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordReferenceSheet.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/ChordShapeSheet.kt` (one comment)

## Problem

The instrument and tuning chips moved to the top of the song's first section, the tuner sheet gained a button there,
and the instrument stopped being synced; these comments at 72d48deb7 still describe the earlier state:

1. `TunerSheet.kt`, the `TunerSheet` KDoc: "The tuner over the song being read, opened from the song details overflow
   menu, …" and "The instrument is the app's one, set from the instrument chip of the Tuner tab, the Chords tab,
   Settings, or the song's own Chords section". The sheet is opened from the tuner button of the song's first section,
   and from the overflow menu only where that section is not there (`ui/tuner/CLAUDE.md`, `TunerButton` /
   `tunerAction`); the chips head the first section, not a Chords section, and the onboarding's instrument page sets the
   instrument too.
2. `DialogType.kt`, `Tuner`: "The tuner over the song being read, opened from the song details overflow menu; …" — same
   as above.
3. `DialogType.kt`, `Instrument`: "opened from the instrument chip of every screen that depends on it (the song details
   screen's Chords section, the Tuner tab, the Chords tab, and Settings)" — the first section, and the onboarding.
4. `ChordReferenceSheet.kt`, the KDoc: "a preference synced in meanwhile that leaves no instrument closes it". The
   instrument and its tuning are "kept on the device and never synced" (`ui/CLAUDE.md`, One instrument for the whole
   app); what the guard (`userPreferences?.chordTuning() ?: return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }`)
   covers is an instrument without shapes, which nothing on screen can pick while the sheet covers the tab.
5. `ChordShapeSheet.kt`: "The sheet is opened from a song's Chords section, which Other leaves out, so only a preference
   changed while it was open gets here without a tuning" — every instrument without shapes leaves the Chords section out,
   not only Other (the basses, the seven- and eight-string guitars, and the violin too).

## Fix

1. `TunerSheet.kt`: "…opened from the tuner button of the song's first section (or its overflow menu, where the chords
   and the metronome are both switched off and that section is not there), …" and "set from the instrument chip of the
   Tuner tab, the Chords tab, Settings, the onboarding, or the chips at the top of the song's first section".
2. `DialogType.kt` `Tuner`: "…opened from the song's tuner button (or its overflow menu); it listens while it is up."
3. `DialogType.kt` `Instrument`: "(the chips at the top of the song details screen's first section, the Tuner tab, the
   Chords tab, Settings, and the onboarding)".
4. `ChordReferenceSheet.kt`: "…which nothing can change while the sheet covers the tab; an instrument without shapes,
   which leaves the tab nothing to draw, closes it."
5. `ChordShapeSheet.kt`: "The sheet is opened from a song's Chords section, which an instrument without shapes leaves
   out, so only an instrument changed while it was open gets here without a tuning; …"

Plan 20 changes the code of `ChordShapePager.kt` and `ChordsScreen.kt`, not these KDocs, so the two do not overlap.
`DialogType.kt`'s `@ConsistentCopyVisibility` on `ChordReference` (and the blank line before its KDoc) landed in
`153886ec6`; edit only the two KDocs named, which that commit left as quoted above.

## Tests

None: comments only.

## Manual check

None: read each comment against `ui/tuner/CLAUDE.md` (TunerButton), `ui/CLAUDE.md` (One instrument for the whole app),
and `screens/songDetails/SongInstrumentChips.kt`.
