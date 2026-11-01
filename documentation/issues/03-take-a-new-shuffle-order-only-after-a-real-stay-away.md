# Take a new Shuffle order on returning to the app only after a real stay away, and never mid selection

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** mainly Android (iOS and the web only after a real backgrounding)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/LibraryState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ShuffleReturn.kt` (new, if the
decision is pulled out as below),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/ShuffleReturnTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/CLAUDE.md`

**Challenged:** amended — the time away is measured on the wall clock (`kotlin.time.Clock`, which `LibraryState.currentHour`
already reads), not `TimeSource.Monotonic`: the monotonic clocks stop while the device sleeps (Android's `System.nanoTime`
is `CLOCK_MONOTONIC`; iOS's and macOS's `mach_absolute_time` likewise), so a phone put down with its screen off for an
hour could count only seconds away and never reshuffle, the very case D2 is for. Lifecycle checked and unchanged:
a recreation (ON_STOP then ON_START within milliseconds, the view model kept) is no return, process death rebuilds
`LibraryState` on the current hour, and the first ON_START has no stop recorded.

Runs after plan 02 in lane S.

## Problem

`CampfireApp.kt` takes the Shuffle order on to the current hour on every `ON_START` while Songs is on top:

```kotlin
// Coming back to the app on the song list opens it again, for the Shuffle order too (LibraryState.shuffleHour).
LifecycleEventEffect(Lifecycle.Event.ON_START) {
    viewModel.metronomeController.onAppStarted()
    if (viewModel.navigator.backStack.lastOrNull() == CampfireDestination.Songs) viewModel.libraryState.catchUpWithTheHour()
}
```

`LibraryState.kt`: `fun catchUpWithTheHour() = _shuffleHour.update { currentHour() }`. Under Shuffle a new hour
changes `ScreenData.shuffleHour` and so both `SongGroups.orderKey` (`"${sortingMode.name}@${shuffleHour}"`, which sends
the list to the top through `ScrollToTopWhenChanged`) and `filterKey` (which reseeds a running selection's order in
`rememberSongGroupsWithSelection`). Other sorts are untouched (`GetScreenDataUseCaseImpl` passes 0 for them).

`ON_START` is not only "the app came back to the front". Once the clock has passed the hour since the last catch-up,
the list is reshuffled and sent to the top:

- on Android, returning from the system file picker or share sheet launched from Songs (Import, a selection's
  export), seconds later;
- on Android, an activity recreation (dark mode or language switched in the system, a fold), which stops and starts the
  activity without the user leaving;
- in the middle of selection mode, where the list's order is reseeded and the ticked songs regrouped under the user.

The songs `CLAUDE.md` promises the hour moves on only "as the screen is opened (the app returning to the front on it,
or another tab left for it)", "never under a reader". The other caller, `CampfireViewModel`'s back stack listener
(another top-level tab left for Songs), is right and stays.

## Fix

1. Count a return only after a real stay away. Record the wall-clock time when the app stops and, on start, catch up only
   when it has been away at least `SHUFFLE_RETURN_AWAY` (README decision D2, recommended **5 minutes**), Songs is on
   top, and no song selection is running (`viewModel.songSelection.selectedSongFileNames == null`).
2. Pull the decision into a small testable class in `ui/state` (`ShuffleReturn`, internal), owned by `LibraryState`,
   with an injected `kotlin.time.Clock` (`Clock.System` in the app, a fake whose `now()` the test sets):

   ```kotlin
   internal class ShuffleReturn(private val clock: Clock) {
       private var stoppedAt: Instant? = null
       fun onStopped() { stoppedAt = clock.now() }
       /** Whether a start ends a stay away long enough to open the songs screen afresh. */
       fun isReturn(): Boolean = (stoppedAt?.let { clock.now() - it >= SHUFFLE_RETURN_AWAY } == true).also { stoppedAt = null }
   }
   ```

   Wall-clock time, not `TimeSource.Monotonic`: a monotonic mark does not advance while the device sleeps on Android,
   iOS, and macOS, and a phone put down is asleep. A clock set back reads as a negative time away, so no return; a
   clock set forward at worst reshuffles once, as crossing the hour would anyway.

   `LibraryState` gets `fun onAppStopped()` and `fun onAppStarted(isSongsOnTop: Boolean, isSelecting: Boolean)`, the
   latter calling `catchUpWithTheHour()` only when `shuffleReturn.isReturn() && isSongsOnTop && !isSelecting`.
   `LibraryState` (built by `CampfireViewModel`, not by Koin) holds `ShuffleReturn(Clock.System)` as a private
   property. (Its `lastRescanAt` rightly stays monotonic: it only spaces rescans apart while the app runs.) The first start (no stop recorded) is not a return: the
   hour is the launch hour already.
3. `CampfireApp.kt`: call `viewModel.libraryState.onAppStopped()` from the existing `ON_STOP` effect and replace the
   `ON_START` line with `viewModel.libraryState.onAppStarted(isSongsOnTop = …, isSelecting = viewModel.songSelection.selectedSongFileNames != null)`.
   Reword its comment: coming back to the app on the song list after a stay away opens it again.
4. `screens/songs/CLAUDE.md`, Shuffle section: "the app returning to the front on it" becomes "the app returning to the
   front on it after at least five minutes away (`ShuffleReturn`), never mid selection" — the number as D2 decides.

Why 5 minutes (D2): a trip to the system file picker or a share sheet commonly takes 30 s to a few minutes, and a
recreation takes milliseconds; 30 s would still reshuffle under someone browsing for a file. Someone away for five
minutes has put the phone down and is opening the list again, which is what the order changing on the hour is for.
The alternative is 30 s (the reviewer's example), which only filters out recreations and quick glances.

## Tests

`ShuffleReturnTest` (`presentation/src/commonTest/.../ui/state/`), with a fake `Clock`:
- no stop recorded → not a return;
- stopped, 10 s, started → not a return; stopped, 5 min, started → a return;
- a return is consumed: a second start without a stop is not one;
- stopped, the clock set back an hour, started → not a return.

And a `LibraryState`-level check if it can be built cheaply in `commonTest` (look for an existing helper in
`presentation/src/commonTest/.../ui/state/songs`); otherwise the `isSongsOnTop` / `isSelecting` gate is a plain
boolean expression in `onAppStarted`, covered by reading it.
