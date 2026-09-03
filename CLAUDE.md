# Campfire

Kotlin Multiplatform app (Android + iOS + JVM desktop) for browsing song lyrics/chords. Compose UI is shared between all platforms. Data comes from Google Sheets (song lists, via Retrosheet) and GitHub-hosted text files (song details).

## Architecture

Strict `api` / `implementation` module split at every layer. Only `:app:*` modules see implementations; everything else depends on `api` modules and gets wiring via Koin.

```
app:android / app:desktop / app:ios        entry points, Koin startup, platform chrome (app:ios also holds the Xcode project)
  presentation:android / :desktop / :ios     platform Compose shells
    presentation:shared                      CampfireViewModel + all shared Compose UI
  domain:api / :implementation               use cases (single-method interfaces)
    data:repository:api / :implementation
      data:source:local:api  -> :implementation   (Room, all platforms; expect/actual only for the database location)
      data:source:remote:api -> :implementation   (Ktor + Ktorfit + Retrosheet + kotlinx.serialization)
        data:model                           domain models, shared by everything
```

Data flow: `LocalSource`/`RemoteSource` -> `Repository` (emits `DataState<T>`, local-first then remote) -> use cases (`GetScreenDataUseCase` combines all repos into one `ScreenData` flow) -> `CampfireViewModel` -> `CampfireViewModelStateHolder` (Compose `State` wrapper) -> screens.

## Conventions

- Library modules apply the convention plugins from `gradle/build-logic` (`campfire-library`, or `campfire-compose-library` when they contain Compose). These configure the Android, `desktop` (JVM), `iosArm64` and `iosSimulatorArm64` targets and derive the Android namespace from the Gradle path. Sources live in `src/commonMain/kotlin`; platform code goes in `androidMain` / `desktopMain` / `iosMain` via `expect`/`actual`.
- Shared code must stay JVM-free: no `java.*`, `KoinJavaComponent`, or JVM-only libraries. Use `kotlin.uuid.Uuid`, `androidx.compose.ui.text.intl.Locale`, `KoinPlatform.getKoin()`, and `import kotlinx.coroutines.IO` for `Dispatchers.IO`.
- `:app:android`, `:presentation:android`, `:presentation:android-debug-menu` are plain Android modules; `:app:desktop`, `:presentation:desktop` are plain JVM modules; `:app:ios`, `:presentation:ios` are Kotlin/Native-only.
- Every module's Koin wiring lives in a top-level `Module.kt` exposing one `val xxxModule = module { ... }`. New bindings go there.
- Implementation classes are `internal` and named `<Interface>Impl`. Use cases are `operator fun invoke`.
- Repositories extend `BaseLocalDataRepository` (local only) or `BaseLocalRemoteDataRepository` (local + remote cache).
- Layer boundaries are crossed via mappers (`mapper/` packages), never by leaking entity/response types.
- No tests exist in this repo.

## Build

- Versions in `gradle/libs.versions.toml` (including `android-compileSdk` / `android-minSdk`); app version and Android signing constants are set as **system properties** in the root `build.gradle.kts` and read via `System.getProperty(...)` in `:app:android` / `:app:desktop`. The iOS version lives in the Xcode project.
- `./gradlew :app:android:assembleDebug` — Android APK
- `./gradlew :app:desktop:run` — desktop app; `:app:desktop:packageDistributionForCurrentOS` for installers
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` — compile/link check of the iOS framework; run the app from Xcode (`app/ios/iosApp/iosApp.xcodeproj`) or with `xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 SYMROOT=<dir> OBJROOT=<dir> build`, then `xcrun simctl install/launch`.
