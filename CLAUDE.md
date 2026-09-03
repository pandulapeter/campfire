# Campfire

Kotlin multi-module app (Android + JVM desktop) for browsing song lyrics/chords. Compose UI is shared between both platforms. Data comes from Google Sheets (song lists, via Retrosheet) and GitHub-hosted text files (song details).

## Architecture

Strict `api` / `implementation` module split at every layer. Only `:app:*` modules see implementations; everything else depends on `api` modules and gets wiring via Koin.

```
app:android / app:desktop        entry points, Koin startup, platform chrome
  presentation:android / :desktop   platform Compose shells
    presentation:shared             CampfireViewModel + all shared Compose UI
  domain:api / :implementation      use cases (single-method interfaces)
    data:repository:api / :implementation
      data:source:local:api  -> :implementation-android / :implementation-desktop  (Room)
      data:source:remote:api -> :implementation-jvm                                (Retrofit/Moshi/Retrosheet)
        data:model                  domain models, shared by everything
```

Data flow: `LocalSource`/`RemoteSource` -> `Repository` (emits `DataState<T>`, local-first then remote) -> use cases (`GetScreenDataUseCase` combines all repos into one `ScreenData` flow) -> `CampfireViewModel` -> `CampfireViewModelStateHolder` (Compose `State` wrapper) -> screens.

## Conventions

- Every module's Koin wiring lives in a top-level `Module.kt` exposing one `val xxxModule = module { ... }`. New bindings go there.
- Implementation classes are `internal` and named `<Interface>Impl`. Use cases are `operator fun invoke`.
- Repositories extend `BaseLocalDataRepository` (local only) or `BaseLocalRemoteDataRepository` (local + remote cache).
- Layer boundaries are crossed via mappers (`mapper/` packages), never by leaking entity/response types.
- No tests exist in this repo.

## Build

- Versions in `gradle/libs.versions.toml`; SDK/version/signing constants are set as **system properties** at the top of the root `build.gradle.kts` and read via `System.getProperty(...)` in module scripts.
- `./gradlew :app:android:assembleDebug` — Android APK
- `./gradlew :app:desktop:run` — desktop app; `:app:desktop:packageDistributionForCurrentOS` for installers
