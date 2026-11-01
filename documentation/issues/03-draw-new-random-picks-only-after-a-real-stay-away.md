# Draw new random picks on returning to the app only after a real stay away, timed on the wall clock

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** mainly Android (iOS and the web only after a real backgrounding)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/RandomPicksDraw.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (its construction),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/RandomPicksDrawTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`

**Rewritten** after 3ffe2c950 replaced the Shuffle sort this plan was written for with the random picks: the bug moved
with the hourly rule into `RandomPicksDraw`, and the challenge's finding (the monotonic clocks stop while the device
sleeps) now applies to the picks' own hour too. Decision D2 (5 minutes, on the wall clock) stands.

## Problem

`CampfireApp.kt` asks for new picks on every `ON_START` while Songs is on top:

```kotlin
// Coming back to the app on the song list opens it again, for the random picks too (RandomPicksDraw).
LifecycleEventEffect(Lifecycle.Event.ON_START) {
    viewModel.metronomeController.onAppStarted()
    if (viewModel.navigator.backStack.lastOrNull() == CampfireDestination.Songs) viewModel.randomPicksDraw.catchUp()
}
```

`RandomPicksDraw.catchUp` draws a new seed once `REDRAW_INTERVAL` (an hour) has gone by since the last draw, measured
with `timeSource.markNow()` (`TimeSource.Monotonic` in the app). Two things are wrong:

1. **`ON_START` is not only "the app came back to the front".** On Android it is also the return from the system file
   picker or share sheet opened from Songs (Import, a selection's export), seconds later, and an activity recreation
   (dark mode or the language switched in the system, a fold). Once the hour has passed, the five cards at the top of the
   list are replaced under someone who never left it. The songs `CLAUDE.md` promises new picks "only as the screen is
   opened ... never under a reader".
2. **The hour is measured on a clock that stops while the device sleeps.** Android's `System.nanoTime` is
   `CLOCK_MONOTONIC`, and iOS's and macOS's `mach_absolute_time` likewise stop in sleep, so a phone put down with its
   screen off for three hours can count minutes, and the picks the user comes back to are the old ones.

Neither touches the list's position or a running selection: the picks change no `orderKey` or `filterKey`, and
selection mode drops them (`withSelectionLeading`).

## Fix

1. `RandomPicksDraw` takes a `kotlin.time.Clock` in place of the `TimeSource` (`Clock.System` from
   `CampfireViewModel`, a fake whose `now()` the test sets) and keeps `drawnAt: Instant`. A clock set back reads as
   negative time, so no redraw until it catches up; one set forward at worst draws once early.
2. Count a return only after a real stay away: `fun onAppStopped()` records `stoppedAt = clock.now()`;
   `fun onAppStarted(isSongsOnTop: Boolean)` calls `catchUp()` only when `isSongsOnTop` and a stop was recorded at
   least `RETURN_AWAY` (README decision D2: **5 minutes**) ago, then forgets the stop. The first start (no stop) is no
   return: the launch drew the picks already. `catchUp()` stays for the back stack listener in `CampfireViewModel`
   (another tab left for Songs), which is right as it is.
3. `CampfireApp.kt`: call `viewModel.randomPicksDraw.onAppStopped()` from the existing `ON_STOP` effect, and replace
   the `ON_START` line with `viewModel.randomPicksDraw.onAppStarted(isSongsOnTop = …)`. Reword its comment: coming back
   to the app on the song list after a stay away opens it again.
4. `screens/songs/CLAUDE.md`, Random picks: "the app returning to the front on it" becomes "the app returning to the
   front on it after at least five minutes away", and the hour is "on the wall clock".

Why 5 minutes (D2): a trip to the system file picker or a share sheet commonly takes 30 s to a few minutes, and a
recreation takes milliseconds. Someone away for five minutes has put the phone down and is opening the list again.

## Tests

`RandomPicksDrawTest`, moved from `TestTimeSource` to a fake `Clock`, keeping its two tests, plus:
- no stop recorded → a start draws nothing;
- stopped, 10 s, started (an hour since the draw) → nothing; stopped, 5 min, started → new picks;
- a return is consumed: a second start without a stop draws nothing;
- started with Songs not on top → nothing;
- stopped, the clock set back an hour, started → nothing.
