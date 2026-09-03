# :presentation:android

Android Compose shell around `:presentation:shared` (re-exported as `api` — flagged with a TODO to make it an implementation detail).

- `CampfireAndroidApp` — the root composable. Calls `viewModel.onInitialize()`, wires `BackHandler` to close the song details screen, sets up pull-to-refresh and `UiSize.fromScreenWidth(...)` for the phone/tablet layout split, then hands everything to the shared `CampfireScaffold`. Takes a `urlOpener` callback so the module stays free of Custom Tabs.
- `catalogue/CampfireAndroidTheme` — maps `UserPreferences.UiMode` to a Material theme.
- `screens/` — Android-specific arrangement of the shared screen components.
- `utilities/KeyboardState` — IME visibility as Compose state.

Layout is chosen from screen width, not navigation routes: there is no nav library, `selectedNavigationDestination` in the shared view model drives a `Crossfade`.
