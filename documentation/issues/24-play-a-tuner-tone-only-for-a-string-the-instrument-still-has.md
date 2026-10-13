# Play a tuner tone only for a string the instrument in the player's hands still has

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the guard moved out of `TunerController.toggleTunerTone`, which the Tuner tab's reference tone
button also calls (`viewModel.toggleTunerTone(REFERENCE_NOTE)`, A4): guarded there, the A4 button would play nothing in
chromatic mode and in every tuning without an A4 string (standard guitar among them). It now sits in
`TunerStringsSetting`, the one way into the strings rows on both the tab and the sheet.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerStringsSetting.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunedStrings.kt` (or a new small pure file beside it),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunedStringsTest.kt` (or a matching new test),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

The strings row keeps the second row of a paired instrument while it shrinks away, drawn as it was
(`tuner/TunerStrings.kt` at 72d48deb7):

```kotlin
val lastPartners = remember { LastPartners() }
partners?.let { lastPartners.value = it }
AnimatedVisibility(visible = partners != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
    StringRow(notes = lastPartners.value.orEmpty(), …, onToggleTone = onToggleTone)
}
```

and the whole strings setting does the same as it leaves for chromatic mode (`TunerStringsSetting` →
`AnimatedSettingsRow`, which keeps its last value while shrinking). Drawing what is leaving is intended; but the
segments stay tappable, and the tap goes straight to the tone (`TunerController.kt`):

```kotlin
fun toggleTunerTone(note: Int) {
    if (tuner.state.value.tone == note) tuner.stopTone() else tuner.playTone(note, tunerSettings.value.referencePitch)
}
```

So Mandolin → Guitar and a tap on the shrinking pair row plays a mandolin string's note on a guitar's tuner, and a tap on
the row fading out after Chromatic is switched on plays a string in chromatic mode, where `ui/tuner/CLAUDE.md` says the
strings are "Gone". `startFollowingSettings` stops a tone whose strings changed, but only on a change of config, so a
tone started after that change keeps sounding. (The other half of the review note — 12-string → Mandolin swapping the
pair row's notes in one frame — is not a defect: the first row swaps its notes in the same frame, the instrument having
changed, and both rows read as the new instrument at once.)

## Fix

Guard the tone where the strings rows hand a tap on, so every leaving row on both the tab and the sheet is covered at
once, and the reference tone button (`ReferencePitchSetting`, which calls the same `toggleTunerTone` with A4, a note of
no string in chromatic mode or in standard guitar tuning) is left alone:

1. Add a pure function, e.g. in `TunedStrings.kt`: `internal fun isStringOf(config: TunerConfig, note: Int) =
   config.strings?.contains(note) == true` (`TunerConfig.strings` is every string listened for, both of each course,
   null in chromatic mode).
2. `TunerStringsSetting.kt`: its `config` and `tone` parameters are the current ones (the `AnimatedSettingsRow` lambda
   only keeps the leaving `tuning` and `partners` for drawing), so wrap the callback handed to `TunerStrings`:
   ```kotlin
   val currentConfig by rememberUpdatedState(config)
   val currentTone by rememberUpdatedState(tone)
   // A row leaving with its instrument, or for chromatic mode, is drawn as it was but plays nothing: only a string the
   // tuner listens for now starts a tone, and the one sounding can always be stopped.
   val onToggleString: (Int) -> Unit = remember(onToggleTone) {
       { note -> if (note == currentTone || isStringOf(currentConfig, note)) onToggleTone(note) }
   }
   ```
   and pass `onToggleTone = onToggleString` to `TunerStrings`. Read through `rememberUpdatedState`, since the leaving
   content of the animated row is not guaranteed to be recomposed with the newest parameters. `TunerController` keeps
   `toggleTunerTone` as it is; `startFollowingSettings` still stops a tone whose strings changed.
3. `ui/tuner/CLAUDE.md`, the `TunerStringsSetting` / `TunerStrings` bullet: add that a row leaving with its instrument (or
   for chromatic mode) is drawn as it was but plays nothing.

Optional, for the screen reader: skip the `onClick` semantics of a leaving `StringRow` (pass an `isLeaving` flag from the
`AnimatedVisibility`), so it is not announced as buttons while it shrinks; the guard is what fixes the sound.

## Tests

`isStringOf`: with `TunerConfig(440, InstrumentTuning.MANDOLIN, courses = …)` (the mandolin's courses, as
`tunerConfigOf` builds it) a mandolin string's note and its partner are strings, a guitar note that is not (e.g. the
low E, MIDI 40) is not; with `tuning = null` (chromatic) nothing is.

## Manual check

Tuner tab, Mandolin picked, Chromatic off. Pick Guitar from the instrument chip and tap the second row while it
shrinks: nothing sounds. Pick 12-string guitar, then switch Chromatic on and tap a string while the row fades: nothing
sounds. With Chromatic off again, tapping a string still plays and a second tap stops it. With the guitar in standard tuning,
and again with Chromatic on, the reference pitch's A4 button still plays and stops its tone.
