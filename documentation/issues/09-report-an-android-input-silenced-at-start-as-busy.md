# Report an Android input that is silenced from the moment it starts as busy, not as silent

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android 10+ (API 29+)
**Files:** `tuner/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/tuner/implementation/AndroidAudioInput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

Since Android 10 an app that starts recording while a call or a privileged capture (the assistant's hotword, another
app's `VOICE_COMMUNICATION`) holds the microphone still gets `RECORDSTATE_RECORDING`, but is *silenced*: it is handed
zeros. `AndroidAudioInput` turns that into `MICROPHONE_BUSY` through an `AudioRecordingCallback`, registered only after
the record has started:

```kotlin
try {
    record.startRecording()
} catch (_: IllegalStateException) { ... }
if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) { ... return AudioInputStart.Refused(TunerStopReason.MICROPHONE_BUSY) }
ring.clear()
this.record = record
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) registerSilencing(record, listener)
Thread({ capture(record, listener) }, "Tuner").start()
```

```kotlin
override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
    val ours = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId } ?: return
    if (ours.isClientSilenced && this@AndroidAudioInput.record === record) listener.onLost(TunerStopReason.MICROPHONE_BUSY)
}
```

The callback only reports *changes*; registering it does not deliver the current configuration. When the record is
silenced from its first frame, the change that silenced it is the start itself, which can be dispatched before the
callback is registered. The tuner then shows the `SILENT` notice after two seconds of zeros ("Nothing at all is heard. Check that the
microphone is not muted and that Campfire may use it." — the wrong cause on Android) instead of the busy one ("The
microphone is in use by a call or another app.").

API levels (the executor confirms them against the SDK reference while editing): `AudioManager.registerAudioRecordingCallback` and
`AudioRecordingConfiguration.getClientAudioSessionId` are API 24; `AudioRecordingConfiguration.isClientSilenced` and
`AudioRecord.getActiveRecordingConfiguration` are API 29, which is why the code gates on `VERSION_CODES.Q`.

## Fix

After registering the callback, ask the record for its own configuration once, and refuse the start as busy if it is
silenced already — before the capture thread starts, so nothing else holds the record:

```kotlin
ring.clear()
this.record = record
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    registerSilencing(record, listener)
    // The callback hears only changes, and the change that silenced a record started under a call is its own start.
    if (record.activeRecordingConfiguration?.isClientSilenced == true) {
        stop()
        record.release()
        return AudioInputStart.Refused(TunerStopReason.MICROPHONE_BUSY)
    }
}
Thread({ capture(record, listener) }, "Tuner").start()
```

`stop()` clears `this.record` (so a late callback is ignored), unregisters the callback and stops the record; the
release is done here because no capture thread exists yet to do it (see the comment in `capture`'s `finally`). Mark the
check `@SuppressLint("NewApi")` the way `registerSilencing` is, or move it into a small `@RequiresApi(29)` helper next
to `registerSilencing`. Registering before the check (rather than checking first) leaves no gap in which a change
could be missed.

## Tests

None: `AudioRecord` is platform audio (root `CLAUDE.md`). `./gradlew :app:android:assembleDebug` must compile it.

## Manual check

Android 10+ phone: start a phone call (or a WhatsApp/Meet voice call), then open Campfire's tuner with the microphone
permission granted. The tuner shows "The microphone is in use by a call or another app." at once, not
the silent one after two seconds. Without a call the tuner listens normally.

## Docs

`tuner/implementation/CLAUDE.md`, the **Android** bullet: "a client silenced by a call (`AudioRecordingCallback`,
Android 10+) is busy" → add "whether it is silenced from the start or later".
