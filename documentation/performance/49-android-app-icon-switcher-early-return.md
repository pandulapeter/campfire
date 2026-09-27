<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 49 — Skip the launcher-icon PackageManager calls when the icon is already right

| | |
|---|---|
| Lane | F |
| Impact | low |
| Confidence | high |
| Platforms | Android |
| Files | `app/android/src/main/java/com/pandulapeter/campfire/AppIconSwitcher.kt` |
| Depends on / conflicts with | — (48 makes the calls rarer; the two are independent) |
| Commit message | `Remember the launcher icon the switcher last applied so that it asks the package manager nothing when it is unchanged.` |

## Problem
`CampfireAndroidApp.kt:64` runs `LaunchedEffect(appIconThemeColor) { appIconThemeColor?.let(onAppIconChanged) }` on the main thread in every new composition. That is every cold start, and every activity recreation (every rotation today, see 48). It calls `CampfireMainActivity.kt:62–65`, which calls `AppIconSwitcher.apply(context, themeColor, isLeaving = false)`.

In `AppIconSwitcher.kt:43–62`:

```kotlin
val hasNeverSwitched = packageManager.getComponentEnabledSetting(initialEntry) == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
if (hasNeverSwitched && (target == UserPreferences.ThemeColor.CAMPFIRE || !isLeaving)) return
ENTRIES.entries.sortedByDescending { (color, _) -> color == target }.forEach { (color, name) ->
    packageManager.setState(component = component(context, name), state = …)
}
packageManager.setState(component = initialEntry, state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
```

and `setState` (lines 64–68) calls `getComponentEnabledSetting` before each set.

For a user who has changed the icon color at least once, `hasNeverSwitched` is false. Every call then makes **13 synchronous IPCs** to the system's package manager, on the main thread, around the first frame: 1 for the initial entry and 11 in the loop, and one more for the initial entry at the end. They find nothing to change. That is roughly 1–6 ms per call on a quiet device, and more under load. `onStop` (`CampfireMainActivity.kt:96–102`) repeats it each time the user leaves.

## Fix
Keep a process-lifetime memo of the color the switcher has fully applied, and return before any IPC when it matches.

```kotlin
/**
 * The color whose launcher entry this process has already made the only enabled one, so that the switch asked for
 * on every composition and every stop costs nothing when the icon is already right. Only ever written after the whole
 * switch has been carried out (or found to be unnecessary for the app's own icon), never on the early return that
 * leaves the first switch for the user's way out.
 */
private var appliedTarget: UserPreferences.ThemeColor? = null

fun apply(context: Context, themeColor: UserPreferences.ThemeColor, isLeaving: Boolean) {
    val target = …                          // unchanged
    if (target == appliedTarget) return
    val packageManager = context.packageManager
    val initialEntry = component(context, INITIAL_ENTRY)
    val hasNeverSwitched = …                 // unchanged
    if (hasNeverSwitched && target == UserPreferences.ThemeColor.CAMPFIRE) {
        appliedTarget = target               // the manifest's own entry is already the right one
        return
    }
    if (hasNeverSwitched && !isLeaving) return   // the first switch waits for onStop: not recorded
    … the loop and the final disable, unchanged …
    appliedTarget = target
}
```

- Resolve `target` before any `PackageManager` access, so the early return costs no IPC. It already needs only `Build.VERSION` and `appIconColor`.
- The memo lives in the `internal object`, so it survives activity recreation and dies with the process. The first call of a process still reads the real state once. That keeps the class honest about a state changed from outside (an `adb shell pm` command, or a crash in the middle of a switch).
- Nothing else changes:
  - the order in which entries are enabled and disabled (a new entry before any old one is disabled);
  - the first-switch-on-leaving rule;
  - `DONT_KILL_APP`;
  - `setState`'s own check.

  Extend the class KDoc with one sentence about the memo.

## Verification
- `./gradlew :app:android:assembleDebug`.
- On the emulator (memory note `android-emulator-verification`; `pm` queries need no root):
  - **Fresh install, own color:** no switch happens. `adb shell pm dump com.pandulapeter.campfire.debug | grep -A2 CampfireActivity` shows the manifest defaults.
  - **Pick Red in Settings, then leave the app:** the first switch happens on the way out, exactly as today. The Red alias is enabled and `.CampfireActivity` disabled.
  - **Reopen and rotate a few times:** the icon stays Red. A temporary `Log.d` around the IPCs, or a `systrace` / Perfetto trace with the `binder_driver` category, shows no package manager transactions from the app's main thread after the first composition of the process.
  - **Pick Blue:** it switches at once (the later-switch path). Pick Red again, and back: each change switches exactly once.
- Kill the process (`adb shell am kill`) and reopen: one round of reads, then none.
