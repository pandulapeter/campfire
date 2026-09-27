<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 30 — Poll the desktop's dark mode less often, once for the whole app, and not while minimized

| | |
|---|---|
| Lane | D |
| Impact | low (idle CPU and battery on laptops; no frame-time effect) |
| Confidence | high |
| Platforms | desktop |
| Files | presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/SystemDarkTheme.desktop.kt |
| Depends on / conflicts with | — |
| Commit message | `Ask the desktop for its dark mode once for the whole app, and less often.` |

## Problem
`SystemDarkTheme.desktop.kt:19-24`:
```kotlin
internal actual fun isSystemInDarkThemeLive() = produceState(currentSystemTheme == SystemTheme.DARK) {
    while (true) {
        delay(250)
        value = currentSystemTheme == SystemTheme.DARK
    }
}.value
```
- Every call site runs its own loop. `UiMode.isDarkTheme()` is read by `CampfireTheme` (always, whenever the mode is "System default", which is the default), and also by `ThemeColorChoice` while Settings → General or the welcome sheet is composed.
- Each loop wakes the main thread 4 times a second for the life of the process, including while the window is minimized.
- Each wake makes a native call (`org.jetbrains.skiko.currentSystemTheme`: an `NSUserDefaults`/`NSAppearance` read on macOS, a registry read on Windows, and `UNKNOWN` on Linux).
- The KDoc says this "costs nothing noticeable", and per wake that is true. The cost is the constant idle wakeups, which keep a laptop's CPU from sleeping deeply.

## Fix
1. **One poller for the whole app.** A top-level, lazily started `StateFlow`:
   ```kotlin
   private val systemDarkTheme: StateFlow<Boolean> = flow {
       while (true) { emit(currentSystemTheme == SystemTheme.DARK); delay(POLL_INTERVAL) }
   }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.WhileSubscribed(), currentSystemTheme == SystemTheme.DARK)

   @Composable internal actual fun isSystemInDarkThemeLive() = systemDarkTheme.collectAsStateWithLifecycle().value
   ```
   - `collectAsStateWithLifecycle` stops collecting below STARTED, so a minimized window stops the poll through `WhileSubscribed`. An unfocused but visible window keeps polling. That matters: the user changes the mode in System Settings while looking at Campfire behind it, and the app must follow.
   - Emit off the main thread. `stateIn` deduplicates, so the UI only hears about a real change.
2. **`POLL_INTERVAL` = 1 s.** A dark-mode switch shows within a second, which then cross-fades anyway (see `CampfireTheme`).
3. Update the KDoc: one loop, the interval, and that it pauses while the window is minimized.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run`.
- On macOS with the app in "System default", switch Appearance in System Settings with the Campfire window visible but unfocused: the app follows within about a second.
- Minimize the window and confirm with a temporary log that polling stops, and that it resumes on restore, picking up a change made meanwhile.
- Activity Monitor → Energy / "Idle Wake Ups" for the process drops.
