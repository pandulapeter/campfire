# :app:android

Android application entry point. The only Android module that knows about implementation modules.

- `CampfireAndroidApplication` — starts Koin with `dataLocalSourceAndroidModule + dataRemoteSourceJvmModule + dataRepositoryModule + domainModule + presentationModule`, then initializes `DebugMenu`. Add new Koin modules here.
- `CampfireActivity` — single `AppCompatActivity`, edge-to-edge, hosts `CampfireAndroidApp`. Syncs status bar appearance with `viewModel.uiMode`, and opens links via Custom Tabs.
- `TemporaryWarningManager` — one-off "under development" dialog, marked for deletion.

Build types: `debug` (`.debug` suffix, `internal.keystore`) and `release` (R8 + resource shrinking, signing from system properties). Contains the app's `AndroidManifest.xml`, launcher icon, and themes (`values/` + `values-night/`).
