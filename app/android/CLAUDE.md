# :app:android

Android application entry point. The only Android module that knows about implementation modules.

- `CampfireAndroidApplication` — starts Koin with `dataLocalSourceModule + dataRemoteSourceModule + dataRepositoryModule + domainModule + presentationModule`. Add new Koin modules here.
- `CampfireActivity` — single `AppCompatActivity`, edge-to-edge, hosts `CampfireAndroidApp` and opens links via Custom Tabs (colored to match the theme the composable reports). System bar appearance is handled inside `CampfireAndroidApp`.

Build types: `debug` (`.debug` suffix, `internal.keystore`) and `release` (R8 + resource shrinking, signing from system properties). Contains the app's `AndroidManifest.xml`, launcher icon, and themes (`values/` + `values-night/`).
