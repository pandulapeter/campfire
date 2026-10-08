# Declare the outputs' default sample rate and chunk/queue frame counts once, on AudioOutput's companion

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `metronome/implementation/src/commonMain/.../AudioOutput.kt` (`AudioOutput.Companion`: `CHUNK_SECONDS`, `QUEUED_SECONDS`); `SilentAudioOutput.kt` (`SAMPLE_RATE`, `aheadFrames`, the `delay`); `androidMain/.../AudioOutput.android.kt` (`DEFAULT_SAMPLE_RATE`, `BYTES_PER_FRAME`, buffer size, `feed`'s `frames`); `desktopMain/.../AudioOutput.desktop.kt` (`SAMPLE_RATE`, `open` buffer size, `feed`'s `frames` / `bytes`); `iosMain/.../AudioOutput.ios.kt` (`DEFAULT_SAMPLE_RATE`, `frames` in `start`); `wasmJsMain/.../AudioOutput.wasmJs.kt` (`aheadFrames`); `metronome/implementation/CLAUDE.md` (no change expected; check the outputs paragraph)
**Depends on:** none (if 46 is in the same lane, land 46 first — both edit `SilentAudioOutput` and the platform outputs)

## Problem

`48_000` is declared four times as a private constant — `SilentAudioOutput.SAMPLE_RATE`,
`AndroidAudioOutput.DEFAULT_SAMPLE_RATE`, `DesktopAudioOutput.SAMPLE_RATE`, `IosAudioOutput.DEFAULT_SAMPLE_RATE` — and the
frame counts derived from `AudioOutput.CHUNK_SECONDS` / `QUEUED_SECONDS` are recomputed at every use:

```kotlin
val frames = (AudioOutput.CHUNK_SECONDS * sampleRate).toInt()                 // Android, iOS, desktop
(AudioOutput.QUEUED_SECONDS * sampleRate).toInt() * BYTES_PER_FRAME           // Android
open(format, (AudioOutput.QUEUED_SECONDS * SAMPLE_RATE).toInt() * format.frameSize)   // desktop
val aheadFrames = (AudioOutput.QUEUED_SECONDS * SAMPLE_RATE).toLong()         // silent, web
```

The CLAUDE.md promises "~20 ms chunks, ~100 ms queued" for every output; a change to either has to be made the same way
in five files, and a new output copies the pattern again.

(The finding also proposed a shared `runPcmFeed` for the Android and desktop feed loops. They are not identical: Android
writes `ShortArray`s and fails on a negative return, sets `THREAD_PRIORITY_URGENT_AUDIO` and releases the track in a
`finally` on its own thread; desktop converts to little-endian bytes and fails on a short write. A shared loop would need
three lambdas to save about ten lines, so it is left out.)

## Fix

1. In `AudioOutput.Companion` add
   ```kotlin
   const val DEFAULT_SAMPLE_RATE = 48_000
   const val BYTES_PER_FRAME = 2   // 16-bit mono
   fun chunkFrames(sampleRate: Int) = (CHUNK_SECONDS * sampleRate).toInt()
   fun queuedFrames(sampleRate: Int) = (QUEUED_SECONDS * sampleRate).toInt()
   ```
   Keep the expressions exactly as written today so the frame counts do not change (at 48 kHz: 960 and 4 800; at
   44.1 kHz: 882 and 4 410).
2. Replace the four private `48_000` constants with `AudioOutput.DEFAULT_SAMPLE_RATE` (the desktop and silent outputs
   *use* it as their rate; Android and iOS use it as the fallback when the device reports none — keep a comment saying so
   at those two sites), the frame expressions with the helpers (`.toLong()` at the silent and web call sites, which need
   a `Long`), and Android's private `BYTES_PER_FRAME` with the shared one. The desktop keeps `format.frameSize` (it is
   the JDK's own answer for the format it opened) — or uses `BYTES_PER_FRAME`; either is behaviour-preserving, prefer
   keeping `format.frameSize`.
3. One commit; compile every target.

## Tests

No new logic to test beyond the two helpers; add two assertions to an existing commonTest (e.g. a small
`AudioOutputTest`: `chunkFrames(48_000) == 960`, `queuedFrames(44_100) == 4_410`). Compile all four source sets:
`./gradlew :metronome:implementation:desktopTest :metronome:implementation:compileDebugKotlinAndroid :metronome:implementation:compileKotlinIosSimulatorArm64 :metronome:implementation:compileKotlinWasmJs`.

## Manual check

none — covered by tests and compilation (every value is unchanged).
