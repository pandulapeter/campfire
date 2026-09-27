<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 25 — Snap the app's colour scheme when the preferences first arrive; fade only the launch screen

| | |
|---|---|
| Lane | D |
| Impact | medium (every cold start of anyone whose stored theme differs from the system guess) |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/CampfireTheme.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | 26 (same `CampfireTheme` body; land 25 first). 32 edits the same launch-screen block of `CampfireApp.kt` (land 32 first or rebase). |
| Commit message | `Snap the app to its stored colors as the preferences arrive and fade only the launch screen's own.` |

## Problem
`theme/CampfireTheme.kt:66-90`: every change of `isDarkTheme to themeColor` animates `progress`, and every frame of it does two things:
```kotlin
MaterialExpressiveTheme(colorScheme = lerp(start, stop, progress.value), ...)          // :84
LocalSecondAccentColor provides lerp(secondAccentStart, secondAccentStop, progress.value) // :90, staticCompositionLocalOf (:109)
```
In the Material3 build this project uses (`org.jetbrains.compose.material3:material3-desktop:1.12.0-alpha03`, checked with `javap`):
- `ColorScheme` is immutable (`private final long primary; …`).
- `LocalColorScheme` is a `staticCompositionLocalOf`.

So each frame provides a new value to two static locals. The Compose runtime then recomposes **everything** under the provider with skipping disabled, and it does so on every frame of `defaultEffectsSpec`.

The first change of every launch is the correction from the system guess (`uiMode == null`, `themeColor == null` → Campfire palette, system light/dark) to the stored preference. It happens for everyone who picked a colour other than Campfire's, or a mode other than the system's.
- It starts in the frames in which `CampfireContent` is composed for the first time (`arePreferencesLoaded` turns on in the same emission), while the first library batches arrive.
- The whole app therefore recomposes, non-skipping, about 10–15 times on the main thread during the busiest stretch of the start.
- Nobody sees any of it: the opaque `LaunchScreen` (`CampfireApp.kt:317-340`) covers the app, and the launch screen waits for `isThemeSettled` anyway.

The fade exists for one reason, given in the KDoc: the *launch screen*, a mark on the background, must not blink from the guessed palette to the stored one.

## Fix
Make the first resolution snap for the app and fade for the launch screen only.

1. **In `CampfireTheme`, detect the first resolution.** A `null` `uiMode` together with a `null` `themeColor` means the preferences have not been read yet. `UserPreferences` has non-null defaults, so a read preference is never null.
   ```kotlin
   val isResolved = uiMode != null || themeColor != null
   var hasResolved by remember { mutableStateOf(isResolved) }
   LaunchedEffect(isDarkTheme to themeColor) {
       if (targetColorScheme === stop) return@LaunchedEffect
       if (!hasResolved && isResolved) {
           hasResolved = true
           // The app snaps: it is covered by the launch screen, whose own colors fade below.
           launchStart = lerp(start, stop, progress.value); launchStop = targetColorScheme
           start = targetColorScheme; stop = targetColorScheme
           secondAccentStart = targetSecondAccentColor; secondAccentStop = targetSecondAccentColor
           progress.snapTo(1f)
           launchProgress.snapTo(0f); launchProgress.animateTo(1f, MOTION_SCHEME.defaultEffectsSpec())
           return@LaunchedEffect
       }
       // …the existing fade for every later change (plan 26 replaces it)…
   }
   ```
2. **Hand the launch screen its colours as draw-phase values.** Add a parameter to the content lambda, or a small class passed with it, that exposes the launch screen's background and mark colours as lambdas:
   ```kotlin
   @Stable class LaunchScreenColors(private val background: () -> Color, private val mark: () -> Color) { … }
   // background = { lerp(launchStart.background, launchStop.background, launchProgress.value) }, likewise onSurfaceVariant
   ```
   Keep the whole signature change inside `CampfireTheme` and `CampfireApp`; `CampfireTheme` has no other caller (check with grep).
3. **In `LaunchScreen` (`CampfireApp.kt:317-340`), read those colours only while drawing, so its fade recomposes nothing:**
   - Replace `Surface(color = …)` with a `Box` that draws its background with `drawBehind { drawRect(colors.background()) }`. Keep the touch blocking that `Surface` gave, since the KDoc at `:309-312` requires it: add `Modifier.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() … } }`, or keep a `Surface(color = Color.Transparent)` wrapper for its input blocking.
   - Draw the mark with `painter` in `drawWithContent` / `Modifier.paint(painter, colorFilter = …)`, taking the tint from `colors.mark()` inside a `drawBehind` that calls `with(painter) { draw(size, colorFilter = ColorFilter.tint(colors.mark())) }`. `Icon(tint = …)` would read the colour in composition.
4. **`isThemeSettled`** becomes:
   ```kotlin
   targetColorScheme === stop && !progress.isRunning && !launchProgress.isRunning && typography != null
   ```
   so the launch screen still does not start its opacity fade while its colours are moving.
5. **With plan 32:** on Android and the web the platform's startup screen covers the launch screen as well. There, when `isStartupScreenHeldUntilAppReady` is true, `launchProgress` can snap too. Mention it in 32 if 25 lands first.

What must NOT change:
- A launch whose stored theme equals the guess is still no change at all (the identity check).
- The launch screen still never blinks.
- Later changes (Settings, the welcome sheet, the system flipping dark mode) keep their fade. Plan 26 is about their cost.
- `onBackgroundColorChanged` (`CampfireApp.kt:216`) now reports the final background at once. That is right: the desktop window behind the launch screen is only seen while resizing.

## Verification
- `./gradlew :presentation:desktopTest`, `:app:desktop:run`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:app:web:wasmJsBrowserDevelopmentRun`.
- On desktop (the only platform that shows the launch screen whole), set the Blue palette and the Dark mode with the system in Light, then cold start: the launch screen's background and mark still cross-fade to the dark blue background with no blink.
- Add a temporary recomposition counter (a `SideEffect { count++ }` in `CampfireContent`) and check it composes once or twice during startup rather than about 12 times.
- Changing the colour in Settings still fades as before.
- Update the `ui/theme/` paragraph of `presentation/CLAUDE.md`. Its "The first change of every launch is the app correcting the guess … cross faded like the rest" should say that the app snaps and only the launch screen fades. Update the KDoc of `CampfireTheme` too.
