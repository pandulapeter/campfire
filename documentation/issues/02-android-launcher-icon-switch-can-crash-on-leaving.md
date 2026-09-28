# Keep a refused launcher-icon switch from crashing the Android app

**Kind:** robustness  ·  **Severity:** medium  ·  **Platforms:** Android
**Files:** app/android/src/main/java/com/pandulapeter/campfire/AppIconSwitcher.kt

## Problem
`AppIconSwitcher.apply()` (`AppIconSwitcher.kt:54-80`) calls `PackageManager.setComponentEnabledSetting` through
`setState` (lines 82-86) with no `try`/`catch` on the path. It runs from two places, neither guarded either:
`CampfireMainActivity.onCreate`'s `onAppIconChanged` (through a `LaunchedEffect` in `CampfireAndroidApp.kt`) as soon
as the preferences are read or the color changes, and `CampfireMainActivity.onStop()`, the first switch, which runs
every time a user who has never switched leaves the app with a color other than the app's own.

`setComponentEnabledSetting` throws `IllegalArgumentException` when the component cannot be resolved and
`SecurityException` where a device policy restricts it (managed and work profiles, and some OEM launchers). Uncaught
in `onStop` that is a crash on the main thread every time such a user backgrounds the app, for as long as the color
stays chosen, and uncaught in the `LaunchedEffect` it is a crash on the frame the preferences load. The app ships no
crash reporting, so a crash loop tied to one device configuration is invisible to the developer. The neighbouring
platform calls in `CampfireMainActivity` (`startSyncService`, `dismissSyncService`) are guarded with exactly this
reasoning; the icon switch was left out.

## Fix
Wrap the switching part of `apply()` (from the first `setState` to the last) in `try { … } catch (exception: Exception)`
that logs with `println` and returns without setting `appliedTarget`, so the next `apply()` (next color change, next
stop) tries again rather than believing the switch happened. Keep the "enable the new entry before disabling the old
one" ordering inside the guarded block, since it is what keeps the app from having no launcher entry for a moment.
The `getComponentEnabledSetting` query at the top can throw the same `IllegalArgumentException` and belongs inside
the guard too.

## Verification
No unit test reaches `PackageManager`. Manual: temporarily make `setState` throw, pick a non-default color, leave the
app, come back: no crash, the icon simply stays. Remove the throw.

## Conflicts
None; nothing else in this batch touches `app/android`.
