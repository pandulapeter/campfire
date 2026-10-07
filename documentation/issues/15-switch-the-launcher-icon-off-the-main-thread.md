# Make the launcher icon's package manager calls on a background thread rather than on the main thread at cold start

**Kind:** performance (Android cold start)  ·  **Severity:** low  ·  **Platforms:** Android
**Lane:** S  ·  **Files:** `app/android/src/main/java/com/pandulapeter/campfire/AppIconSwitcher.kt`,
`app/android/src/main/java/com/pandulapeter/campfire/CampfireMainActivity.kt`, `app/android/CLAUDE.md`

## Problem

`presentation/src/androidMain/.../CampfireAndroidApp.kt:71` at 491c4254a runs
`LaunchedEffect(appIconThemeColor) { appIconThemeColor?.let(onAppIconChanged) }` as soon as the preferences arrive,
on the main thread and while the splash is still held; `CampfireMainActivity.kt:64-67` answers it with
`AppIconSwitcher.apply(context = this, themeColor = themeColor, isLeaving = false)`.

`AppIconSwitcher.apply` (`AppIconSwitcher.kt:52-85`) returns early only when `target == appliedTarget`, and
`appliedTarget` is a process-lifetime memo (the earlier performance review's plan 49), so it is `null` in every new
process. For anyone who has switched the icon color at least once (`hasNeverSwitched` false), a cold start therefore
makes 1 `getComponentEnabledSetting` for the initial entry, 11 more inside `setState` for each alias, and one more for
the initial entry — **13 synchronous binder calls to the package manager on the main thread** that find nothing to
change. Estimated 5–15 ms on a low-end phone under cold-start load, in the frames before the splash is released.

`app/android/CLAUDE.md` explains why the switch happens as soon as the preference is reported (and the first one only
in `onStop`), but nothing in it requires the calls to be on the main thread; `setComponentEnabledSetting(…,
DONT_KILL_APP)` and `getComponentEnabledSetting` are callable from any thread.

## Fix

Run every switch on one background thread owned by `AppIconSwitcher`, in order, so the main thread only enqueues:

```kotlin
/**
 * Every switch runs here, one after another: the package manager answers each call over binder, and the first call of
 * a process asks about every launcher entry - thirteen round trips the main thread made around the first frame.
 * One thread, so that a switch asked for as the color is picked and the one asked for as the user leaves never
 * interleave, and [appliedTarget] is only ever touched from it.
 */
private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "AppIconSwitcher").apply { isDaemon = true } }

fun apply(context: Context, themeColor: UserPreferences.ThemeColor, isLeaving: Boolean) {
    val applicationContext = context.applicationContext
    executor.execute { applyNow(applicationContext, themeColor, isLeaving) }
}

private fun applyNow(…) { /* the current body of apply, unchanged */ }
```

- Pass `context.applicationContext` (the activity may be gone by the time the task runs; `packageName` and
  `packageManager` are the same).
- `appliedTarget` stays a plain `var`: it is only read and written on the executor's thread.
- `CampfireMainActivity.onStop` keeps deciding *whether* to switch on the main thread (`isChangingConfigurations`,
  `isCoveredWithinTask()`, `CampfireMetronomeService.isRunning`) and only the package-manager work moves. The first
  switch, the one that closes the task, therefore happens a moment after `onStop` returns instead of inside it; the
  task is closed by the system either way, and a process killed before the queued switch runs is the same as a refused
  switch today — not recorded as applied, retried on the next stop. Update the KDoc of `onStop` and of
  `AppIconSwitcher` ("asked for on every composition and every stop … the first call of a process still reads the real
  state once" → once, on its own thread).

Rejected alternative: persisting the applied target (SharedPreferences) to skip the check entirely — a read of its own
on the main thread, and a stale value after a restore or an interrupted switch would leave two launcher entries.

Update `app/android/CLAUDE.md`'s launcher icon paragraph with one sentence: the switch runs on a thread of its own,
one switch at a time, so the first one of a process (which asks about every entry) costs the main thread nothing.

## Tests

None: Android framework calls in a plain Android module, which the project does not unit test.

## Manual check

On a device that has switched colors before: cold start with `adb shell am start -W` several times — no
`getComponentEnabledSetting` binder transactions on the main thread in a Perfetto trace (they appear on
`AppIconSwitcher`). Then change the color in Settings: the launcher icon follows as before; on a fresh install, pick a
color and leave the app: the first switch still happens on leaving, and coming back from the new icon opens the app.
