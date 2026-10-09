# Give the tuner display a description when nothing is heard and while a reference tone plays

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — TunerState is in another module, so state.tone cannot smart-cast; bind it to a local first
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerDisplay.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

`TunerDisplay` replaces the semantics of everything it draws with one description (`tuner/TunerDisplay.kt:69` and
`:73-78`, `:102-105` at b5c8ed3b5):

```kotlin
val reading = (state.listening as? TunerListening.Hearing)?.reading?.takeIf { state.tone == null }
…
val description = when {
    reading == null -> null
    …
}
…
.clearAndSetSemantics {
    description?.let { contentDescription = it }
    …
},
```

`reading` is null both while nothing is heard and while a tone sounds (`takeIf { state.tone == null }`), and then no
description is set — while `clearAndSetSemantics` has also cleared the visible "Play a note" (`tuner_play_a_note`) and
"Reference tone" (`tuner_reference_tone`) texts the display draws. A screen reader focusing the display hears nothing
(TalkBack may skip the node entirely), even though a sighted user reads a clear prompt or the name of the tone.

## Fix

Fill the two missing branches of the description. If plan 52 has landed this is its `exactDescription` (the explored,
non-live description); otherwise the same `description`.

```kotlin
val description = when {
    state.tone != null -> stringResource(Res.string.tuner_reading_tone, noteNameWithOctave(state.tone, notation))
    reading == null -> stringResource(Res.string.tuner_play_a_note)
    isInTune -> …                      // unchanged
    reading.cents < 0 -> …             // unchanged
    else -> …                          // unchanged
}
```

and set it unconditionally: `contentDescription = description` (it is no longer nullable; drop `description?.let`).
**Challenged:** `TunerState` is declared in `:tuner:api`, another module, so `state.tone` does not smart-cast after the `!= null` check (Kotlin refuses a smart cast on a public property of another module); bind it first — `val tone = state.tone` beside `reading` (or `tunerState.tone` once plan 58 lands) and `tone != null -> stringResource(Res.string.tuner_reading_tone, noteNameWithOctave(tone, notation))`.

New string, in the tuner block of both files next to `tuner_reference_tone`:

```xml
<!-- The display as a screen reader reads it while a reference tone sounds, e.g. "Reference tone, A4". -->
<string name="tuner_reading_tone">Reference tone, %1$s</string>
```

```xml
<!-- The display as a screen reader reads it while a reference tone sounds, e.g. "Reference tone, A4". -->
<string name="tuner_reading_tone">Referenciahang, %1$s</string>
```

(No existing string carries the note: `tuner_reference_tone` is "Reference tone" alone, and `tuner_play_reference` is
the chip's action.) The note name is the app's own, so `stringResource` is right. Plan 55 swaps in the spoken name.

If plan 52 has landed, its live node stays silent for these two states on purpose: a tone is started by a tap the
reader has just heard, and "Play a note" every time a string fades would be the noise plan 52 removes.

Docs: `ui/tuner/CLAUDE.md`, the `TunerDisplay` / `TunerMeter` bullet: add "With nothing heard it is read as the prompt,
and while a tone plays as that tone."

## Tests

None: a Composable's description; the strings are not logic.

## Manual check

TalkBack and VoiceOver: open the Tuner tab with the microphone allowed and the room quiet; focusing the display reads
"Play a note". Choose Guitar, tap the E2 string chip, focus the display: "Reference tone, E2". Hungarian in-app:
"Szólaltass meg egy hangot", "Referenciahang, E2".
