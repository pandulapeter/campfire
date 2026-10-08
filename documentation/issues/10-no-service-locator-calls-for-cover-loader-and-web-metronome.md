# Stop the cover image loader and the web shell looking dependencies up from Koin on their own

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all (cover loader), web (metronome)
**Challenged:** amended — "the last collector" and "`MetronomeController.start()`" contradict once plan 01 has landed
(that controller's other collectors start mid-`init`), so the rule gets a start function of its own called last; the
effect now lives in its own file (`wasmJsMain/.../ui/MetronomeStartableEffect.kt`), which is deleted.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CoverArt.kt`
(`ProvideCoverArtImageLoader`, `createCoverArtImageLoader`, the `KoinPlatform` import); `ui/CampfireApp.kt`
(the `ProvideCoverArtImageLoader()` call); `presentation/src/wasmJsMain/.../ui/CampfireWebApp.kt`
(the `MetronomeStartableEffect(viewModel)` call); `presentation/src/wasmJsMain/.../ui/MetronomeStartableEffect.kt`
(deleted); `ui/CampfireViewModel.kt` (a new collector in
`init`, or `MetronomeController` after plan 01); `ui/metronome/MetronomeContext.kt` (new pure
`isMetronomeStartable`); `commonTest/.../metronome/MetronomeContextTest.kt`; `presentation/CLAUDE.md` (the
`CampfireWebApp.kt` paragraph mentions `Metronome.setStartable`; the `CoverArt.kt` paragraph)
**Depends on:** none

## Problem

Two places bypass the dependencies the rest of the UI is handed:

- `CoverArt.kt:67` builds Coil's singleton loader with a service-locator call made whenever Coil first asks for it:
  ```kotlin
  internal fun ProvideCoverArtImageLoader() = setSingletonImageLoaderFactory { context -> createCoverArtImageLoader(context) }
  private fun createCoverArtImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
      .components { add(CoverArtKeyer()); add(CoverArtFetcher.Factory(getCoverArt = KoinPlatform.getKoin().get())) } …
  ```
  The dependency on `GetCoverArtUseCase` is invisible at the call site in `CampfireApp`, resolved at an arbitrary later
  moment, and would fail at runtime rather than at the composable's call if Koin were not started (the Koin compiler
  plugin's compile-time graph check cannot see a `get()` call).
- `CampfireWebApp.kt:160` fetches the engine a second way, beside the view model that already owns it:
  ```kotlin
  private fun MetronomeStartableEffect(viewModel: CampfireViewModel) {
      val metronome = koinInject<Metronome>()
      LaunchedEffect(viewModel, metronome) {
          snapshotFlow { viewModel.backStack.lastOrNull() }
              .combine(viewModel.userPreferences.map { it?.isMetronomeEnabled != false }) { top, isEnabled ->
                  isEnabled && (top == CampfireDestination.Metronome || top is CampfireDestination.SongDetails)
              }.distinctUntilChanged().collect(metronome::setStartable)
      }
  }
  ```
  Every other metronome decision (start, stop, what to play) is the view model's; this one rule about which screens
  may open the audio device lives in a platform shell, untested, and talks to the singleton behind the view model's
  back. `Metronome.setStartable` is common API that does nothing outside the web (its KDoc says so), so nothing forces
  it to be web-only code.

(`androidMain`'s `koinInject<AndroidFilePicker>()` in `FilePicker.android.kt` is a platform shell resolving a
platform singleton the activity also registers with; it is left alone.)

## Fix

1. `ProvideCoverArtImageLoader(getCoverArt: GetCoverArtUseCase = koinInject())` — resolved where it is called, in
   composition, the way the rest of the UI resolves its Koin dependencies (`koinViewModel()`, `koinInject`) — and pass
   it to `createCoverArtImageLoader(context, getCoverArt)`. Remove the `KoinPlatform` import. `CampfireApp`'s call is
   unchanged. One commit.
2. Add a pure rule next to `isMetronomeScreenLeft` in `ui/metronome/MetronomeContext.kt`:
   `internal fun isMetronomeStartable(top: CampfireDestination?, isMetronomeEnabled: Boolean) = isMetronomeEnabled && (top == CampfireDestination.Metronome || top is CampfireDestination.SongDetails)`.
   Move the collector into the view model's `init` as the **last** launch, so the order of the existing ones does not
   change (after plan 01: a separate `MetronomeController.startStartableRule()`, called last in `init`, not folded into
   the controller's other start calls, which run mid-`init`):
   `combine(snapshotFlow { backStack.lastOrNull() }, userPreferences.map { it?.isMetronomeEnabled != false }, ::isMetronomeStartable).distinctUntilChanged().collect(metronome::setStartable)`.
   Delete `MetronomeStartableEffect` (its file) and its call from `CampfireWebApp`. Behaviour difference to check: the effect only
   ran while the web composition existed, the collector runs for the view model's lifetime — on the web those are the
   same (one page, one view model); elsewhere `setStartable` is a no-op. One commit.

## Tests

`MetronomeContextTest`: `isMetronomeStartable` is true on `Metronome` and `SongDetails` with the feature on, false on
`Songs`, `Setlists`, `Settings`, `SongEditor`, `ImportReport`, and false everywhere with the feature off.

## Manual check

Web: on the Songs screen tap around (no audio permission prompt / no audio context started); open the Metronome tab
and press play (click starts on the first press); open a song and press its metronome button, then play (starts);
switch the Metronome feature off and on. All platforms: covers still load on song cards and in the song details bar.
