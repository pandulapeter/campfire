<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep Android's audio focus while one tone replaces another, giving it back only on a stop or a loss

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android
**Challenged:** amended — a loss arriving while the next note's `play` is queued on the engine called the old tone's `onLost`, which the engine then ignored as stale, and the new `play` kept the request it no longer held, so that note sounded on over a call with nothing to stop it; the listener now marks the focus as lost, `play` sets the current `onLost` before reading that mark and asks again when it is set.
**Files:** `tuner/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/tuner/implementation/AndroidToneOutput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

Every `play` begins with `stop()`, which abandons the focus, and then asks for transient focus again
(`AndroidToneOutput.kt:46-53` and `:87-97` at 396acf923):

```kotlin
override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
    stop()
    val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        ...
    if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
```

```kotlin
override fun stop() {
    val track = track ?: return
    this.track = null
    abandonFocus()
    ...
```

The scale sheet replaces the tone every 250 ms, so an eight-note scale abandons and re-requests focus eight times in
~2.4 s. Another app holding focus (a music player paused for the first note) is sent `AUDIOFOCUS_GAIN` and then
`AUDIOFOCUS_LOSS_TRANSIENT` between every pair of notes, so it may resume and pause between them; each round trip is
also a fresh chance for the request to be refused mid-scale.

## Fix

- Split the track's fade-out from the focus: a private `fadeOutTrack()` holding today's `stop()` body minus
  `abandonFocus()`; `stop()` becomes `fadeOutTrack()` then `abandonFocus()`.
- The focus listener must call the onLost of the tone sounding now, not of the one that requested focus: the engine
  ignores a stale tone's `onLost` (`TunerEngine.playTone` checks `toneId == toneSession`), so a loss during the third
  note would otherwise be dropped. Keep a `@Volatile private var onLost: () -> Unit = {}` and a
  `@Volatile private var isFocusLost = false`, and build the request's listener as
  `{ change -> if (change < 0) { isFocusLost = true; this.onLost() } }` (`play` runs on the engine's thread, the
  listener on the main looper, hence the volatiles).
- `play`, in this order: `this.onLost = onLost`; `fadeOutTrack()`; then, if `focusRequest == null || isFocusLost`,
  `abandonFocus()`, `isFocusLost = false`, and request focus as today (refused: `return false`); a held request that
  was not lost is kept as it is. The order is what closes the race between a loss and the next note: the engine posts
  a loss to its own thread, so the scale's next `playTone` can run before it and the loss then reaches the old tone's
  `onLost`, which the engine ignores. Since the listener sets the mark before reading `onLost` and `play` sets
  `onLost` before reading the mark, either the new tone's `onLost` is called (and the engine stops it) or `play` sees
  the mark and asks again, which a call refuses.
- Paths that end with no tone sounding still give focus back: a track that fails to build calls `abandonFocus()` as
  today (the previous tone has already faded), and a loss reaches `stop()` through the engine (`stopToneNow` →
  `output.stop()`).
- Update the class KDoc's focus sentence: the transient focus is held for as long as tones follow one another and given
  back when they stop or it is lost.

`tuner/implementation/CLAUDE.md`, Android bullet: after "under transient focus (loss ends it)" add "held while one
tone replaces another (a scale), since giving it back between notes resumes and pauses another app's music".

## Tests

None can be written: the class is all Android framework (`AudioManager`, `AudioTrack`), and the module has no Android
unit tests. Compile with `./gradlew :app:android:assembleDebug` (what CI compiles every `androidMain` with).
