# Hold the first launcher icon switch while a click is playing, so locking the screen does not close the task under it

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android
**Files:** `app/android/src/main/java/com/pandulapeter/campfire/CampfireMainActivity.kt`, `app/android/CLAUDE.md`

## Problem

The first launcher icon switch disables `.CampfireActivity`, and "Android closes the tasks started from a component it
is told is *disabled*" (`app/android/CLAUDE.md`, measured on Pixel Launcher, API 37). That is why it "waits for the
user to leave": `onStop` makes it, except where the stop is not leaving (8ee010b36, `CampfireMainActivity.kt:93-105`):

```kotlin
override fun onStop() {
    super.onStop()
    val appIconColor = appIconColor
    if (appIconColor != null && !isChangingConfigurations && !isCoveredWithinTask()) {
        AppIconSwitcher.apply(context = this, themeColor = appIconColor, isLeaving = true)
    }
}
```

Locking the screen is also `onStop`, with nothing covering the task. A fresh installation: the welcome sheet offers
the theme color, the user picks one, opens a demo song, starts the click and locks the phone on the music stand — the
case the metronome's `mediaPlayback` service exists for (root `CLAUDE.md`: "the screen locked or another app in front
is a phone on a music stand and keeps it"). The first switch runs, the task is closed, the activity finishes,
`CampfireViewModel.onCleared()` calls `metronome.stop()` (`CampfireViewModel.kt:2736-2737`), and the click dies a
moment after the screen goes dark. It happens once per installation, but on the first use of the feature. The
documented trade-off is losing the user's *place* once; silencing a click that is playing is not part of it, the same
way the existing exemptions keep a picker or a consent page alive.

## Fix

In `onStop`, also skip the switch while the metronome's service is in the foreground:

```kotlin
if (appIconColor != null && !isChangingConfigurations && !isCoveredWithinTask() && !CampfireMetronomeService.isRunning) {
```

`CampfireMetronomeService.isRunning` (companion, `@Volatile`) is true from `startForeground` until the click stops,
and the service is started as the click starts (`onMetronomeNotificationChanged`), so it is set by the time the screen
can be locked. Nothing is lost by skipping: `AppIconSwitcher` records nothing on that path, so the switch is asked for
again at the next stop with no click playing — the user leaving the app normally. Later switches are made as the color
is picked (`isLeaving = false`) and are not affected; a later switch a device policy refused is only retried a stop
later.

Extend the KDoc of `onStop` ("A stop that only recreates the activity is not leaving, and neither is one caused by
another app's screen coming up inside this task …") with: "nor is a stop with a click playing, which is the screen
being locked over a song on a music stand: closing the task would silence it." In `app/android/CLAUDE.md`, after
"which is why only that one waits for the user to leave", add "(and not a screen locked over a playing click, which it
would silence)".

## Tests

None: an Activity callback and the package manager, which only a device exercises.

## Manual check

On an emulator or phone, uninstall, install the debug build fresh, pick a non-default color in the welcome sheet,
open a song, start the click, lock the screen: the click keeps playing and its notification stays. Unlock, stop the
click, go home: the launcher icon now has the picked color (`adb shell monkey -p com.pandulapeter.campfire.debug -c
android.intent.category.LAUNCHER 1` starts the new entry).
