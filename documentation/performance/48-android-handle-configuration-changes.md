<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Approved by the user on 2026-09-27: execute, verified on the emulator against every listed site.
# 48 — Handle size and orientation changes in place instead of recreating the activity

**DECISION NEEDED.** This changes how the Android shell lives through rotation, folding and window resizing. Several places were written around recreation. They stay correct, but some of their comments become half-true. Please decide:

- (a) whether to take this at all, and
- (b) which configuration changes to handle. The recommendation below deliberately leaves the system language and the system dark mode to recreation.

| | |
|---|---|
| Lane | F |
| Impact | medium on tablets, foldables, ChromeOS and desktop windowing (every resize); low on phones (rotation only) |
| Confidence | medium |
| Platforms | Android |
| Files | `app/android/src/main/AndroidManifest.xml`, `app/android/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (KDoc only), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt` (comment only) |
| Depends on / conflicts with | Conflicts with 32 (lane D, `CampfireApp.kt`, the launch-screen block around lines 240–295): land 32 first and adjust the comment on top of it. Conflicts with 44 and 45 (`app/android/CLAUDE.md`). Makes 49's win smaller, but 49 still stands on its own. |
| Commit message | `Keep the Android activity through size, orientation and density changes instead of recreating it.` |

## Problem
`AndroidManifest.xml:59–62` declares `CampfireMainActivity` with no `android:configChanges`, and the application is `android:resizeableActivity="true"` (line 43). So every one of the following destroys the activity and builds the whole Compose tree again:

- a rotation;
- a fold or unfold;
- a split-screen or freeform resize that crosses a size bucket;
- a keyboard being attached;
- a density or font-scale change.

Rebuilding means the root composition, theme, `NavDisplay`, the current screen with its lists, every `LaunchedEffect` restarting (`CampfireAndroidApp.kt:64`'s icon switch included, see 49), and the pre-draw gate being installed again. On a phone that is a hitch per rotation. In a freely resizable window (Android 16 desktop windowing, ChromeOS, tablets in split screen) it can happen repeatedly during one drag. The code already works hard to hide this: see the KDoc of `CampfireViewModel.hasShownApp` at `CampfireViewModel.kt:379–387` ("a frozen window and a few hundred milliseconds of lost taps on every configuration change").

## Fix
**Recommended set.** In `AndroidManifest.xml`, on `CampfireMainActivity`:

```xml
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|fontScale|keyboard|keyboardHidden|navigation"
```

The rest of the UI follows without code changes. `AndroidComposeView` updates `LocalConfiguration`, `LocalDensity` (fontScale included) and the window size on its own. `ProvideInterfaceScale` (`InterfaceScale.kt:40–48`) derives from `LocalDensity`. `WindowSize.fromWidth`, `LocalWindowInfo.containerSize` (`SongEditorScreen.kt:318`, `ListItems.kt:979`) and the `BoxWithConstraints` of the details, the editor and the dialogs all follow the new size in place. Add an XML comment above the attribute saying why, and why two changes are left out:

- **`locale|layoutDirection` left to recreation.** `ApplyLanguagePreference` (`theme/Language.kt:24–33`) reads `androidx.compose.ui.text.intl.Locale.current`. On Android that is the JVM default locale, not composition state, so a handled locale change would leave "System default" in the old language until the next recreation. The same goes for `languageDisplayName` (`java.util.Locale`).
- **`uiMode` left to recreation.** Compose would follow it (`isSystemInDarkTheme()` reads `LocalConfiguration`), but the window background and splash colors come from the `values`/`values-night` theme. They are only re-read on recreation, and plan 45 pins exactly those. A system dark-mode flip is rare enough to keep recreating for.

**Code that assumes recreation.** Every place below keeps working, because recreation still happens for the changes left out and for the system reclaiming the activity. List them in the commit's notes, and fix the comments marked *update*.

1. `CampfireMainActivity.kt:44–55`: `savedInstanceState == null` and `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` keep a recreated activity from importing the intent again. Still needed (a system reclaim, a language or dark-mode change). No change.
2. `CampfireMainActivity.kt:90–102`: `onStop` skips the first icon switch when `isChangingConfigurations`. With handled changes there is simply no `onStop` for them. Still correct. No change.
3. `CampfireApp.kt:249–258`: `hasShownAppBefore = remember { viewModel.hasShownApp }` keeps the launch screen from coming back over a recreated composition. The comment "(Android recreates its activity on every configuration change)" at line 252 → *update* to "on the configuration changes it does not handle itself (see the manifest), and when the system reclaims it".
4. `CampfireApp.kt:547–549`: `NavDisplay`'s `transitionSpec` checks `viewModel.hasShownApp`. No change.
5. `CampfireViewModel.kt:379–387`: KDoc of `hasShownApp`, "Android recreates its activity … on every rotation" → *update* the same way.
6. `CampfireViewModel.kt:814`: messages held by the view model because "Android recreates that screen". Still true for the remaining cases. No change, or soften the wording.
7. `CampfireApp.kt:663`: a message shown again after the composition was recreated. No change.
8. `SongEditorScreen.kt:144` and `:821`: the editor's text lives through a configuration change in the view model, and "a rotation restores through here as well". After this change a rotation no longer goes through that path, but a language change or a reclaim still does. *Update* the second comment, which names rotation as the example.
9. `dialogs/Dialogs.kt:975` and `:1191`: saved selections because "the dialog outlives a recreated Activity". No change.
10. `platform/AppUpdate.android.kt:55`, `:133`, `:213`, `:256`: picking up a flexible download in a recreated activity. No change.
11. `platform/FilePicker.android.kt:91`, `:119`, `:149`: a picker result arriving at a recreated activity ("a rotation, or the system reclaiming the Activity"). No change, apart from optionally dropping "a rotation" from the example at line 91.
12. `data/source/remote/implementation/…/auth/SyncAuthenticator.android.kt:120`: activity lifecycle callbacks. Unaffected.
13. **Custom Tabs colors** (`CampfireMainActivity.openUrl`) take `isDarkTheme` from Compose. Unaffected.

**Documentation.**
- `app/android/CLAUDE.md`: the `CampfireMainActivity` bullet gains a sentence on which configuration changes the activity handles itself and why locale and uiMode are not among them.
- The recreation wording in the activity paragraph ("a rotation as much as the system bringing the app back") becomes "a language or dark mode change as much as …".

Must not change: the handling of intents on recreation, the first-icon-switch timing, the launch-screen logic, or anything on the other platforms.

## Verification
- `./gradlew :app:android:assembleDebug`, then on the `Resizable_Experimental` emulator (memory note `android-emulator-verification`), check each of these:
  - Rotate on the song list, on a song's details (scroll position and text size kept), in the editor with unsaved text (text, caret and the unsaved state kept), and with a dialog and a bottom sheet open. Nothing flickers, and the launch screen does not reappear.
  - Resize the window across 600 dp and 840 dp: the navigation bar/rail switches in place.
  - Change display size (`adb shell wm density 480`, then `wm density reset`) and font size in Settings: the layout follows without recreation.
  - Switch the system language: the app recreates and "System default" follows.
  - Toggle the system dark mode: the app recreates, and the window, splash and bars are in the right colors.
  - Open a file with "Open with", rotate, and confirm it is not imported twice.
- Measure the rotation hitch with `adb shell dumpsys gfxinfo com.pandulapeter.campfire.debug reset`, rotate five times, then read `dumpsys gfxinfo`: janky frames and the 99th percentile drop against the build before.
