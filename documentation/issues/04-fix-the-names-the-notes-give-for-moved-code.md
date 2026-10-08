# Fix the class and function names in the notes and KDoc that point at code that moved or never existed

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `app/di/CLAUDE.md`, `data/sync/implementation/CLAUDE.md`,
`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/LibraryChanges.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/CLAUDE.md`, `app/web/CLAUDE.md`
**Challenged:** amended — every row checked at HEAD and given its exact replacement; the sync notification row also fixes the following sentence's "It" (the rescan is `CampfireApp`'s, not the notification helper's); the navigation row's premise was wrong (the transitions never pick the editor out of a scene, only the top level destinations); the web loading-screen row names `CampfireColorScheme`; the two-frame wait is `CampfireApp`'s, not the web shell's.

## Problem

These notes name symbols that do not exist at HEAD (`git grep` finds them only in the docs). Some were made stale by
the refactor; others were stale before it and got copied into the new directory files when the notes were split.

| File | Says | Replace with |
|---|---|---|
| `app/di/CLAUDE.md:17-18` | "naming the six module objects (`DataLocalSourceModule`, `DataRemoteSourceModule`, `DataRepositoryModule`, `DomainModule`, `MetronomeModule`, `PresentationModule`)" | "naming the seven module objects (`DataLocalSourceModule`, `DataRemoteSourceModule`, `DataRepositoryModule`, `DataSyncModule`, `DomainModule`, `MetronomeModule`, `PresentationModule`)" — the order of `CampfireDependencyGraph.kt:36-42`. |
| `data/sync/implementation/CLAUDE.md:69` | "`DataRepositoryModule.syncEngine` builds it with" | "`DataSyncModule.syncEngine` builds it with" (`Module.kt:50`). Only the name; line 17's "the `Logger` from `DataRepositoryModule`" is correct (`DataRepositoryModule.logger`) and stays. |
| `LibraryChanges.kt:18` KDoc | "(see `SyncRepositoryImpl.scheduleSynchronization`)" | "(see `SyncRunScheduler.schedule` in `:data:sync:implementation`, which collects it)" — `SyncRunScheduler.kt:65` is `libraryChanges.changes.collect { schedule() }`; backticks, not a `[link]`, since this module cannot see it. |
| `presentation/.../ui/CLAUDE.md:158-160` | "`SyncNotificationEffect` only ever reports … in the background. It also rescans the library when the app comes back to the front" | "`rememberSyncNotifications` (in `CampfireApp.kt`) only ever reports … in the background. `CampfireApp` also rescans the library when the app comes back to the front" — the helper is the private `@Composable fun rememberSyncNotifications(viewModel)` (`CampfireApp.kt:333`, the `hasShownNotification` latch); the `ON_START` rescan and `refreshIfStale` are `CampfireApp`'s own `LifecycleEventEffect`s (`CampfireApp.kt:116-135`), so the "It" of the next sentence has to name it. |
| `presentation/.../ui/CLAUDE.md:97` | "Tagging a song (`setSongTags`, `removeSongTag`)" | "Tagging a song (`setSongTags`)". |
| `ui/dialogs/CLAUDE.md:199` | "uses `campfireBottomSheetContainerColor` for its background" | "uses `sheetContainerColor()` for its background" (`CampfireBottomSheet.kt:105`; the parenthesis after it, light background / Material's lighter sheet container in dark, matches the code and stays). |
| `ui/navigation/CLAUDE.md:22` | "`SongEditor` also answers `isContentKey` so that `CampfireApp`'s transitions can pick the editor out of a scene." | "`SongEditor`'s `contentKey` is its file name behind a prefix of its own, leaving `shouldStartInsideFirstSection` out, since that says how the editor opens rather than which editor it is." Nothing picks the editor out of a scene: `NavigationTransitions.kt` (lines 58-59, 160-161) only tells the top level destinations from the cards, with `TopLevel.fromContentKey` on each scene's last entry, which the same paragraph's list of keys already implies. |
| `app/web/CLAUDE.md:19` | "(the `CampfireColorSchemes` palettes, picked by `prefers-color-scheme`)" | "(the light and dark backgrounds of `:presentation`'s `CampfireColorScheme`, picked by `prefers-color-scheme`)" — `ui/theme/CampfireColorScheme.kt:34`, `#FAF8FE` / `#15121C`, the values `index.html`'s `--background` repeats (its own comment already says "CampfireColorScheme's"). |
| `app/web/CLAUDE.md:40-42` | "`DismissLoadingScreen` in `CampfireWebApplication.kt` waits two frames before reporting ready — `withFrameNanos` resumes while its own frame is still being assembled — so the fade uncovers the app rather than an empty page." | "`CampfireApp` waits two frames after the launch screen is taken away before it calls `onAppReady` — `withFrameNanos` resumes while its own frame is still being assembled — and `CampfireWebApp` (`presentation/src/wasmJsMain`) passes `dismissLoadingScreen()`, which calls `window.campfireReady()`, so the fade uncovers the app rather than an empty page." — the wait is `repeat(2) { withFrameNanos { } }` in common `CampfireApp.kt:214` and `:252`; `dismissLoadingScreen` (`CampfireWebApp.kt:147`) only calls the page. |

## Fix

Correct each entry as described. Change only the names and what is needed around them; keep the paragraphs otherwise
as they are and wrap at 120 columns. Before rewording a sentence, read the code it describes, so the new name is
accurate, not just existent. When done, `git grep` each old name: no hits outside the generated Baseline Profile.

## Tests

None (docs). `python3 .github/scripts/check_license_headers.py` and `./gradlew :data:source:local:api:compileKotlinDesktop`
for the KDoc change.

## Manual check

None.
