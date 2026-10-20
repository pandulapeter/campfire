<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Let the tuner report a tone the output refused, and end a scale run at the first refused note

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (Android, iOS, and desktop refuse in practice)
**Challenged:** amended — step 2 offered two state writes as an example of publishing "in one state"; it now names the one write (`toneEndedAt` marked as `setTone` does, then a single `copy(tone = null, refusedTones = + 1)`), so no collector ever sees the tone gone without its refusal.
**Files:** `tuner/api/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/api/model/TunerState.kt`,
`tuner/api/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/api/Tuner.kt`,
`tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngine.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerController.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerControllerTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`, `tuner/api/CLAUDE.md`

Independent of plans 03 and 04 (they change the outputs, not what the engine publishes).

## Problem

The scale run waits for each note to sound (`TunerController.kt:146-156` at 396acf923):

```kotlin
tuner.playTone(note, tunerSettings.value.referencePitch)
withTimeoutOrNull(SCALE_NOTE_START_TIMEOUT_MILLIS) { tuner.state.first { it.tone == note } }
delay(if (index == notes.lastIndex) SCALE_LAST_NOTE_MILLIS else SCALE_NOTE_MILLIS - SCALE_NOTE_LEAD_MILLIS)
```

When the output refuses (`ToneOutput.play` returns false: Android focus refused during a call, an iOS session that
will not activate, a desktop line that is unavailable), the engine only sets `tone = null`
(`TunerEngine.playTone`: `setTone(note.takeIf { isPlaying })`). For the first note that is no change at all, so nothing
tells the run the note will never come: every note waits the full 2 s timeout (`SCALE_NOTE_START_TIMEOUT_MILLIS`), and
an 8-note scale crawls its circle through the formula for ~18 s in silence, the play button showing Stop throughout.
`tone == null` alone cannot be the signal, since it is also the state before a note that is still on its way.

## Fix

1. `:tuner:api` (explicit API): add to `TunerState`
   `public val refusedTones: Int = 0`, with a `@param` line in its KDoc: how many tones the platform refused to start
   since the tuner was made; it only grows, so a caller that just asked for a tone can tell that tone's refusal from
   one still on its way. Extend `Tuner.playTone`'s KDoc: a tone the platform will not play is never shown as
   [TunerState.tone] and counts in [TunerState.refusedTones].
2. `TunerEngine.playTone`: when `output.play` returns false, publish `tone = null` and `refusedTones + 1` in one
   assignment, marking `toneEndedAt` first where a tone was sounding, as `setTone` does (a sounding tone that fails to
   be replaced still leaves its tail unread): e.g. give `setTone` a `refusedTones: Int = _state.value.refusedTones`
   parameter written in the same `copy`, or write the refusal branch out. Not `setTone(null)` followed by a second
   `copy`: a collector would see the tone gone with no refusal yet.
3. `TunerController.toggleScale`: read the count before asking, and end the run when the answer is a refusal:

   ```kotlin
   val refusals = tuner.state.value.refusedTones
   tuner.playTone(note, tunerSettings.value.referencePitch)
   val answer = withTimeoutOrNull(SCALE_NOTE_START_TIMEOUT_MILLIS) {
       tuner.state.first { it.tone == note || it.refusedTones != refusals }
   }
   if (answer != null && answer.tone != note) {
       // The output will not play: every later note would be refused the same way, in silence.
       _playingScaleNote.value = null
       return@launch
   }
   ```

   Keep the timeout for an output that is only slow to open (the run then goes on, as today), and update the KDoc's
   "A note the output never starts (focus refused) is waited for only so long, then the run goes on" to say a refused
   note ends the run and one slow to start is waited for only so long. The engine already published `tone = null`, so
   the run needs no `stopTone()` there; `scaleJob` is left to finish on its own (do not call `stopScale()` from inside
   the job, which would cancel itself). Since the run reads the count it set off itself and is the only thing playing
   tones while the scale sheet is up, no other refusal can be mistaken for its own.
4. `ui/tuner/CLAUDE.md` Lifecycle, the scale bullet: add "a note the platform refuses ends the run (`refusedTones`)".
   `tuner/api/CLAUDE.md`: one bullet, "`TunerState.refusedTones` only grows, so a caller can tell its tone's refusal from
   one still on its way."

No message is shown for the refusal, as none is for a refused reference tone on the Tuner tab today; the button simply
turns back to Play.

## Tests

- `TunerEngineTest` (`commonTest`, desktop): `` `a refused tone is counted and never shown` `` — with
  `output.isPlayable = false`, `playTone` then `runCurrent()`: `state.tone` is null and `refusedTones` is 1; a second
  refused call makes it 2; a played one leaves it unchanged.
- New `presentation/src/commonTest/.../ui/tuner/TunerControllerTest.kt`, building `TunerController` with
  `scope = backgroundScope` in `runTest`, `userPreferences = MutableStateFlow(null)`, a no-op
  `UpdateUserPreferencesUseCase` (as `ExportControllerTest` stubs it), `writeDelayMillis = 0L`, and a fake `Tuner`
  whose `state` is a `MutableStateFlow(TunerState())` and whose `playTone` records the note and either publishes
  `tone = note` or, when refusing, bumps `refusedTones`:
  - `` `a scale whose output refuses its first note ends at once` ``: after `toggleScale(listOf(60, 62, 64))` and
    `advanceTimeBy(500)`, `playingScaleNote` is null and only 60 was asked for.
  - `` `a scale plays every note when the output starts them` ``: all three notes asked for in order, and
    `playingScaleNote` null once the run is over (`advanceUntilIdle`).
- `./gradlew :tuner:implementation:desktopTest :presentation:desktopTest`.
