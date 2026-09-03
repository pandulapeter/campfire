# :presentation:desktop

Desktop Compose shell around `:presentation:shared` (re-exported as `api` — TODO to make it an implementation detail). Mirrors `:presentation:android`.

- `CampfireDesktopApp(viewModel, stateHolder, windowSize)` — root composable; `UiSize` comes from the window width rather than screen configuration, and refresh is a `CircularProgressIndicator` in the app bar instead of pull-to-refresh. Links open through `java.awt.Desktop`.
- `catalogue/CampfireDesktopTheme` — `UiMode` to Material theme.
- `screens/` — desktop arrangement of the shared screen components.

Keep screen behavior aligned with the Android module; anything non-trivial belongs in `:presentation:shared` instead.
