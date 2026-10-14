# Let the screens call the view model's holders instead of the forwarders left on `CampfireViewModel`

**Challenged:** amended — `:tools:screenshots` is a friend module of `:presentation` (`-Xfriend-paths` in its
build file), so its calls are rewired, not kept; the keep list names the members `:app:desktop` calls; the call-site
search covers method references and implicit receivers; KDoc links in `DialogType.kt` are added to the doc fixes;
`tools/screenshots/src/main/**` joins the files; and an allowance a rewrap grows is raised.

**Kind:** refactor  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, every
`:presentation` file (all source sets) that calls a removed forwarder, `tools/screenshots/src/main/kotlin/**`
(`Shot.kt`, `Promo.kt`, `Website.kt`, `Render.kt`), `ui/dialogs/DialogType.kt` (KDoc), `ui/print/CLAUDE.md`,
`ui/screens/settings/CLAUDE.md`, `ui/screens/setlists/CLAUDE.md`, `.github/scripts/size_allowances.json`

## Problem

The `code-style` skill: "a facade such as `CampfireViewModel` builds its holders and exposes them as `internal val`s,
which screens call directly; … no member only forwards to a holder unless a module outside `:presentation` needs it"
(`ui/CLAUDE.md`: "its own members serve other modules or span holders"). c590c7665 removed most forwarders, but about
40 one-line ones remain at 0f47f4bc9, e.g.:

```kotlin
internal suspend fun preparePrintSource(dialog: DialogType.Export, tabNoticeLabels: TabNoticeLabels) = exportController.preparePrintSource(dialog, tabNoticeLabels)
fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = setlistsController.createSetlist(title, description, date, isCountdownShown)
fun toggleMetronome() = metronomeController.toggleMetronome()
fun cancelSynchronization() = syncController.cancelSynchronization()
/** See [SetlistsController.reorderingSetlistFileName]. */
internal var reorderingSetlistFileName: String? get() = setlistsController.reorderingSetlistFileName ...
/** See [Navigator.settingsTab]. */  // settingsTab
```

So the facade is still 923 lines, and docs name the forwarders (`ui/print/CLAUDE.md:38`
`CampfireViewModel.preparePrintSource`, `ui/screens/settings/CLAUDE.md:48` `CampfireViewModel.settingsTab`,
`ui/screens/setlists/CLAUDE.md:24,29` `CampfireViewModel.createSetlist` / `reorderingSetlistFileName`; KDoc
`ui/dialogs/DialogType.kt:38,51,144,152` `[CampfireViewModel.duplicateSong]`, `[CampfireViewModel.setlists]`,
`[CampfireViewModel.searchCoverArt]`, `[CampfireViewModel.searchSongDetails]`).

## Fix

1. List every member of `CampfireViewModel` whose body only calls one holder: the one-line functions
   (`grep -nE "^\s*(internal |override )?(suspend )?fun .*\) = [a-zA-Z]+\.[a-zA-Z]+\("`), the multi-line ones the grep
   misses (`duplicateSong`), and the `/** See [...] */` delegating properties (`isLoading`, `setlists`, `tags`,
   `fontScale`, `syncState`, `metronomeSettings`, `songsScrollPosition`, `songGroups`, `tunerSettings`,
   `hasTurnedOnMicrophone`, `settingsTab`, `reorderingSetlistFileName`, `hasShownApp`, `userPreferences`), plus
   `showDialog`.
2. Keep a member when:
   - `:app:*` calls it. Today that is `userPreferences` (`AppIcon.kt`, `TitleBarAppearance.kt`), `requestExit`,
     `settleSynchronizationBeforeExit`, and `magnifyByTouchpad` (`CampfireDesktopApplication.kt`); `handleKeyEvent` is
     an extension in `presentation/src/desktopMain` and already calls holders. Re-grep `app/*/src` (Kotlin and Swift)
     before deleting.
   - It is an override (`onCleared`).
   - It does more than forward: passes `sortedSongs()` or `::showDialog`, a fixed argument such as
     `openOnboarding(isOpenedFromSettings = true)`, converts an argument (`setTagColor`'s `color?.id`), sends a
     message (`onChordShapeWrittenForStandardTuning`), spans holders (`connectSyncProvider`, `openTunerSheet`,
     `selectTopLevelDestination`, `selectToolsItem`), or builds a dialog from several values (`showSongDetailsLookup`,
     `fillSongMetadata`).
   `:tools:screenshots` is **not** a reason to keep one: its build file makes it a friend of `:presentation`
   (`-Xfriend-paths`, main and test compilations), so it already reads internal members (`settingsTab`,
   `reorderingSetlistFileName`, `chordLibrary`) and can call the holders.
3. For every other one: point each call site at the holder, then delete the forwarder. Call sites are in four forms;
   find all of them: `viewModel.name(...)` / `viewModel?.name`, method references `viewModel::name` (e.g.
   `SyncSettings.kt` `viewModel::cancelSynchronization`, `OnboardingScreen.kt` `viewModel::cancelSyncConnection` →
   `viewModel.syncController::cancelSyncConnection`), implicit receivers in `CampfireViewModel.*` extensions (e.g.
   `settingsTab` in `wasmJsMain/.../navigation/BrowserRouteInputs.kt`), and uses inside `CampfireViewModel` itself
   (`::showDialog`, `showDialog(...)` in `openTunerSheet` → `dialogHost`). Include `tools/screenshots/src/main`
   (`Shot.kt`, `Promo.kt`, `Website.kt`, `Render.kt`). The compile is the final check: no removed name may survive.
   Pure mechanical change; no behaviour change, no signature change of a holder (defaults match: `openEditor`,
   `synchronizeLibrary`).
4. Point the doc lines above at the holder (`ExportController.preparePrintSource`, `Navigator.settingsTab`,
   `SetlistsController.createSetlist`, `SetlistsController.reorderingSetlistFileName`, and in `DialogType.kt`
   `SongFilesController.duplicateSong`, `LibraryState.setlists`, `CoverArtSearchController.searchCoverArt`,
   `SongDetailsLookupController.searchSongDetails`), then grep every `CLAUDE.md`, skill, and KDoc for any other
   `CampfireViewModel.<removed name>`.
5. `python3 .github/scripts/check_sizes.py --update` to lower the allowances that shrank. A call site that rewraps
   past the 150-column limit may grow a file or function past its allowance: raise that allowance by the net gain.

Not affected: the Koin graph check in `app/di` (the constructor is unchanged); desktop/web keys (`handleKeyEvent` and
`AppShortcuts.perform` already call `navigator`, `fontScaleController`, and `metronomeController`); the committed
Baseline Profile (rules naming removed methods are ignored; it is regenerated at release).

Verify: `:presentation:desktopTest`, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:app:web:compileKotlinWasmJs`, `:tools:screenshots:test` (compiles
`src/main` too), `spotlessCheck`.

## Tests

None: a behaviour-preserving move; the compile and the existing UI tests cover it.
