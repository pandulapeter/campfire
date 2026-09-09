# :app:android

Android application entry point. The only Android module that knows about implementation modules.

- `CampfireAndroidApplication` — starts Koin with `dataLocalSourceModule + dataRepositoryModule + domainModule + presentationModule`. Add new Koin modules here.
- `CampfireActivity` — single `AppCompatActivity`, edge-to-edge, hosts `CampfireAndroidApp` and opens links via Custom Tabs (colored to match the theme the composable reports). System bar appearance is handled inside `CampfireAndroidApp`. It also receives the files the system hands over: `ACTION_VIEW` ("open with") and `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (shared to Campfire), read off the main thread and passed to the UI through a `Channel`. The activity is `singleTask`, so a second file opened while Campfire is running arrives at `onNewIntent` rather than at a new instance.

`AndroidManifest.xml` registers Campfire **only** for the ChordPro extensions (`.cho`, `.chopro`, `.chordpro`, `.crd`, `.chord`, `.pro`), with a wildcard MIME type so that the `pathPattern`s are what actually decide — a `content://` URI has no extension in the eyes of the intent resolver unless a type is declared. Zip and plain text are deliberately not registered: the app reads one when it is handed over, but an app that claims them system wide answers for every archive and note on the device. The share filter is narrowed to `text/plain` for the same reason. Keep this list in step with `LibraryFiles.SONG_EXTENSIONS`.

A `FileProvider` (authority `${applicationId}.files`, paths in `res/xml/file_paths.xml`) lets a shared song leave the app's private storage as a content URI.

Build types: `debug` (`.debug` suffix, `internal.keystore`) and `release` (R8 + resource shrinking, signing from system properties). Contains the app's `AndroidManifest.xml`, launcher icon, and themes and colors (`values/` + `values-night/`) — the only Android XML resources in the project; everything the Compose UI draws lives in `:presentation`'s `composeResources`.
