# :presentation:android

Thin Android shell around `:presentation:shared` (re-exported as `api` — flagged with a TODO to make it an implementation detail).

- `CampfireAndroidApp(urlOpener)` — obtains the `CampfireViewModel` with `koinViewModel()`, keeps the system bar icon colors in sync with the selected theme via `enableEdgeToEdge(...)` (the app theme may differ from the system theme), and hands off to the shared `CampfireApp`. The `urlOpener` receives whether the dark theme is active so Custom Tabs can match.

Everything else (navigation, predictive back, insets, screens) lives in `:presentation:shared`.
