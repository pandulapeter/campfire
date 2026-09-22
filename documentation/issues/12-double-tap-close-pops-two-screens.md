# 12 · Double-tapping the editor's Close closes the song too; buttons on a screen that is sliding in or out still act

**Severity:** wrong behaviour (all platforms. Very likely for anyone who double-taps or taps quickly: the second tap of a double-tap on the editor's Close lands on the song screen's Back arrow as the editor slides away) · **Area:** `:presentation` (`CampfireApp.kt`: the `ScreenSurface` the song details and editor entries are drawn on)

## Symptom
1. Open a song from the song list, then open its editor from the song's menu (Edit). The stack is now
   Songs, Song, Editor.
2. Double-tap the editor's Close button (top start corner), or double-click it on desktop. No text needs to be typed.
3. The editor slides down, and the song screen it uncovers closes as well. The user lands on the song list instead
   of the song they were reading.

Related things that happen through the same gap:
- Tap a song's Back arrow and tap again at the same spot straight away while the card slides off. On a stack that was
  opened from the setlists tab, the tap lands on whatever the setlists screen has there.
- While a song slides in, the card that is still arriving takes taps on its app bar (the menu, "display options")
  before it has landed. If the push is reversed (see the navigation generation workaround), the menu opens on a
  screen that is on its way out.

## Cause
Nothing stops a screen from taking touches while it is moving. The song details and the editor are drawn on
`ScreenSurface` (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:626-631`):

```kotlin
private fun ScreenSurface(content: @Composable () -> Unit) = Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
    content = content,
)
```

Both app bars go back through the view model with nothing tied to the screen that asked
(`SongEditorScreen.kt:304`, `SongDetailsScreen.kt:216`: `IconButton(onClick = onBack)`, and `onBack` is
`viewModel::navigateBack`, `CampfireApp.kt:444` and `:457`), and `navigateBack` pops whatever is on top
(`CampfireViewModel.kt:802-808` → `popBackStack`, `:831-835`).

The timing makes the double-tap case land almost every time. The editor leaves on `motionScheme.defaultSpatialSpec()`
(`CampfireApp.kt:723-733`), the expressive spatial spring (damping 0.8, stiffness 380). That spring has covered about
28 % of the distance after 50 ms and 66 % after 100 ms. The second tap of a double-tap comes 100–200 ms after the
first. By then the editor's top start corner is well below the finger, and what is under the finger is the song
screen's own Back arrow, in exactly the same place. That arrow is live: the details screen is the target of the
transition and nothing disables it.

The view model has no reliable way to know that a transition is running.
`ReportNavigationTransition` (`CampfireApp.kt:792-796`) reports the *entry's* own enter/exit transition. A card
popped off the stack is disposed while its report still says "running". The card it uncovers enters with
`EnterTransition.None`, so its own transition starts and ends within one frame and it never recomposes to report
"not running". After every pop, `isNavigationTransitionRunning` (`CampfireViewModel.kt:210`) is therefore stuck at
true until the next report. That only costs an extra `navigationGeneration` bump, but it rules the flag out as a
basis for anything that ignores input.

Navigation 3 does keep a reliable signal. `NavDisplay` caps the lifecycle of every entry of every scene at `STARTED`
for as long as the scene transition is not settled, and only lets it reach `RESUMED` once
`transition.currentState == transition.targetState` (`navigation3-ui` 1.1.1, `NavDisplay.kt`, the
`rememberLifecycleOwner(maxLifecycle = if (isSettled && currentOverlayScenes.isEmpty()) RESUMED else STARTED)` around
each scene). An entry that has left the back stack is capped at `CREATED` by
`rememberBackStackAwareLifecycleNavEntryDecorator`, which is always installed (`SceneState.kt`). So inside an entry,
`LocalLifecycleOwner.current.lifecycle` is below `RESUMED` while that screen is moving, whenever the host is
`RESUMED`. One frame of slack either way: `rememberLifecycleOwner` (lifecycle-runtime-compose 2.11.0) applies its cap
from a `LaunchedEffect`, so a scene that has just settled reaches `RESUMED` in the effects of that frame, and a new
scene's owner starts at `INITIALIZED` until its first effects run. A tap in the frame a screen lands is therefore still
ignored, which is harmless.

## Fix
Make the two cards that cover the chrome ignore touches while their scene is not settled. Do it in `ScreenSurface`,
the one place both of them go through.

1. `CampfireApp.kt`, in `CampfireContent` before `NavDisplay` (around `:388`), capture the host's lifecycle, which
   is the activity, the window or the view controller:

   ```kotlin
   // What the screens' own lifecycles are compared with, see ScreenSurface.
   val hostLifecycle = LocalLifecycleOwner.current.lifecycle
   ```

   and pass it to both `ScreenSurface(hostLifecycle) { ... }` calls (`:438`, `:450`).

2. Replace `ScreenSurface` (`:626-631`), keeping its KDoc and adding a paragraph:

   ```kotlin
   /**
    * ... (existing text) ...
    *
    * It also takes no touches while it is moving - being dealt, taken away, or uncovered by the card above it leaving.
    * Navigation 3 holds every entry below RESUMED until its scene transition has settled, so an entry below RESUMED
    * in a host that is RESUMED is one that is moving. The editor leaves on a spring that has cleared a finger within
    * a tenth of a second, and without this the second tap of a double-tap on its Close landed on the Back arrow of
    * the song underneath and closed that too.
    */
   @Composable
   private fun ScreenSurface(
       hostLifecycle: Lifecycle,
       content: @Composable () -> Unit,
   ) {
       val entryLifecycle = LocalLifecycleOwner.current.lifecycle
       Surface(
           modifier = Modifier
               .fillMaxSize()
               .pointerInput(hostLifecycle, entryLifecycle) {
                   awaitEachGesture {
                       val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                       // Asked when the finger comes down rather than for every event: a gesture that started on a
                       // screen that had landed is the user's to finish, and one that started on a moving screen is
                       // not, even if the screen lands before the finger is lifted.
                       if (hostLifecycle.currentState == Lifecycle.State.RESUMED && !entryLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                           down.consume()
                           do {
                               val event = awaitPointerEvent(PointerEventPass.Initial)
                               event.changes.forEach { it.consume() }
                           } while (event.changes.any { it.pressed })
                       }
                   }
               },
           color = MaterialTheme.colorScheme.background,
           content = content,
       )
   }
   ```

   The down itself is consumed too, otherwise a child `clickable` that does not require an unconsumed down starts a
   press ripple. Consuming in the `Initial` pass works because it runs parent first: every `clickable`,
   `scrollable` and text field underneath then sees consumed changes and cancels (`waitForUpOrCancellation` returns
   null for a consumed up).

   Imports: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.compose.LocalLifecycleOwner`,
   `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.input.pointer.PointerEventPass`,
   `androidx.compose.foundation.gestures.awaitEachGesture`, `androidx.compose.foundation.gestures.awaitFirstDown`.

3. Why the host comparison is needed. The desktop window's lifecycle is `STARTED` whenever the window is not focused
   (Compose Multiplatform 1.12, `ComposeContainer.desktop.kt:573`: `!isDetached && !isMinimized && isFocused ->
   RESUMED`). An entry is always at or below its host, so without the comparison a click that focuses the window
   would be thrown away. Android before 10 pauses an activity in split screen that does not have the focus, for the
   same reason.

4. Do **not**:
   - base this on `CampfireViewModel.isNavigationTransitionRunning` or on `ReportNavigationTransition`. That flag is
     stuck at true after every pop (see Cause), and the screens would then take no touches at all.
   - put the modifier around `NavDisplay` or on `TopLevelScreenSurface`. Both cover the whole window, the rail's
     column included. A node with a `pointerInput` that is hit takes the event away from the rail, which is laid out
     under the screens as a sibling. `CampfireTopAppBar.kt` explains why nothing may cover that column. The top
     level screens do not need it anyway: the song screen slides in from the end, over their rows' overflow buttons
     first, and `openSongDetails` / `openEditor` already ignore a second push.
   - delay or debounce `navigateBack`. The system back gesture and the desktop's Escape go through it too, and a back
     that reverses a push that is still running has to keep working (the `navigationGeneration` workaround).
   - disable the app bar buttons with `enabled = false` while moving. That grays them out mid-slide, which is an
     animation narrating nothing.

## Tests
None (UI is untested).

## Verify
1. Android (`.debug` build): Songs, then a song, then Edit. Double-tap Close quickly: the editor closes and the song
   stays. Triple-tap: the same. Then with a single tap on Close and a tap on Back once the editor is gone: the song
   closes too (only taps made while a screen is moving are ignored).
2. Tap a song and at once tap the song screen's overflow button while it slides in: nothing opens. Once it has landed,
   the menu opens on the first tap.
3. Predictive back on the song screen: start the gesture, cancel it, and tap Back at once. The tap right after the
   cancel animation lands works (the scene settles when the cancel animation ends). Complete the gesture: it closes
   as before.
4. The interrupted-transition cases the `navigationGeneration` workaround exists for: open a song and press the
   system back while it is still sliding in. The song goes away and the list takes taps again once it has settled.
   It must never stay unresponsive. This is the check that the lifecycle always reaches RESUMED again.
5. Desktop (`./gradlew :app:desktop:run`): double-click the editor's Close. The song stays. Click into the window from
   another app's focus onto a song row while nothing is moving: it opens with that first click (host `STARTED` →
   nothing ignored).
6. iOS simulator: the edge swipe back from a song still works, and a double tap on the editor's Close leaves the song
   open.
7. Web: the same double-click check.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet, after "…so that the specs recognize it, since a scene only
exposes content keys.": add "The two cards that cover the chrome (`ScreenSurface`: the song details and the editor)
take no touches while they are moving, which is while Navigation 3 holds their lifecycle below `RESUMED` in a host
that is `RESUMED`. The second tap of a double-tap on the editor's Close otherwise landed on the Back arrow of the song
it uncovered. The top level screens are left alone, since anything over their whole area would take the rail's
touches."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 42 and 34 edit other parts of `CampfireApp.kt` (`:205-240`, `:481-529`), so run them one after another.
