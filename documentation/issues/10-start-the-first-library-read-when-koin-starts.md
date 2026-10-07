# Start the first preferences and library read when Koin starts, not when the first composition creates the view model

**Kind:** performance (startup)  ·  **Severity:** medium  ·  **Platforms:** all (most on desktop, then Android)
**Lane:** S  ·  **Files:** `app/di/src/commonMain/kotlin/com/pandulapeter/campfire/di/CampfireDependencyGraph.kt`,
`app/di/build.gradle.kts`, `app/di/CLAUDE.md`

## Problem

The first read of the preferences, the songs and the setlists is started by the view model's constructor
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1263-1264` at
491c4254a):

```kotlin
init {
    viewModelScope.launch { loadScreenData(false) }
```

and the view model is created by `koinViewModel()` inside the first composition:
`app/desktop/.../CampfireDesktopApplication.kt:193` (inside `application { Window { … } }`),
`presentation/src/androidMain/.../CampfireAndroidApp.kt`, `presentation/src/iosMain/.../CampfireIosApp.kt` and
`presentation/src/wasmJsMain/.../CampfireWebApp.kt`. Koin itself is started much earlier:
`CampfireAndroidApplication.kt:21` (`Application.onCreate`), `CampfireDesktopApplication.kt:95` (before
`application { }`), `CampfireWebApplication.kt:21` (before `ComposeViewport`) and `CampfireViewController.kt:37`.

So nothing touches the disk until the platform has brought up its window and rendering stack and composed far
enough to reach the view model: on the desktop the AWT toolkit, the Skiko/Skia context and the window (about
0.5–1.5 s on a low-end Windows 2-in-1, estimated), on Android the activity, its window and the first Choreographer
frame (150–400 ms on a low-end phone, estimated), on the web the canvas and the Wasm Compose runtime. The launch
screen / splash is held until the preferences *and* the library are there (`CampfireApp.kt` ~line 362,
`arePreferencesLoaded && hasLibraryToShow`), so the whole read sits after that init instead of overlapping it.

What makes an earlier start safe, verified at 491c4254a:

- `BaseLocalDataRepository.loadDataIfNeeded` (`data/repository/implementation/.../base/BaseLocalDataRepository.kt`)
  is `mutex.withLock { _dataState.value.takeUnless { it is DataState.Loading || hasReadFailed }?.data ?: read() }`:
  the lock is held for the whole read, so the view model's own `loadScreenData(false)` waits for the read already in
  flight and then returns its cached result instead of reading again. A preload that failed sets `hasReadFailed`, so
  the view model's call reads again, as today.
- Reading writes nothing that decides first-run behaviour: `UserPreferencesLocalSourceImpl.loadUserPreferences` only
  reads (a missing document answers the defaults, nothing is written), and `IsFirstRunUseCase` asks
  `fileStorage.exists(PREFERENCES, "preferences.json")` on its own, so `plantDemoLibraryOnFirstRun`,
  `showWelcomeOnFirstRun` and `forgetSyncConnection` (all started by the same `init`) see exactly what they see now.
  The one write a read makes — a setlist file that names no date is given today's date and saved right after the read
  (root `CLAUDE.md`, Conventions) — happens on every start today too, only later.
- The desktop already starts Koin after `claimSingleInstance` (`CampfireDesktopApplication.kt:91`, "Koin must not
  be, since its singletons are what would read the library a second time"), so a second process still exits before
  anything is read.
- Android processes started without UI: auto backup runs in restricted mode without the app's `Application` class;
  both foreground services are `START_NOT_STICKY` and only started by the running app. The one remaining case is
  another app opening an exported file through the `FileProvider` after Campfire's process died, which would now read
  the library once for nothing — harmless, and rare.

## Fix

Start the read from `startCampfireDependencyGraph()` in `:app:di`, right after `startKoin`, so all four entry points
get it without being edited (in particular `CampfireDesktopApplication.kt`, lane D, is not touched):

```kotlin
/**
 * Starts Koin with every module of the app, and the first read of the preferences and the library with it. ...
 */
fun startCampfireDependencyGraph(configuration: KoinAppDeclaration = {}) =
    startKoin<CampfireDependencyGraph>(configuration).also { koinApplication ->
        // The read the launch screen waits for, started before the platform has brought its window up rather than
        // when the first composition gets as far as the view model: the view model asks for the same read and joins
        // this one, since a repository holds its lock for as long as its read takes.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                koinApplication.koin.get<LoadScreenDataUseCase>().invoke(false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // The view model asks again and reports what it finds; nothing here may keep the app from starting.
                println("Could not start reading the library: ${exception.message}")
            }
        }
    }
```

- Resolve the use case **inside** the coroutine, so the construction of the repositories, local sources and the file
  storage (which the view model would otherwise construct on the main thread) also leaves the main thread; Koin's
  singleton creation is synchronized, so the view model resolving the same singletons at the same moment is safe.
- Keep `viewModelScope.launch { loadScreenData(false) }` in the view model unchanged: it is what reads again after a
  failed preload, and what a test or a future entry point without the preload relies on.
- `:app:di` needs `implementation(libs.kotlin.coroutines)` in `app/di/build.gradle.kts` (check first whether it is
  already on the classpath through `koin-core`; add it explicitly either way rather than leaning on a transitive one).
- The use case's KDoc says its three reads "run side by side in the caller's own scope, so a caller that goes away
  takes the reads with it". The preload's scope never goes away, which is intended (the read belongs to the process);
  no change to the use case is needed.

Alternative considered: a `@Single(createdAtStart = true)` preloader class. Koin Annotations 4.2.2 declares
`Single(createdAtStart)` and the pinned compiler plugin (1.2.1) carries the flag in its definition model, so it would
probably work, but it hides the ordering in an annotation and constructs the preloader (and its whole dependency
chain) synchronously inside `startKoin` on the main thread. The explicit call is preferred.

Update `app/di/CLAUDE.md`'s `startCampfireDependencyGraph` bullet: it also starts the first read of the preferences
and the library, which the view model joins.

## Tests

None: this is wiring of the Koin entry point and timing across threads, which the project does not unit test
(CLAUDE.md, "Only pure logic is tested"). `BaseLocalDataRepositoryTest` already covers a second caller of
`loadDataIfNeeded` waiting for the first one's read; run
`./gradlew :data:repository:implementation:desktopTest` to confirm it still passes.

## Manual check

- Desktop, a library of ~2,000 songs, on the slowest machine available: time from launching to the launch screen
  fading into a full list, before and after (`campfire.log` timestamps, or a stopwatch over several cold starts). The
  window must not appear later than before (on a 2-core machine the read now competes with Skia start-up). If the
  window itself is visibly later, report it instead of adding a delay.
- Android cold start (`adb shell am start -W`): `TotalTime` to the splash release with a large library, before/after.
- First run on every platform (delete the data directory): demo songs planted, welcome sheet shown, nothing read twice
  (the desktop log shows one scan).
- A second desktop instance launched while one runs still exits without reading anything.
