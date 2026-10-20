<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# On iOS, play a tone that nothing is listening beside in the `playback` category, not `playAndRecord` / `measurement`

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** iOS
**Files:** `tuner/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/tuner/implementation/IosTunerSession.kt`,
`tuner/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/tuner/implementation/IosToneOutput.kt`,
`tuner/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/tuner/implementation/IosAudioInput.kt`,
`tuner/implementation/CLAUDE.md`

**Decision:** switch a tone heard alone to `playback`, or accept that a scale is quieter for users who granted the
microphone than for those who did not — recommended: switch (below; the session code allows it safely).

## Problem

`IosTunerSession.acquire()` picks the category from the permission alone, for every user, every time
(`IosTunerSession.kt:51-64` at 396acf923):

```kotlin
val isSet = if (isRecordingAllowed) {
    session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionModeMeasurement, AVAudioSessionCategoryOptionDefaultToSpeaker, error.ptr)
} else {
    session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, error.ptr)
}
```

So once the microphone has been granted (for the Tuner tab, once), the scale sheet's notes — which never listen — play
through `playAndRecord` in `measurement` mode, which leaves out the system's output processing and is markedly quieter on
the built-in speaker than the `playback` category that users without the permission, and the metronome, get. The same
scale is louder or quieter depending on an unrelated permission.

## Fix

Choose the category by who is using the session, not by the permission:

- Replace `acquire()` with two entry points (or one taking `isInput: Boolean`): `acquireForInput()` sets
  `playAndRecord` / `measurement` / `defaultToSpeaker` (the input calls it only when recording is allowed, as
  `IosAudioInput.start` already checks); `acquireForTone()` sets `playback` / default only when `users == 0`, and when
  the session is already held (the input listening on the Tuner tab or the tuner sheet) only counts and keeps the
  category it has — a reference tone played while listening stays in `playAndRecord`, exactly as today, and the
  speaker-to-microphone muting in `TunerEngine` is unchanged. `setActive(true)` is still called each time (idempotent).
- `IosToneOutput.start` calls `acquireForTone()`; `IosAudioInput` calls `acquireForInput()`. `release()` is unchanged.
- The one order that switches a sounding tone's category is the input joining a tone that holds `playback` (the input
  opening while a reference tone plays). `setCategory` on an active session reconfigures the route, which the tone's
  engine reports as `AVAudioEngineConfigurationChangeNotification`; `IosToneOutput.observe` then ends the tone
  (`lose`), and the engine shows it stopped. That already happens today to a user without the permission who grants it
  while a tone sounds, the screens start listening before any tone can be tapped, and the scale sheet never listens;
  accept it rather than add a second path. Say so in the class KDoc.
- Rewrite `IosTunerSession`'s KDoc: `playAndRecord` / `measurement` while the input uses it (a tone joining the input
  keeps it), `playback` for tones heard alone — louder on the built-in speaker, and never a recording category without
  permission; an input joining a tone switches the category, which ends that tone.

`tuner/implementation/CLAUDE.md`, iOS bullet: replace "`playAndRecord` / `measurement` / `defaultToSpeaker`, no
Bluetooth option, only when record permission is granted; otherwise `playback` for a tone (touching a recording
category without permission makes the system prompt)" with "`playAndRecord` / `measurement` / `defaultToSpeaker`, no
Bluetooth option, while the input holds it (a tone joining keeps it); a tone alone takes `playback`, louder on the
speaker and never a recording category without permission (which makes the system prompt); an input joining a tone
switches the category, which ends the tone".

## Tests

None can be written: the session and its categories are `AVAudioSession`, iOS only, and the module tests only
`commonTest` on the desktop. Compile with `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`.
