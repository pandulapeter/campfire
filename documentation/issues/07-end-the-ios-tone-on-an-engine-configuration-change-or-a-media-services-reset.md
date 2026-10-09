# End the iOS tone on an engine configuration change or a media services reset

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** iOS
**Files:** `tuner/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/tuner/implementation/IosToneOutput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

`IosToneOutput` plays the tone on an `AVAudioEngine` of its own and observes only interruptions:

```kotlin
// A call stops the engine on its own; the tone is then over rather than shown as sounding.
observer = NSNotificationCenter.defaultCenter.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
    val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
    if (type == AVAudioSessionInterruptionTypeBegan) onLost()
}
```

A new route (headphones plugged in or pulled, a Bluetooth speaker connecting) stops an `AVAudioEngine` on its own and
posts `AVAudioEngineConfigurationChangeNotification` for it; a media services reset tears every engine down and posts
`AVAudioSessionMediaServicesWereResetNotification`. Neither is observed here, so the tone goes quiet while
`TunerState.tone` stays set: the string chip stays selected, the page keeps naming the tone, and — since the engine
hides every reading while `tone != null` — the meter stays at rest however the string is played, until the player
taps the chip again.

Both siblings already observe both: `IosAudioInput.observe` in this module, and the metronome's iOS output
(`metronome/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/metronome/implementation/AudioOutput.ios.kt`):

```kotlin
// A new route (headphones plugged in) stops the engine on its own, and the services being reset takes it
// away altogether; either way what was scheduled is gone, so the click ends and says why rather than going
// quiet while it still shows as playing.
center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) {
    listener.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
},
center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) {
    listener.onLost(MetronomeStopReason.OUTPUT_FAILED)
},
```

## Fix

In `IosToneOutput`, replace `private var observer: NSObjectProtocol?` with
`private var observers = emptyList<NSObjectProtocol>()` and register three observers after `player.play()`:

```kotlin
observers = observe(engine, onLost)
```

```kotlin
/**
 * A call, a new route and the media services being reset each stop the engine on their own; the tone is then over
 * rather than shown as sounding, which would also keep every reading hidden.
 */
private fun observe(engine: AVAudioEngine, onLost: () -> Unit): List<NSObjectProtocol> {
    val center = NSNotificationCenter.defaultCenter
    return listOf(
        center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (type == AVAudioSessionInterruptionTypeBegan) onLost()
        },
        center.addObserverForName(AVAudioEngineConfigurationChangeNotification, engine, null) { onLost() },
        center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) { onLost() },
    )
}
```

(the configuration change observed with `object = engine`, so the input's engine changing does not end the tone by
itself; imports `platform.AVFAudio.AVAudioEngineConfigurationChangeNotification` and
`platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification`). In `stop()`:

```kotlin
observers.forEach(NSNotificationCenter.defaultCenter::removeObserver)
observers = emptyList()
```

`onLost` is the engine's `{ onEngine { if (toneId == toneSession) stopToneNow() } }`, which calls `stop()` on the
confined coroutine, so stopping an engine the system already stopped is harmless (`player.stop()` and `engine.stop()`
on a stopped engine do nothing).

## Tests

None: platform audio code, not pure logic (root `CLAUDE.md`). `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`
must compile it.

## Manual check

iPhone with wired or Bluetooth headphones, the tuner open with a preset:

1. Tap a string chip so the tone plays, then plug in or pull out the headphones: the tone stops *and* the chip is no
   longer selected; plucking the string is read again straight away.
2. With the tone playing and the tuner also listening, make sure the listening input's own reopen on the same route
   change does not leave the tone selected and silent (both engines stop; both are reported).
3. Regression: tap a chip while the tuner is listening and let it play for ten seconds without touching the route —
   the tone must not end on its own (that would mean the input's engine starting posts a configuration change for the
   tone's engine; if it does, observe the configuration change only after the first render, or compare the route).
4. On a device with Developer Mode on, with a tone playing: Settings → Developer → Reset Media Services. Back in
   Campfire the chip is no longer selected.

## Docs

`tuner/implementation/CLAUDE.md`, the **iOS** bullet: "The tone is an `AVAudioPlayerNode` on an engine of its own: …"
add "ended by an interruption, a configuration change of its engine or a media services reset".
