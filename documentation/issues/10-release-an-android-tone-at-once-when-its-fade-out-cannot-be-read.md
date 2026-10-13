# Release an Android tuner tone at once when its fade-out shaper is missing or unreadable, instead of letting it sound at full volume for a second

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android
**Files:**
`tuner/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/tuner/implementation/AndroidToneOutput.kt`,
`tuner/implementation/CLAUDE.md`

## Problem

`AndroidToneOutput.stop()` starts a fade-out `VolumeShaper` and polls it until the mixer has applied silence:

```kotlin
override fun stop() {
    val track = track ?: return
    this.track = null
    abandonFocus()
    val fadeOut = try {
        track.createVolumeShaper(fade(floatArrayOf(1f, 0f))).also { it.apply(VolumeShaper.Operation.PLAY) }
    } catch (_: IllegalStateException) {
        null
    }
    releaseOnceSilent(track, fadeOut, SystemClock.uptimeMillis() + RELEASE_TIMEOUT_MILLIS)
}

private fun releaseOnceSilent(track: AudioTrack, fadeOut: VolumeShaper?, deadlineMillis: Long) {
    handler.postDelayed({
        val volume = try {
            fadeOut?.volume
        } catch (_: IllegalStateException) {
            null
        }
        if (volume != 0f && SystemClock.uptimeMillis() < deadlineMillis) {
            releaseOnceSilent(track, fadeOut, deadlineMillis)
        } else {
            ...release...
        }
    }, FADE_MILLIS)
}
```

When `createVolumeShaper` threw (so `fadeOut` is `null`) or reading `.volume` throws, `volume` is `null`, and
`null != 0f` is true: the loop keeps polling until `RELEASE_TIMEOUT_MILLIS` (1 s). Nothing is fading the tone in that
case, so the reference note goes on at full volume for a second after the user stopped it (or after a focus loss, such as
an incoming notification sound), and is then cut at full amplitude, which pops — the very thing the poll exists to avoid.
It needs the shaper to fail, which is rare (a track the system has already invalidated), hence low.

## Fix

Keep polling only while there is a fade whose volume can be read and is still above silence:

```kotlin
if (volume != null && volume > 0f && SystemClock.uptimeMillis() < deadlineMillis) {
    releaseOnceSilent(track, fadeOut, deadlineMillis)
} else {
    ...unchanged release...
}
```

With no readable fade the track is released on the first check, a fade's length (10 ms) after `stop()`: a pop, but at
once rather than after a second of tone. Optionally, where `fadeOut` is `null` at `stop()`, call `track.setVolume(0f)`
before scheduling the release, so the mixer's own volume ramp has a chance to soften the cut; only keep that if a device
shows it helps, since the ramp's length is not documented. Update the KDoc of `releaseOnceSilent` ("A track the mixer
never reaches … is released after a timeout") to add "and one whose fade cannot be read, at once".

In `tuner/implementation/CLAUDE.md`, the Android bullet's "it is stopped only once the fade-out shaper reads silence (a
second at most)" gains "or at once where there is no shaper to read".

## Tests

None. The class is `androidMain` and the module's tests run on the desktop target. Extracting the condition into a
`commonMain` helper would test a single comparison, which is not worth a file; the fix is the condition itself.

## Manual check

On an Android device, open the tuner, play a reference tone, and stop it: it still fades out without a pop, as before.
To see the fixed path, a debug build with `createVolumeShaper` in `stop()` temporarily replaced by `null` must stop the
tone within a few milliseconds rather than a second later (revert the change afterwards).
