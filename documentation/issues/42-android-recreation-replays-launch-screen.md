# 42 · Android: every rotation, system theme or language change and window resize freezes the screen and swallows taps while an invisible launch screen fades out

**Severity:** performance / wrong behaviour (Android only; happens on every configuration change that recreates the activity: rotation, dark mode or language switched in the system, font size, a foldable being folded or unfolded, a split-screen divider or a desktop-windowing window being resized, a hardware keyboard being attached) · **Area:** `:presentation` (`CampfireApp.kt`, `CampfireViewModel.kt`), `:app:android` (`CampfireActivity.kt`, read only)

## Symptom
1. Android phone, any build. Open the app and wait for it to settle on the song list.
2. Rotate the device.
3. The new orientation shows up noticeably late. The last frame stays frozen for 2 frames plus the whole launch
   screen fade (about 200–300 ms, longer on a slow device) before the rotated app is drawn. Any tap made in the
   first few hundred milliseconds after the new orientation appears does nothing.
4. Same thing when the system switches dark mode, when the system language or font size changes, and when a
   foldable is unfolded. On a device with resizable windows (tablets in desktop windowing, Chromebooks), dragging
   the window edge recreates the activity again and again, and each time the redraw is held back like this, so
   the resize stutters.

Nothing is lost, but it is a stall on the most common interaction there is, and the swallowed taps look like the app
ignoring the user.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt:205-240`. Whether the
launch screen has been taken away is remembered by the composition:

```kotlin
var isAppReady by remember { mutableStateOf(false) }
if (!isAppReady) {
    val opacity = remember { Animatable(1f) }
    ...
    LaunchScreen(...)
    ...
    LaunchedEffect(arePreferencesLoaded, hasLibraryToShow, isThemeSettled, areDrawablesLoaded) {
        if (arePreferencesLoaded && hasLibraryToShow && isThemeSettled && areDrawablesLoaded) {
            repeat(2) { withFrameNanos { } }
            opacity.animateTo(0f, fadeSpec)
            isAppReady = true
            onAppReady()
        }
    }
}
```

`CampfireActivity` declares no `android:configChanges`
(`app/android/src/main/AndroidManifest.xml:51-54`), so a configuration change destroys the activity and its
composition. The `CampfireViewModel` survives, so `arePreferencesLoaded`, `hasLibraryToShow` and the theme are all
already settled on the first frame of the new composition. `isAppReady`, though, starts again at `false`. The launch
screen is composed over the app at full opacity, waits two frames and then runs the whole fade.

The new activity also installs the pre-draw gate again unconditionally
(`app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt:37` and `:70-79`):

```kotlin
keepStartupScreenUntilAppIsReady()
...
override fun onPreDraw(): Boolean {
    if (!isAppReady) return false
```

`isAppReady` (the activity's own field, `:32`) only becomes true once `onAppReady` has been called at the end of the
fade. Until then every draw of the new window is cancelled, so the user watches the frozen rotation snapshot for the
whole fade. The fade itself is never seen. The `LaunchScreen` is a `Surface` (`CampfireApp.kt:261-284`) exactly so
that it swallows touches, so every tap made in that time is lost.

The other three shells never recreate their composition: iOS keeps its `ComposeUIViewController`, the desktop its
window, the web its page. So this is Android only. A process that was killed and restored gets a new
`CampfireViewModel` and should see the launch screen and the splash hold. That case is a cold start and has to keep
working.

## Fix
Remember "the app has been shown" where it survives a recreation, which is the view model, and let a composition
that starts after that skip the launch screen and release the shell straight away.

1. `CampfireViewModel.kt`, next to `hasLibraryToShow` (`:262-276`):

   ```kotlin
   /**
    * Whether the launch screen has already been taken away once. A plain flag rather than a state, since it is only
    * read as the root composition starts: Android recreates its activity, and with it the whole composition, on every
    * rotation, and a composition that started from nothing would put the launch screen back over an app the user is
    * already using. Worse, it would hold the new activity's first frame back until that screen had faded, which is
    * a frozen window and a few hundred milliseconds of lost taps on every configuration change. A process that is
    * started again gets a new view model, which is the start the launch screen is for.
    */
   internal var hasShownApp = false
   ```

2. `CampfireApp.kt:205-240`:

   ```kotlin
   // Read once: the composition that took the launch screen away is not always the one drawing the app (Android
   // recreates its activity on every configuration change), and one that starts after it has nothing to cover.
   val hasShownAppBefore = remember { viewModel.hasShownApp }
   var isAppReady by remember { mutableStateOf(hasShownAppBefore) }
   if (hasShownAppBefore) {
       // The shell still holds a startup screen of its own until it is told, the Android activity's pre-draw gate.
       LaunchedEffect(Unit) { onAppReady() }
   }
   if (!isAppReady) {
       ... unchanged, except that right before `isAppReady = true`:
           viewModel.hasShownApp = true
   }
   ```

   Keep `onAppReady()` where it is for the first composition. The two calls exclude each other: the new branch only
   runs when `hasShownAppBefore` is true, and then the `if (!isAppReady)` block is never composed.

3. Do **not**:
   - add `android:configChanges` to the activity. That changes what the whole app does on every configuration change
     (resources, insets, the Play update controller's `rememberSaveable`s) to fix one flag.
   - skip `keepStartupScreenUntilAppIsReady()` when `savedInstanceState != null`. A process restored after being
     killed also has a saved state and must keep the splash until its (new) view model has read the library.
   - use `rememberSaveable` for `isAppReady`. It would survive a process death as well, and then show a restored
     process's library half read, with no launch screen over it.
   - call `onAppReady()` from a `SideEffect`. It would run on every recomposition of `CampfireApp`, and the web's
     `campfireReady` is not meant to be called again and again.

## Tests
None (UI is untested).

## Verify
1. Android (`./gradlew :app:android:assembleDebug`, install the `.debug` build). Open the app on the song list and
   rotate. The rotated list appears with the usual rotation animation and no extra delay. Tap a song right after it
   appears: it opens. Compare with the build before the fix, where the first taps after the rotation do nothing.
2. Switch the system dark mode from the quick settings while the app is in front: the app redraws right away (with
   the theme cross fade `CampfireTheme` does anyway when the app follows the system), with no frozen frames first.
3. Cold start (swipe the app away, open it): the system splash still holds until the song list is ready, and there is
   no launch mark flash. Same when the app is opened onto a sync run that is going in the background.
4. Process death: open a song, background the app, `adb shell am kill com.pandulapeter.campfire.debug`, reopen it
   from Recents. The splash holds until the song is back, as before.
5. Desktop, web and iOS: the launch screen still fades on start as before (their composition is never recreated, so
   `hasShownApp` is only ever read as false).
6. Compile: `./gradlew :app:android:assembleDebug :app:desktop:compileKotlin :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet, after the sentence ending "…so that they hand over to the app
itself rather than to the last frames of a mark fading off it.": add "The launch screen belongs to the start of the
process and not to the composition: Android recreates its activity, and with it the whole composition, on every
rotation and every change of the system's theme, language or window size, and a composition that starts after the
app has been shown (`CampfireViewModel.hasShownApp`) composes no launch screen and releases `onAppReady` at once.
Otherwise every rotation would hold the new activity's first frame back behind an invisible fade and swallow the
taps made meanwhile."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 34 also edits `CampfireApp.kt` (the `Messages` composable, lines 481-529), not these lines.
