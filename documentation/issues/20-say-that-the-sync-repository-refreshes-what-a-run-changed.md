# Say in the repository contracts that the sync repository refreshes what a run changed, and that an import ends with `adoptImported`

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SongRepository.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SetlistRepository.kt`,
`data/repository/api/CLAUDE.md`

## Problem

The interface docs still describe how sync and imports reached the lists before they were reworked:

- `SyncRepository.kt:22-26` (8ee010b36): "A run that changed files on disk therefore has to be followed by a rescan of
  those, which is the job of `SynchronizeLibraryUseCase`." `SynchronizeLibraryUseCaseImpl`
  (`domain/implementation/.../SyncUseCaseImpls.kt:105-112`) only calls `syncRepository.synchronize(...)` and returns
  before the run does; the lists are refreshed by `SyncRepositoryImpl` itself (`refreshLibraryAfterRun`,
  `finishRunCutShort`, the live refresh), and per file with `refresh(fileNames)`, not with `rescan()` — which
  `data/repository/implementation/CLAUDE.md` already says ("The use case cannot, now that it returns before the run
  does").
- `data/repository/api/CLAUDE.md:14-15`: `rescan()` is "re-read the folder, which is what a refresh and the end of an
  import do"; `:36-38`: "a run that changed files has to be followed by a `rescan()`, done by
  `SynchronizeLibraryUseCase`"; `:45`: "A `rescan()` is the only thing that re-reads the library folder" — next to a
  `refresh(fileNames)` that re-reads files too.
- `SongRepository.rescan`'s KDoc (`SongRepository.kt:27-28`): "what a refresh and an import need"; the setlist one
  (`SetlistRepository.kt:35`): "what a rescan and an import need". An import ends with `adoptImported`.

Someone following the interface docs would add a rescan to the use case and race the repository's own refresh.

## Fix

Docs only:
- `SyncRepository` KDoc: "…the library itself keeps living in the song and setlist repositories. A run writes files
  behind their backs, so this repository hands them the files it changed (`SongRepository.refresh`,
  `SetlistRepository.refresh`) while it runs and whichever way it ends."
- `SongRepository.rescan` / `SetlistRepository.rescan`: "Reads the whole directory again — the Settings refresh
  action, and a sync run that ended in something other than an exception." (Check `SyncRepositoryImpl.rescanLibrary`
  and the presentation's callers of the rescan use case for the exact list before writing it.)
- `data/repository/api/CLAUDE.md`: list `refresh(fileNames)` and `adoptImported` next to `rescan()` in the
  `SongRepository` bullet, with what each is for; in the `SyncRepository` bullet, replace the `SynchronizeLibraryUseCase`
  sentence with "which is why a run hands the files it changed to their `refresh`, done by `SyncRepositoryImpl`
  itself"; change ":45" to "A `rescan()` is the only thing that lists the library folder again; `refresh` re-reads
  named files, and everything else keeps the cached list in step by updating the one entry it changed…".

## Tests

None (KDoc and CLAUDE.md only).

## Manual check

None.
