# Re-arm the one-time reopen on a disconnected microphone once the reopened input has run for two seconds

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (in practice Android, iOS and the web, where a route change
reports `MICROPHONE_DISCONNECTED`)
**Challenged:** amended — plan 05 lands first in the lane, so the `if (input.latest(window))` snippet no longer exists; the fix is now given in its post-05 form (the re-arm keyed to fresh positions, ahead of plan 02's speaker branch so a tone does not hold it back), plus a test that a frozen reopened input is not re-armed.
**Files:** `tuner/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngine.kt`,
`tuner/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/tuner/implementation/TunerEngineTest.kt`,
`tuner/implementation/CLAUDE.md`

Lands after plan 01 (`TunerEngineTest`, `FakeAudioInput`), and after plan 05 if both are taken (see Fix, last point).

## Problem

A new route (headphones plugged in or pulled) arrives as `MICROPHONE_DISCONNECTED`, and the engine opens the input
again — but only once per `listen` from a stopped state:

```kotlin
override fun listen(config: TunerConfig) = onEngine {
    ...
    if (current is TunerListening.Hearing || current is TunerListening.Starting) return@onEngine
    canReopen = true
    open()
}
```

```kotlin
if (reason == TunerStopReason.MICROPHONE_DISCONNECTED && canReopen) {
    canReopen = false
    open()
} else {
    setListening(TunerListening.Stopped(reason))
}
```

Nothing sets `canReopen` back to true while listening continues, so a player who plugs headphones in, tunes for ten
minutes and pulls them out has the tuner stop with "microphone disconnected", although the reopen would have worked as
it did the first time. The intent ("an input that keeps going away is reported") is about disconnects close together.
Proved at b5c8ed3b5 with a probe: a disconnect, a reopen, then a second disconnect a minute of virtual time later →
`Stopped(MICROPHONE_DISCONNECTED)` with no second start.

## Fix

In `TunerEngine.hear`, re-arm the reopen once the session has been delivering windows for a while:

```kotlin
var firstWindowAt: TimeMark? = null
...
if (input.latest(window)) {
    val heardSince = firstWindowAt ?: timeSource.markNow().also { firstWindowAt = it }
    // An input that came back from a new route and has kept working may be opened again the next time one changes.
    if (!canReopen && heardSince.elapsedNow() >= REOPEN_REARM_DELAY) canReopen = true
    ...
}
```

with `val REOPEN_REARM_DELAY = 2.seconds` in the companion (import `kotlin.time.Duration.Companion.seconds`,
`kotlin.time.TimeMark`). `firstWindowAt` is local to the `hear` call, so it starts again with every session, the
reopened one included. Two disconnects less than two seconds of working input apart still stop with the reason.

Plan 05 (stale input) lands before this one in the lane, so by then there is no `if (input.latest(window))` to hang
this on: count only fresh windows, in the block where plan 05 notes that the position moved, so that a frozen input
does not re-arm itself. It sits before plan 02's speaker / let-go branch, so a reopened input that keeps delivering
while a tone sounds re-arms all the same:

```kotlin
if (position != AudioInput.NO_WINDOW && position != lastPosition) {
    lastPosition = position
    movedAt = timeSource.markNow()
    val heardSince = firstWindowAt ?: movedAt.also { firstWindowAt = it }
    // An input that came back from a new route and has kept working may be opened again the next time one changes.
    if (!canReopen && heardSince.elapsedNow() >= REOPEN_REARM_DELAY) canReopen = true
}
```

(The re-arm is only looked at when the position moves, so two seconds of a stalled input do not count.)

## Tests

In `TunerEngineTest`:

1. `` `a disconnect long after a reopen is opened again` `` — listening (`sine(330f)` signal), `onLost(MICROPHONE_DISCONNECTED)`,
   `runCurrent()`, `advanceTimeBy(3_000)`, a second `onLost(MICROPHONE_DISCONNECTED)` on the *new* listener
   (`input.listener` after the reopen) → `Hearing`, `startCount == 3`. Fails at b5c8ed3b5.
2. `` `a reopened input that stopped delivering is not opened again` `` — as case 1, but `input.isFrozen = true` (plan
   05's fake) right after the reopen's first poll; `advanceTimeBy(3_000)`, a second disconnect →
   `Stopped(MICROPHONE_DISCONNECTED)`, `startCount == 2`.
3. `` `two disconnects close together stop` `` — the same with `advanceTimeBy(1_000)` between them →
   `Stopped(MICROPHONE_DISCONNECTED)`, `startCount == 2`. (Plan 01's case 5 says the same; keep one of the two.)

## Manual check

iPhone or Android phone with the tuner listening: plug wired headphones in (the tuner keeps listening), wait ten
seconds, pull them out. The tuner keeps listening rather than stopping with the disconnected notice. Doing it twice
within a second still stops it.

## Docs

`tuner/implementation/CLAUDE.md`, the `TunerEngine` bullet: "opens the input again once before it is reported" →
"opens the input again, once until the reopened input has worked for two seconds, before it is reported".
