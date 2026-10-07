# Ask Play whether an update exists once the app is on screen, not while the splash is still up

**Kind:** performance (Android cold start)  ·  **Severity:** low  ·  **Platforms:** Android
**Lane:** S  ·  **Files:**
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.android.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.kt`,
`presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.desktop.kt`,
`presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.ios.kt`,
`presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/AppUpdate.wasmJs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/AppUpdateGate.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (only the
`isAppOnScreen` declaration near `hasShownApp`, ~line 567-574),
`presentation/CLAUDE.md`

## Problem

`AppUpdate.android.kt:77` at 491c4254a:

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.checkForUpdate() }
```

`AppUpdateGate` is composed as soon as the theme is (`CampfireApp.kt:306`), and the activity's first `ON_RESUME`
arrives while the system splash is still held (it is released by `onAppReady` once the preferences, the library and
the icons are in, `CampfireApp.kt:328-386`). So every cold start calls `appUpdateManager.appUpdateInfo`, which binds
to the Play Store's update service — on a 2 GB phone where the Play Store process is not resident, that spawns it, and
its start (a large process: class loading, its own disk reads) competes for the CPUs and the flash with Campfire's
own start, its library scan included. Estimated 100 ms or more of contention on a low-end phone; nothing of the
check's answer is needed before the app is on screen.

## Fix

Skip the check while the launch screen / splash is up, and make the first one as the app appears.

1. `CampfireViewModel.kt`: the existing `private val isAppOnScreen = MutableStateFlow(false)` (set by `hasShownApp`'s
   setter) becomes `internal val isAppOnScreen: StateFlow<Boolean>` backed by the private mutable one (rename the
   mutable to `_isAppOnScreen`; keep its KDoc). Do not touch anything else in the view model (lane U edits
   `songMetadataOf`).
2. `AppUpdate.kt`: `internal expect fun rememberAppUpdateController(isAppOnScreen: Boolean): AppUpdateController`,
   KDoc: the store is not asked anything while the launch screen is up; the first check is made as the app appears.
   The three `NoAppUpdates` actuals take and ignore the parameter.
3. `AppUpdateGate.kt`: `val isAppOnScreen by viewModel.isAppOnScreen.collectAsStateWithLifecycle()` and pass it.
4. `AppUpdate.android.kt`:

   ```kotlin
   val isAppOnScreenNow by rememberUpdatedState(isAppOnScreen)
   // A cold start's first resume comes while the splash is still up, where binding to Play's service can start the
   // Play Store's own process alongside this one's start; that check is made as the app appears instead.
   LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (isAppOnScreenNow) controller.checkForUpdate() }
   var hasMadeFirstCheck by remember { mutableStateOf(isAppOnScreen) }
   LaunchedEffect(isAppOnScreen) {
       if (isAppOnScreen && !hasMadeFirstCheck) {
           hasMadeFirstCheck = true
           controller.checkForUpdate()
       }
   }
   ```

   An activity recreated in a running process (language, dark mode, reclaimed) has `hasShownApp` already true, so
   `isAppOnScreen` is true from its first frame and its `ON_RESUME` checks at once, as today — which is what lets a
   recreated activity pick up a flexible download in progress (the controller's `init` drops a carried-over
   `Downloading`, and the first answer re-registers the listener). The saved `lastKnownState` keeps a blocking screen
   up across the recreation exactly as before.

No extra delay after the app appears (a timer after the first frame was considered and rejected: it would only push a
priority 4–5 blocking screen later and add a wait gate). A required update is therefore found a few hundred
milliseconds later on a cold start than now — Play's answer itself takes longer than that — and still covers the app
before it can be used for anything meaningful.

Root `CLAUDE.md`'s Updates section does not describe when the check is made, so it needs no change; add one clause to
`presentation/CLAUDE.md`'s `ui/platform/AppUpdate.kt` bullet: Play is first asked once the app is on screen, so a cold
start does not bind to its service (and maybe start the Play Store's process) under the splash.

## Tests

None: lifecycle and Play Core wiring, not pure logic.

## Manual check

- From an internal testing track with a newer build published at priority 2–3: cold start → the app appears, then the
  flexible-update dialog shows; at priority 4–5 the blocking screen covers the app right after it appears.
- Start the flexible download, rotate / switch dark mode mid-download: the Restart offer still appears when it ends.
- Perfetto trace of a cold start on a low-end phone with the Play Store process killed first
  (`adb shell am force-stop com.android.vending`): before the fix `com.android.vending` starts during Campfire's
  splash; after it, only once the app is on screen.
