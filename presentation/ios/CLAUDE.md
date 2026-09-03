# :presentation:ios

iOS Compose shell around `:presentation:shared` (re-exported as `api` — TODO to make it an implementation detail). Mirrors `:presentation:android`; Kotlin/Native only (`iosArm64`, `iosSimulatorArm64`), sources in `src/iosMain`.

- `CampfireIosApp` — root composable. Calls `viewModel.onInitialize()`, sets up pull-to-refresh and `UiSize.fromScreenWidth(...)` from `BoxWithConstraints`, then hands everything to the shared `CampfireScaffold`. Takes a `urlOpener` callback so the module stays free of UIKit.
- `catalogue/CampfireIosTheme` — maps `UserPreferences.UiMode` to a Material theme.
- `screens/` — the Android screen arrangements with the platform suffix swapped.
- `utilities/KeyboardState` — IME visibility as Compose state.

There is no back handler: the song details screen is closed through its own controls. Keep screen behavior aligned with the Android module; anything non-trivial belongs in `:presentation:shared` instead.
