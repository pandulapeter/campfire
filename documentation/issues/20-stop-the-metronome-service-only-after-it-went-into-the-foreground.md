# Stop the metronome service from its playback collector only after it went into the foreground

**Challenged:** amended — gating the existing collector on `isInForeground` can leave the service in the foreground forever (StateFlow skips a value equal to the last one it emitted, so a Stopped it saw and ignored before `onStartCommand` hides a later Stopped); the collector now starts in `onStartCommand`, after `startForeground`.

**Kind:** bug (crash)  ·  **Severity:** low  ·  **Platforms:** Android
**Files:** app/android/src/main/java/com/pandulapeter/campfire/metronome/CampfireMetronomeService.kt, app/android/CLAUDE.md

## Problem

`CampfireMetronomeService` is started with `ContextCompat.startForegroundService` by `CampfireMainActivity.onMetronomeNotificationChanged`
the moment a click starts. The system delivers `onCreate` and `onStartCommand` as two separate messages on the main
thread, and `startForeground` is only called in `onStartCommand`. But `onCreate` already starts following the engine:

```kotlin
scope.launch {
    // The state of the moment is skipped: the intent that started this service was sent because a click started,
    // and a stop that came before it arrived is answered in onStartCommand.
    metronome.playback.drop(1).collect { if (it !is MetronomePlayback.Playing) stop() }
}
```

and `stop()` calls `stopSelf()` whatever the foreground state is:

```kotlin
private fun stop() {
    if (isInForeground) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    isInForeground = false
    isRunning = false
    stopSelf()
}
```

When the click stops between those two messages (Play and Stop tapped in quick succession, or the audio focus lost
right away), the collector (on `Dispatchers.Main.immediate`, so it runs synchronously when the stop is made on the main
thread, or as a message queued before `onStartCommand`'s) calls `stopSelf()` before `startForeground`. A service started
with `startForegroundService` that is brought down before `startForeground` makes the system post
"Context.startForegroundService() did not then call Service.startForeground()" and crash the process. The comment
already says this case belongs to `onStartCommand`, and `onStartCommand` does handle it: it goes into the foreground
first and then `if (metronome.playback.value !is MetronomePlayback.Playing) stop()`. The sync service follows the rule
explicitly ("It only ever stops itself after `startForeground` has been called", `app/android/CLAUDE.md`); the
metronome service does not.

## Fix

Do not gate the existing collector on `isInForeground`: that leaves a hole. `StateFlow.collect` never hands a collector
a value equal to the last one it handed it, so this ordering keeps the service in the foreground with no click:

1. the click stops after `onCreate` — the collector sees `Stopped()` and, gated, ignores it;
2. the click starts again (the engine sets `Playing`, the collector's resumption is posted to the main thread behind
   the already queued `onStartCommand`);
3. `onStartCommand` reads `Playing`, goes into the foreground and stays;
4. the click stops again before the posted resumption runs — the collector resumes, reads `Stopped()`, which equals
   the `Stopped()` it last saw, and is never called. Nothing stops the service, and if the composition never saw the
   brief second `Playing` (both changes inside one frame) no second intent arrives to re-check either.

Instead, follow the engine only from the moment the service is in the foreground, so that every value the collector
sees is one it may act on. Remove the collector from `onCreate` and, in `onStartCommand`, replace the line
`if (metronome.playback.value !is MetronomePlayback.Playing) stop()` (after `startForeground`, `isInForeground = true`,
`isRunning = true`) with:

```kotlin
// Followed only from here, once the service is in the foreground: stopping before startForeground is a crash, and a
// collector that saw (and had to ignore) a stop earlier would not be told about the next one, a StateFlow never
// repeating the value a collector last got. The first value is the state of the moment, so a click that stopped
// between the intent being sent and its arrival here is stopped at once.
if (playbackJob == null) {
    playbackJob = scope.launch { metronome.playback.collect { if (it !is MetronomePlayback.Playing) stop() } }
}
```

with `private var playbackJob: Job? = null` next to `isInForeground` (and the `drop` import removed, `Job` imported).
`scope` is `Dispatchers.Main.immediate` and `onStartCommand` runs on the main thread, so the launch runs synchronously
up to the collector's first value: the "stopped before it arrived" case is answered inside this `onStartCommand`, as
today. Later `onStartCommand`s (the activity's plain `startService` with new words) find the job and launch nothing.
Any later transition the StateFlow conflates away ends on a value equal to one already acted on in the foreground, so
nothing is missed. Update the class KDoc's "follows the engine's own state" sentence if needed (it still does, from
`onStartCommand`).

`onTaskRemoved` can also call `stop()`, but a task swipe in the few milliseconds between the two messages is not worth
a guard; leave it. The `ACTION_STOP` path returns before the collector is launched and arrives through a plain
`startService` from the notification's `PendingIntent.getService`, which carries no foreground promise, so it is safe too.

Add one sentence to the `metronome/CampfireMetronomeService` paragraph of `app/android/CLAUDE.md`: like the sync service,
it only ever stops itself after `startForeground` — it starts following the engine in `onStartCommand`, once in the
foreground, so a click that ended before then is stopped there.

## Tests

None: the service is Android framework code, outside the pure logic that is unit tested.

## Manual check

On an Android 12+ device with a debug build, open a song, tap the app bar's metronome Play and immediately Stop as fast
as possible, a few dozen times (or start the click while a phone call holds the audio focus). Before the fix this can
crash with `ForegroundServiceDidNotStartInTimeException` / "did not then call Service.startForeground()" in logcat;
after it, the notification appears and goes, and no crash. Also check the ordinary path: Play, leave the app, the
notification's Stop ends the click and the notification.
