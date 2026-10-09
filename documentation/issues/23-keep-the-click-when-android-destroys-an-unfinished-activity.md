# Keep the click when Android destroys the activity without finishing it ("Don't keep activities")

**Challenged:** amended — the observer skips a configuration change's ON_DESTROY, so a stale "kept" flag from a language or dark mode recreation can never keep a click at a later clear; ordering claim verified (LifecycleRegistry.backwardPass walks the observers newest first: `observerMap.forEachReversed` in lifecycle-runtime 2.11.0, the version the project resolves, `descendingIterator()` in 2.10.0; ComponentActivity 1.13.0 registers its ViewModelStore-clearing observer in its constructor).

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android (only with the developer option "Don't keep activities", or anything else that destroys the activity while the process lives)
**Files:** `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireAndroidApp.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/AppExitController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/CLAUDE.md`

## Problem

`AppExitController.onCleared` treats the view model being cleared as the user leaving the app. At b5c8ed3b5:

```kotlin
/**
 * ...
 * It does take the click, though. This is the app being left rather than being sent to the background - the one is
 * a finished Activity, the other a paused one - ...
 */
fun onCleared() {
    metronome.stop()
    // Nothing of the tuner outlives its screen, and with the view model every screen is gone.
    tuner.stopListening()
    tuner.stopTone()
    ...
}
```

On Android that assumption doesn't hold. `ComponentActivity` clears its `ViewModelStore` on `ON_DESTROY` whenever
`!isChangingConfigurations()`, whether or not the activity is finishing. With Developer options → "Don't keep
activities", the system destroys the activity every time it goes out of sight: Home, another app in front, and, on
the devices tried by the people who use that option, the screen being locked. In every one of those cases
`isFinishing` is false. The view model is cleared, the click stops, and the media notification goes with it.
`CampfireMetronomeService` exists precisely so the click keeps playing in the background on a music stand with the
screen off, so this is the feature breaking for anyone testing with that option on (testers, and some users who keep
it on to save memory). The activity comes back from the saved state, with the stack restored, on a song whose click
has stopped.

The real exit paths still need the stop:
- Backing out of the root activity finishes it on Android 11 and below (Android 12 and above only move the task to the
  back, which clears nothing), so `isFinishing` is true there; so is anything else that calls `finish()`, such as the
  update screen's (`AppUpdate.android.kt`).
- Swiping the task away is answered by `CampfireMetronomeService.onTaskRemoved` (`app/android/CLAUDE.md`).
- The desktop window being disposed and an iOS view model being cleared are always the app going away.

The tuner lines and the waiting writes in `onCleared` are right in every case. The tuner's screen is gone with the
activity, and the writes have to happen before the scope is cancelled.

There is one side effect to handle. A click that cannot sound is stopped a few seconds after the app goes out of
sight (`MetronomeController.onAppStopped` launches `silentClickStopJob` on the view model's scope). When the view model
is cleared inside that grace, the job is cancelled with its scope. If `onCleared` stops stopping the click, a silent
click would then play on under a foreground `mediaPlayback` service for silence, which is exactly what that job exists
to prevent.

## Fix

Tell the view model, as the activity is destroyed, whether it is finishing. Keep the click only when it is not.

1. **`CampfireAndroidApp`**, next to the existing `val activity = LocalActivity.current as? ComponentActivity`:

   ```kotlin
   // Android clears the view model with every activity that is destroyed outside a configuration change, finishing
   // or not ("Don't keep activities" destroys it on every trip to the background), and only a finishing one is the
   // app being left. Registered after ComponentActivity's own observer, so it hears ON_DESTROY first: the lifecycle
   // tells observers of a downward move newest first, and the view model is cleared by that older observer.
   DisposableEffect(activity, viewModel) {
       val observer = LifecycleEventObserver { _, event ->
           // Not for a configuration change, which keeps the view model: a flag set then would outlive it and answer a
           // later clear that this observer did not hear (an activity destroyed before its first composition).
           if (event == Lifecycle.Event.ON_DESTROY && activity != null && !activity.isChangingConfigurations) {
               viewModel.onHostDestroyed(isFinishing = activity.isFinishing)
           }
       }
       activity?.lifecycle?.addObserver(observer)
       onDispose { activity?.lifecycle?.removeObserver(observer) }
   }
   ```

   If that ordering were ever reversed, the flag would arrive after `onCleared` and the click would stop, as it does
   today. That failure is the safe one.

   The locale and `uiMode` changes still recreate the activity (`app/android/CLAUDE.md`), and their ON_DESTROY is not
   finishing either, but the view model survives them; skipping them keeps `isHostKeptForLater` written only by the
   destroy that clears it, so the default for any clear nobody announced stays the safe stop.

2. **`AppExitController`**: add

   ```kotlin
   /**
    * Set as an Android activity is destroyed without finishing, which clears the view model all the same while the
    * process, the metronome singleton and its service go on: the user has gone to the background, not left.
    */
   private var isHostKeptForLater = false

   fun onHostDestroyed(isFinishing: Boolean) {
       isHostKeptForLater = !isFinishing
   }
   ```

   and in `onCleared` replace `metronome.stop()` with

   ```kotlin
   if (isHostKeptForLater) stopSilentClick() else metronome.stop()
   ```

   `stopSilentClick` is a new constructor lambda, wired in `CampfireViewModel` to `metronomeController::stopSilentClick`.
   Rewrite the KDoc paragraph on the click: it is the app being left "unless the platform only let go of its window
   (an Android activity destroyed without finishing), where the click goes on as it does in the background, except
   one that cannot sound, which is stopped now instead of after its grace".

3. **`MetronomeController`**: remember what the last `onAppStopped(areBeatsFeltInBackground)` was told, and move the
   body of the delayed check into a function both use:

   ```kotlin
   /** The stop [onAppStopped] makes after its grace, made at once for a view model that is going (see AppExitController.onCleared). */
   fun stopSilentClick() {
       silentClickStopJob?.cancel()
       val playing = metronome.playback.value as? MetronomePlayback.Playing ?: return
       val isFelt = lastAreBeatsFeltInBackground && metronomeSettings.value.isHapticBeatEnabled && playing.pattern.hasUnmutedBeat
       if (!playing.pattern.canSound && !isFelt) metronome.stop()
   }
   ```

   `onAppStopped` keeps sending `Message.SilentMetronomeStopped` itself. A view model that is going has no snackbar
   left to show it in, so `stopSilentClick` sends nothing.

4. **`CampfireViewModel`**: add `fun onHostDestroyed(isFinishing: Boolean) = appExitController.onHostDestroyed(isFinishing)`.
   Desktop and iOS never call it, so their `onCleared` is unchanged.

Docs: in `ui/metronome/CLAUDE.md`, "Three rules stop a click outright": make the third
"`onCleared`, which is the app being left rather than being sent to the background (a finished Activity rather than a
paused one; an Android activity destroyed without finishing, as 'Don't keep activities' does on every trip to the
background, keeps a click that can sound)".

## Tests

None. The decision is a lifecycle flag in the view model and a lifecycle observer in the Android shell, neither of
which is unit-tested. `stopSilentClick` is a few lines lifted out of `onAppStopped`, which has no test of its own.

## Manual check

On an Android phone with Developer options → "Don't keep activities" **on**:

1. Open a song, start the click from the metronome panel, and press Home. The click goes on and the media notification
   stays. Reopen the app: it is on the same song, the panel shows the click playing, and Stop stops it.
2. Start the click, then lock the screen. The click goes on.
3. Set the click's volume to zero (or mute every beat), with Vibrate off, and press Home. The click stops at once and
   its notification goes.
4. Start the click and swipe Campfire away from Recents. The click stops (`onTaskRemoved`). On an Android 11 device,
   start it and press Back from the songs screen until the activity finishes. The click stops there too.
5. With the developer option **off**, repeat 1 and 4. The behaviour is the same as before the change.
