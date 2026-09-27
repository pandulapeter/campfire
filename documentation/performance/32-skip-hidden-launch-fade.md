<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 32 — Don't play the launch screen's fade where the platform's own startup screen hides it

| | |
|---|---|
| Lane | D |
| Impact | medium: about 170 ms off the time to the first frame of the app on every cold start on Android and the web |
| Confidence | high for Android and the web; iOS verified to still need the fade |
| Platforms | Android, web (iOS and desktop unchanged) |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt, presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.android.kt, presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.wasmJs.kt, presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.ios.kt, presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | 25 (same launch-screen block of `CampfireApp.kt`; whichever lands second rebases, and 25's launch colour fade can also snap where this flag is true) |
| Commit message | `Uncover the app at once where the platform's own startup screen hides the launch screen's fade.` |

## Problem
`CampfireApp.kt:261-296`. Once everything is ready, the code waits two frames, runs `opacity.animateTo(0f, fadeSpec)` (`defaultEffectsSpec` outside the desktop app, about 170 ms), and only then calls `onAppReady()`:
```kotlin
repeat(2) { withFrameNanos { } }
opacity.animateTo(0f, fadeSpec)
isAppReady = true
viewModel.hasShownApp = true
onAppReady()
```
On two platforms `onAppReady` is what takes the platform's own startup screen away, so the fade plays entirely underneath it:
- **Android:** `CampfireMainActivity.keepStartupScreenUntilAppIsReady()` returns `false` from `onPreDraw` until `onAppReady` sets `isAppReady`. Nothing is drawn at all, so the fade is never seen. The frame clock still ticks (Choreographer), which is why the animation runs and delays the release.
- **Web:** `index.html`'s loading page stays until `window.campfireReady()` (`CampfireWebApp`'s `dismissLoadingScreen`). Only then does its bar ease to full and the page fade (`index.html:330-370`).

`presentation/CLAUDE.md` already says it: "Android's splash and the web's loading page cover the fade". So every cold start on those two platforms waits for an animation nobody sees.

**iOS is different, which is why `isLaunchScreenWholeStartup` cannot decide this.** It is `false` on iOS (`Platform.ios.kt:16`), but iOS's `LaunchScreen.storyboard` is only the background colour (`app/ios/CLAUDE.md`). The system takes it away at the first frame by itself, and `CampfireIosApp` passes no `onAppReady` (`CampfireViewController.kt` does not use it). So on iOS the Compose launch screen, with the mark, *is* seen, and its fade is the handover to the app. It must stay.

## Fix
1. **Add a platform flag in `ui/platform/Platform.kt`:**
   ```kotlin
   /**
    * Whether a startup screen of the platform's own stays over the app until `onAppReady` - the Android activity's
    * pre-draw gate, the web page's loading screen - so that nothing the launch screen does is ever seen. False on iOS,
    * whose storyboard the system removes at the first frame, and on the desktop, where the launch screen is the whole
    * startup.
    */
   internal expect val isStartupScreenHeldUntilAppReady: Boolean
   ```
   Actuals: Android `true`, web `true`, iOS `false`, desktop `false`.
2. **In `CampfireApp`'s launch effect, where the flag is true:** skip the fade, uncover the app, and release the shell once the frame without the launch screen is certainly being drawn.
   ```kotlin
   if (arePreferencesLoaded && hasLibraryToShow && isThemeSettled && areDrawablesLoaded) {
       if (isStartupScreenHeldUntilAppReady) {
           isAppReady = true
           viewModel.hasShownApp = true
           // Two frames, for the reason given below: the composition without the launch screen has to be the one
           // that is drawn when the shell lets go, or Android's first frame would be the mark after all.
           repeat(2) { withFrameNanos { } }
           onAppReady()
       } else {
           repeat(2) { withFrameNanos { } }
           opacity.animateTo(0f, fadeSpec)
           ...as today
       }
   }
   ```
   Why keep two frames rather than calling `onAppReady()` in the same step:
   - `withFrameNanos` resumes while its frame is still being assembled (the existing comment).
   - On Android, releasing the pre-draw gate before the recomposition that removes `LaunchScreen` has been applied could let one frame of the mark through.
   - Two frames is about 33 ms, against the ~170 ms spring plus two frames today.
   - The two frames *before* the fade can go on these platforms, since nothing under the gate or the loading page is being shown.
   - On the web the bar's easing and the page's CSS fade then cover several more frames, so the canvas has certainly painted the app.
3. **If plan 25 has landed:** its launch-screen colour fade can snap when `isStartupScreenHeldUntilAppReady` is true, since it is equally unseen. Then `isThemeSettled` is reached at once.
4. Leave the rest alone:
   - Rotations and other recreated compositions (`hasShownAppBefore`) release `onAppReady` at once, as today.
   - Desktop and iOS keep their fades.

## Verification
- `./gradlew :presentation:desktopTest :app:android:assembleDebug :app:web:wasmJsBrowserDistribution :app:ios:linkDebugFrameworkIosSimulatorArm64`.
- **Android:** `adb shell am start -W -S com.pandulapeter.campfire.debug/…` several times before and after. `TotalTime`/`WaitTime` and the "Displayed" logcat line drop by about 150–200 ms. The splash hands over straight to the song list, with no frame of the mark (record the screen at 60 fps and step through it).
- **Web:** in the performance panel, `campfireReady` is called about 170 ms sooner after the preferences and library are ready, and no frame of the Compose launch screen is visible when the loading page fades.
- **iOS simulator and desktop:** unchanged (the mark fades, and on desktop grows).
- Update `presentation/CLAUDE.md` (the `ui/CampfireApp.kt` paragraph: "`onAppReady`, which fires once the mark has finished fading" and "there it stays the plain, quicker dissolve") and the `ui/platform/Platform.kt` bullet (add the new flag).
