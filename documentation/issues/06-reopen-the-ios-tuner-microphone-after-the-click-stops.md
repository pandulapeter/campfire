<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Reopen the iOS tuner's microphone when the click's stop deactivated the shared audio session

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** iOS
**Challenged:** amended — option A now detects a dead input by no tap for 1 s rather than `!engine.running` (not documented to flip on a session deactivation, and an immediate check races a call's interruption notice, turning MICROPHONE_BUSY into a reopen); `TunerEngine` unchanged; the proposed engine test already exists, so no new commonTest; `IosToneOutput` checked and left alone.
**Files:** `tuner/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/tuner/implementation/IosAudioInput.kt`,
`tuner/implementation/CLAUDE.md` (one sentence in the iOS bullet), and only for option B
`metronome/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/metronome/implementation/IosAudioOutput.kt`

## Problem

Reviewed at `6c3043996`; found by reading the code against Apple's documented behaviour, not reproduced on a device.

The metronome and the tuner share the one `AVAudioSession`. The tuner counts its users (`IosTunerSession.acquire` /
`release`, deactivating only at zero); the metronome deactivates unconditionally on every stop:

```kotlin
// IosAudioOutput.kt (metronome)
override fun stop() {
    ...
    player.stop()
    engine?.stop()
    engine = null
    deactivateSession()   // setActive(false, NotifyOthersOnDeactivation)
}
```

The two engines run on separate `limitedParallelism(1)` queues, so nothing orders them. Opening the tuner sheet over a
playing click calls `metronome.stop()` (`CampfireViewModel`'s `addBeforeDialogChange`); moving from the Metronome tab to
the Tuner tab within 1.5 s of a sound preview does the same. On the next frame `TunerListeningEffect` calls
`tuner.listen`, which activates the session and starts the input engine. `AVAudioEngine.stop()` commonly takes tens of
milliseconds, so the metronome's `setActive(false)` can land after the tuner started. Apple documents that deactivating
a session with running I/O stops that I/O (the call reports `IsBusy` but the audio is still stopped). The tuner's input
then delivers nothing; after 0.5 s `TunerEngine.isStalled` treats it as silence, and with no configuration-change
notification no error is shown: the sheet listens forever and never reads a note until it is closed and reopened.

## Fix

**Option A (recommended, local to the tuner):** the iOS input notices it has stopped delivering and asks to be reopened.
In `IosAudioInput`, keep the `listener` given to `start`, a `TimeMark` of the last time `ring.latest` answered a new
position (set on `start`, after `ring.clear()`), the last position, and an `isLossReported` flag cleared on `start`.
In `latest`, if `engine` is non-null, the position has not moved for `STALL_REOPEN` (1 s) since that mark, and the
flag is clear: set it and call `listener.onLost(TunerStopReason.MICROPHONE_DISCONNECTED)`. Measured from `start`, so an
input that never delivered its first tap is caught too, which is the race itself. Keep `latest` allocation-free
(`TimeSource.Monotonic.markNow()` is a value class; a held `TimeMark` field is fine).

Why a stall and not `engine.running`, as first proposed:

- Apple documents that the deactivation stops the session's I/O. It does not document that `AVAudioEngine.isRunning`
  turns false when that happens. A tap that stops arriving is the symptom the user actually has, whatever the engine
  reports.
- The input already has notices for a call (`MICROPHONE_BUSY`), a new route (`MICROPHONE_DISCONNECTED`), and a media
  services reset (`FAILED`). Each one stops the engine, and each notice calls `stop()`, which nulls `engine`. An
  immediate `!engine.running` check polled every 33 ms can see the stopped engine before the call's notice is
  delivered. It would then reopen during the call instead of showing busy, and if `setActive(true)` succeeded there it
  would spend the one reopen on a deaf input. Waiting 1 s, which is far above the ≤ 400 ms an iOS tap can be late
  (`TunerEngine`'s hold) and far longer than these notices take, lets them win.
- By then the metronome's late `setActive(false)` (tens of ms) has long landed, so the reopen cannot be undone by it.

Safe to call from `latest`: `latest`, `start`, and `stop` all run on the tuner's one confined coroutine, so `engine`,
the mark, and the flag need no lock. `onLost` only launches onto that coroutine (`onEngine`), so it does not reenter.
Polling continues until that launch runs `closeInput`; the flag keeps it to one report.

Engine side, no change: `onLost(MICROPHONE_DISCONNECTED)` with `canReopen` (true from `listen`) closes and reopens
once. `stop()` releases the session, and the new `start` calls `IosTunerSession.acquire()`, which calls `setActive(true)`
every time, not only from zero users, so it reactivates the session even while a tone holds it. If the reopened input
also stalls, `canReopen` is false (re-armed only after 2 s of fresh windows), so the second report stops the tuner as
`MICROPHONE_DISCONNECTED`, an error shown with a retry instead of deaf listening. If the reopen is refused (busy,
failed), that reason is shown. Either outcome beats the current silent sheet.

`IosToneOutput` (tuner tones) shares the session, but a tone starts only on a tap on a string, well after the click's
stop has settled. A tone silenced some other way is heard as silence and tapped again, so it is left alone.

**Option B (crosses modules):** give both modules one shared session counter so the metronome never deactivates a session
the tuner holds. Neither module may depend on the app or the other, so it would need a new tiny iOS-only module or a
duplicated Objective-C-level check; more structure for the same outcome.

Add one clause to the iOS bullet of `tuner/implementation/CLAUDE.md`: an input that delivers no tap for 1 s reports
itself disconnected (and is reopened once), since another audio user's deactivation stops it with no notice.

## Tests

The engine side is covered already: `TunerEngineTest`'s `a disconnected input is opened again once` and `a reopened
input that stopped delivering is not opened again`. The stall check lives in `iosMain` and cannot run off a device, so no
new commonTest.
