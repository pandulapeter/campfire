<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 35 — Sync waits for the first library load

| | |
|---|---|
| Lane | E |
| Impact | medium (high on the web, where both jobs share one thread) |
| Confidence | medium |
| Platforms | all |
| Files | `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`, `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`, `data/repository/implementation/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 37 and 39 (same file, different functions). Pairs with 36. |
| Commit message | `Start a sync run's local reads only once the library has been read for the screen.` |

## Problem
**The launch sync starts beside the first scan.**
- `CampfireViewModel.kt:853-870` launches `loadScreenData(false)` and, in a separate coroutine, `restoreSync()`.
- `RestoreSyncUseCaseImpl` (`domain/implementation/.../SyncUseCaseImpls.kt`) calls `synchronizeLibrary()` whenever the app starts connected.

**What the run does straight away.**
- `SyncRepositoryImpl.runSynchronization` (386 onward) → `engine.synchronize` → `SyncEngine.kt:138` `provider.list()`.
- Then `readLocalStates()` (`SyncEngine.kt:285-298`) reads every library file, 64 at a time, and hashes it with the pure-Kotlin SHA-256 (`readLocalState`, 300-304).

**Cost:**
- With about 2000 songs, a connected launch reads every file twice at the same moment: the scan reads and parses, the sync run reads and hashes.
- The Dropbox listing comes back in well under the time a large scan takes, so the two usually overlap.
- On Android, iOS and desktop they compete for storage and for `Dispatchers.Default`. On the web they compete for the one thread that also draws the list, so the library appears later on every connected launch, even when nothing needs syncing.

## Fix
Inside the run's `try` in `runSynchronization`, right after the progress is put on screen (after line 406) and before `loadIndex()`, wait for any first read that is still going:

```kotlin
// A launch starts this beside the first scan of the library, and both read every file: the screen's read goes
// first. Waited for rather than started - a repository that is not Loading has been read (or has failed, which a
// second read here would only repeat).
if (songRepository.songs.first() is DataState.Loading) songRepository.loadSongsIfNeeded()
if (setlistRepository.setlists.first() is DataState.Loading) setlistRepository.loadSetlistsIfNeeded()
```

**Why this works:**
- `loadDataIfNeeded()` holds the repository's read mutex, so it waits for the screen's in-flight read and then returns the cache without reading again (`BaseLocalDataRepository.kt:79-81`).
- If it finds a `Loading` left over by a cancelled read, it reads, which is what the screen needs anyway.
- Stopping the run while it waits cancels only this wait. A read cancelled here leaves `Loading(null)`, the state it found.

**Must not change:**
- The progress indicator stays up during the wait. The spinner with no total is exactly what the engine reports while listing.
- Nothing about what the run decides changes; only when it starts reading.
- Do not wait on a `Failure` state: that would read the whole library a second time for nothing.

## Verification
- Tests: `./gradlew :data:repository:implementation:desktopTest`. Add a `SyncRepositoryImplTest` case where the fake song repository is `Loading` until released: the engine's `loadLibraryFiles` must not be called before the release.
- **Manual check:** Android (profile build) with a connected account and about 2000 songs. Use a system trace or Logcat timestamps to measure cold start to the full list, before and after. In the web build, the list must appear before the sync progress starts counting files.
- **Docs:** add one sentence to the `SyncRepositoryImpl` paragraph of `data/repository/implementation/CLAUDE.md`: a run waits for a first library read that is still going before it reads local files.
