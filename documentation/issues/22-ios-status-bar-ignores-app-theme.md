# 22 · iOS: with the app's theme set differently from the phone's, the clock and battery in the status bar are invisible, and pickers, the share sheet and the keyboard come up in the other theme

**Severity:** wrong behaviour (iOS; certain for everybody who picks Light or Dark in Settings → General rather than
"System default" and whose phone is in the other mode — e.g. Dark in the app for reading on stage, phone in Light) ·
**Area:** `:app:ios` (`CampfireViewController.kt`), `:presentation` iosMain (`CampfireIosApp.kt`)

**Verifier:** The style is set on every window of the app's scenes instead of on `controller?.view?.window`, which is null until the hosted view is in its window: a preference that arrives before that would be dropped for good, since the effect only runs again when the preference changes.

## Symptom
1. iPhone in Light mode. Campfire → Settings → theme **Dark**.
2. The status bar keeps drawing its time, signal and battery in black, now on the app's near-black background: they
   are gone. The reverse (phone Dark, app Light) draws them white on white.
3. Import opens the document picker in the phone's theme, the share sheet likewise, and the keyboard of the editor
   and of every dialog field is the light keyboard under a dark app.

Android does not have this: `CampfireAndroidApp` sets the system bar style from the app's theme on every change
(`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireAndroidApp.kt:63-68`), and even
Custom Tabs are told the app's scheme.

## Cause
Nothing on iOS tells UIKit what the app's theme is. `CampfireIosApp` (`presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireIosApp.kt`)
only hands over the pickers and the URL opener, and `CampfireViewController()`
(`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt:25-39`) builds the
`ComposeUIViewController` with the default configuration. In Compose Multiplatform 1.12.0 the hosting controller's
status bar style is `configuration.delegate.preferredStatusBarStyle ?: super.preferredStatusBarStyle()`
(`androidx/compose/ui/scene/ComposeHostingViewController.ios.kt:70-72`), i.e. `UIStatusBarStyleDefault`, which picks
dark or light content from the **trait collection's** interface style — the system's — and the SwiftUI
`UIHostingController` at the root (`iOSApp.swift` / `ContentView.swift`) does the same. `overrideUserInterfaceStyle`
is set nowhere in the project (`grep -rn overrideUserInterfaceStyle app presentation` finds nothing). The Compose
theme meanwhile follows the preference (`CampfireTheme.kt:92-96`), so the two disagree whenever the preference is
not `SYSTEM_DEFAULT`.

## Fix
Make the window's interface style follow the **preference**, which fixes the status bar, the system sheets and the
keyboard in one place, the way the Android shell follows it with `enableEdgeToEdge`.

1. `CampfireIosApp` gains a parameter, documented like the others:

   ```kotlin
   /**
    * @param onUiModeChanged Called with the theme preference whenever it changes, so that the app module can hand it to
    *   UIKit: the status bar, the system sheets and the keyboard follow the window's interface style, not Compose's.
    */
   onUiModeChanged: (UserPreferences.UiMode?) -> Unit = {},
   ```

   and, before `CampfireApp(...)`:

   ```kotlin
   val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
   val uiMode = userPreferences?.uiMode
   LaunchedEffect(uiMode) { onUiModeChanged(uiMode) }
   ```

2. `CampfireViewController()` passes `onUiModeChanged = ::applyInterfaceStyle`, a private function of the file:

   ```kotlin
   /**
    * Hands the theme preference to UIKit, whose status bar, system sheets and keyboard follow the window's interface
    * style rather than Compose's colors. Every window of the app rather than the controller's own: the status bar is
    * styled by the root controller, which is SwiftUI's, and the hosted view is not in its window yet when the first
    * preference can arrive.
    */
   private fun applyInterfaceStyle(uiMode: UserPreferences.UiMode?) {
       val style = when (uiMode) {
           UserPreferences.UiMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
           UserPreferences.UiMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
           UserPreferences.UiMode.SYSTEM_DEFAULT, null -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
       }
       UIApplication.sharedApplication.connectedScenes
           .filterIsInstance<UIWindowScene>()
           .flatMap { it.windows }
           .filterIsInstance<UIWindow>()
           .forEach { it.overrideUserInterfaceStyle = style }
   }
   ```

   Imports `platform.UIKit.UIUserInterfaceStyle`, `UIWindow`, `UIWindowScene`. The app declares a single scene
   (`UIApplicationSupportsMultipleScenes` is false in `Info.plist`), and SwiftUI has created its window before it
   asks for the view controller, so the one window is there from the first call. `:app:ios` already depends on
   `:data:model` (`app/ios/build.gradle.kts:29`), where `UserPreferences` lives.

   Why it works for "System default": with the override `Unspecified`, the window follows the system again, and
   Compose's `isSystemInDarkTheme()` on iOS reads the hosting controller's trait collection
   (`ComposeHostingViewController.userInterfaceStyleDidChange` → `LocalSystemTheme`, CMP 1.12.0), which is what the
   theme resolves `SYSTEM_DEFAULT` from.

Do **not**:
- pass the resolved `isDarkTheme()` instead of the preference. On iOS `isSystemInDarkTheme()` reads the trait
  collection this very override changes, so overriding to the resolved value for `SYSTEM_DEFAULT` would freeze the
  app in whatever the system was at that moment and it would no longer follow the phone switching modes;
- implement `preferredStatusBarStyle` through `ComposeUIViewControllerConfiguration.delegate`: the hosting controller
  is a child of SwiftUI's, whose own style is what the status bar reads, and it would leave the sheets and keyboard
  wrong.

## Tests
None (UI is untested).

## Verify
iOS simulator (`xcodebuild … -target iosApp …`, then install/launch; Device → Appearance toggles the system mode):
1. System Light, app Dark: time and battery are white; Import shows a dark document picker; the editor's keyboard is
   dark. Share from a song's menu: dark share sheet.
2. System Dark, app Light: black status bar content, light sheets and keyboard.
3. App "System default": toggle the simulator's appearance back and forth — the app, the status bar and the sheets
   follow every switch (this is the check that the override is `Unspecified` there).
4. Switch the app's theme while on Settings: the status bar flips with the cross fade.
5. `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` compiles.

## Docs
`app/ios/CLAUDE.md`, first bullet: add "It also sets the window's `overrideUserInterfaceStyle` from the theme
preference (`onUiModeChanged` of `CampfireIosApp`) — unspecified for "System default" — because the status bar, the
document picker, the share sheet and the keyboard follow UIKit's interface style rather than Compose's theme."
`presentation/CLAUDE.md`, the `iosMain/ui/CampfireIosApp.kt` bullet: mention the `onUiModeChanged` parameter and why
it carries the preference rather than the resolved dark flag.

## Touches
- `presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireIosApp.kt`
- `app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt`
- `app/ios/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on
Nothing. 28 also edits `CampfireViewController.kt` (the sync notifier wiring); schedule the two one after another.
