<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

> **Decision:** Decided by the user on 2026-09-27: execute with the recommended default throttle.
# 23 — Rescan the library when the app comes to the front, not on every focus, and without a loading flicker

| | |
|---|---|
| Lane | D |
| Impact | medium-high on desktop (every window focus), medium on iOS |
| Confidence | high |
| Platforms | desktop, iOS (the two where `isLibraryEditableOutsideApp` is true); the `isLoading` part touches all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/CLAUDE.md, app/desktop/CLAUDE.md, app/ios/CLAUDE.md |
| Depends on / conflicts with | 24 (same ViewModel init block and `refresh`; land 23 first). 02 and 18 also edit `CampfireViewModel.kt`, in other regions. |
| Commit message | `Rescan the library when the app comes to the front rather than on every focus, and without a loading state.` |

## Problem
`CampfireApp.kt:197-202`:
```kotlin
if (isLibraryEditableOutsideApp) {
    var hasResumedBefore by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasResumedBefore) viewModel.refresh() else hasResumedBefore = true
    }
}
```

**When it fires:**
- On desktop the window is RESUMED only while it has focus. `ScreenSurface`'s KDoc (`CampfireApp.kt:986`) says so: "a desktop window that does not have the focus is only STARTED". So every alt-tab, every click back into the window, and every return from a native file dialog rescans.
- On iOS, ON_RESUME follows every `didBecomeActive`. That includes Control Center, Notification Center, the system alert after an icon switch, and the Files picker closing.

**What one `refresh()` costs** (`CampfireViewModel.kt:1204` → `LoadScreenDataUseCase(isRescan = true)` → `SongRepositoryImpl.rescan()`):
1. It lists the songs folder, then reads and parses the metadata of **every** `.cho` file (`SongLocalSourceImpl.loadSongs`). This is on Default/IO, but it is the whole library.
2. `songContentRepository.invalidate()` emits `null`. The ViewModel collector (`CampfireViewModel.kt:884-898`) then re-reads every text in `_songTexts`, one after another, copying the whole map on each write. Plan 24 fixes that part.
3. `BaseLocalDataRepository.readOnce()` publishes `DataState.Loading(previousData)` and then `Idle(newInstances)`. As a result:
   - `isLoading = screenData.map { it is DataState.Loading }` (`CampfireViewModel.kt:317`) flips true and back to false.
   - The roots of SongsScreen (`:121`), SetlistsScreen (`:194`) and SongDetailsScreen (`:153`) each recompose twice.
   - The dialog watchdog in `init` (`:917`) pauses and resumes.
4. The rescan returns new `Song` instances with equal content. Every `asState` `distinctUntilChanged` downstream (`allSongs`, `setlists`, `tags`, `languages`, the three lists of `IndexedSongInput`, `songsByFileName`, …) does a full field-by-field comparison on the main thread. The identity short-circuit in `data class equals` no longer applies.

## Fix
**The reason this exists must stay.** On desktop the library is a folder the user can open (see `app/desktop/CLAUDE.md`), and on iOS the Files app can change it. Edits made outside the app have to show up when the user comes back.

1. **Trigger.** Rescan on `Lifecycle.Event.ON_START`, and skip the first ON_START as the current code skips the first resume.
   - On iOS this is `willEnterForeground`: the app coming back from the home screen or another app. Control Center and alerts no longer trigger it.
   - On desktop ON_START is the window becoming visible again (restored from minimized). Before implementing, confirm how Compose Desktop 1.12 maps window state to lifecycle.
2. **Desktop focus, kept but throttled.** A desktop user commonly edits a song in another window side by side and clicks back into Campfire without minimizing. ON_START alone would miss that and regress the reason above. So on desktop, keep ON_RESUME as a trigger too, but throttle it: rescan only if at least `MIN_RESCAN_INTERVAL` (e.g. 10 s) has passed since the last rescan finished. Keep the throttle in the ViewModel so it survives recomposition:
   ```kotlin
   private var lastRescanAt: TimeMark? = null
   fun refreshIfStale(minInterval: Duration = MIN_RESCAN_INTERVAL) {
       if (lastRescanAt?.let { it.elapsedNow() < minInterval } == true) return
       viewModelScope.launch { loadScreenData(true); lastRescanAt = TimeSource.Monotonic.markNow() }
   }
   ```
   Use `kotlin.time.TimeSource.Monotonic`, which works in common code.

   **DECISION NEEDED (small):** the exact interval, and whether ON_START should bypass the throttle. My recommendation: ON_START always rescans, and ON_RESUME on desktop only when stale.
3. **No loading state for a re-read of a library that has already been read once.** `isLoading` cannot simply become `it is Loading && it.data == null`. During the first read the partial batches arrive as `Loading(partial)`, and `rememberHasLoadedLibrary` / the list animation rules (`ListItemAnimation.kt:41-48`) rely on `isLoading` staying true until the last batch. Latch it instead:
   ```kotlin
   private data class LoadingLatch(val isLoading: Boolean, val hasBeenRead: Boolean)
   val isLoading = screenData
       .runningFold(LoadingLatch(isLoading = true, hasBeenRead = false)) { latch, state ->
           val hasBeenRead = latch.hasBeenRead || state is DataState.Idle
           LoadingLatch(isLoading = state is DataState.Loading && !hasBeenRead, hasBeenRead = hasBeenRead)
       }
       .map { it.isLoading }
       .asState(true)
   ```
   - The first read and its partial batches behave exactly as today.
   - A retry after a first read that *failed* still shows loading, because `Failure` does not set `hasBeenRead`. The retry buttons call `refresh()` (`SongsScreen.kt:349`, `SetlistsScreen.kt:289`), and that must stay unthrottled. Only the lifecycle path uses `refreshIfStale`.
   - Once a read has completed, a rescan never flips `isLoading`. A re-read publishes no partial data (`isPublishingPartialData = previousData == null`), so what is on screen meanwhile is the previous, complete library. The two other consumers (SongDetailsScreen's "every song is gone" check at `:154-160` and the dialog watchdog at `CampfireViewModel.kt:917`) are correct to act on it.
4. Leave Android and the web untouched. `isLibraryEditableOutsideApp` is false there.

What must NOT change:
- The first launch's skip of the initial resume or start.
- A rescan still invalidates the song texts.
- "That resume, the read the app starts with and the retry of a list that failed to load are the only things that ever re-read the library" stays true.

## Verification
- `./gradlew :presentation:desktopTest :app:desktop:run` and `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`.
- Desktop, with a library of about 2,000 songs:
  - Alt-tab away and back five times within 10 s: there is one rescan at most (add a temporary log in `SongRepositoryImpl.rescan`).
  - Edit a `.cho` file in the library folder from a text editor next to the window, wait longer than the interval, and click back in: the change shows.
  - Minimize and restore: the change shows immediately.
- iOS simulator: pull down Control Center and dismiss it: no rescan. Home, then back: rescan.
- In both, no row fade or placeholder flicker, and the Layout Inspector or recomposition counts show SongsScreen's root not recomposing on refocus.
- Update the docs in the same commit:
  - The `ui/CampfireApp.kt` paragraph of `presentation/CLAUDE.md` ("It also rescans the library when the app is resumed…").
  - `app/desktop/CLAUDE.md:46` ("hence the rescan when the window regains focus").
  - `app/ios/CLAUDE.md:33` ("rescans on resume here").
  - The KDoc comment above the effect.
