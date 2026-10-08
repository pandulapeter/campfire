# Stop the view model reaching into a UI global, a platform call and screen packages: an owned `OverlayState`, an injectable persistence request, and shared types moved out of `screens/`

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Challenged:** amended — `SettingsTab` is imported by `tools/screenshots` (`Shot.kt`), which the move must update (and
it must stay public); the menu's `DisposableEffect` is keyed on the `OverlayState` instance it counted into, so a
re-provided state (the desktop shell swaps view models) never decrements the wrong counter; moves defer to the
package-move pass's new packages.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/OverflowMenu.kt`
(`openOverflowMenuCount`, `isAnyOverflowMenuOpen`, the `DisposableEffect` in `OverflowMenu`);
`ui/CampfireViewModel.kt` (`openCurrentSearch`, `isSongTextZoomable`, `magnifyByTouchpad`, `toggleMetronomeByKey`,
`libraryPersistence`, the imports of `SettingsTab`, `SONG_METADATA_FIELDS`, `FontScaleAccumulator`, `PINCH_SENSITIVITY`,
`hasSongInfo`, `followingLibraryFileNames`); `ui/CampfireApp.kt` (provide the local);
`screens/setlists/SetlistsScreen.kt` (`isBackEnabled = … !isAnyOverflowMenuOpen`),
`screens/songDetails/SongKeyboardShortcuts.kt` (`!isAnyOverflowMenuOpen`),
`presentation/src/desktopMain/.../ui/CampfireDesktopApp.kt` (`handleKeyEvent`),
`presentation/src/wasmJsMain/.../ui/navigation/BrowserRoutes.kt` (`paths`, or `routeInputs` after plan 08);
`ui/platform/LibraryPersistence.kt` (`requestLibraryPersistence`); moves: `screens/settings/SettingsLayout.kt`
(`SettingsTab`) → `ui/navigation/SettingsTab.kt`, `screens/songDetails/FontScaleAccumulator.kt` and
`screens/songDetails/FontScaleGestures.kt` (`PINCH_SENSITIVITY`) → `ui/fontScale/`, `dialogs/SongMetadataDialog.kt`
(`SONG_METADATA_FIELDS`) → next to `DialogType`, `screens/songDetails/SongMetadata.kt` (`ChordProMetadata.hasSongInfo`)
and `screens/importReport/ImportReportSections.kt` (`followingLibraryFileNames`) → `ui/` model files; tests that import
them (`FontScaleAccumulatorTest`, `ImportReportSectionsTest`, `SongMetadataTest`); `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt`
(imports `presentation.ui.screens.settings.SettingsTab`); `presentation/CLAUDE.md`
(the desktop shell paragraph names `isAnyOverflowMenuOpen (components/OverflowMenu.kt)`; the `LibraryPersistence.kt`
paragraph)
**Depends on:** none. Plan 01's `DialogHost` step should come after this one, so the overlay state moves with it.

## Problem

1. **A process-global counter is part of the view model's logic.** `OverflowMenu` counts open menus in a top-level
   `private var openOverflowMenuCount by mutableIntStateOf(0)` and exposes `internal val isAnyOverflowMenuOpen`, which
   the view model reads in four decisions (`openCurrentSearch`, `isSongTextZoomable`, `magnifyByTouchpad`,
   `toggleMetronomeByKey`), the desktop key handler reads, `BrowserRoutes.paths` reads, and two screens read. It is
   global for a real reason — the desktop key handler runs outside the composition — but a global means: any test of
   those decisions shares one counter across tests; the offscreen screenshot tool, which composes the app many times in
   one process, shares it across renders; and nothing in the view model's API says it depends on it.
2. **The view model calls a platform `expect fun` while it is constructed**:
   `internal val libraryPersistence = flow<LibraryPersistence?> { emit(requestLibraryPersistence()) }.asState(null)`,
   so constructing it in a test asks the platform (and on the web, the browser) for storage persistence.
3. **The view model imports screen-level declarations** that are really shared model types: `screens.settings.SettingsTab`
   (navigation state — `NavigationState` and `BrowserRoutes` use it too), `screens.songDetails.FontScaleAccumulator` and
   `PINCH_SENSITIVITY` (font scale logic the view model owns), `dialogs.SONG_METADATA_FIELDS` (the payload of
   `DialogType.SongMetadata`), `screens.songDetails.hasSongInfo` and `screens.importReport.followingLibraryFileNames`
   (pure helpers over model types). The dependency arrow points from state to screens.

## Fix

1. **`OverlayState`** (`ui/components/OverlayState.kt`):
   ```kotlin
   @Stable internal class OverlayState {
       private var openMenuCount by mutableIntStateOf(0)
       val isAnyMenuOpen: Boolean get() = openMenuCount > 0
       fun onMenuOpened() { openMenuCount++ }
       fun onMenuClosed() { openMenuCount-- }
   }
   internal val LocalOverlayState = staticCompositionLocalOf { OverlayState() }
   ```
   The view model owns one (`internal val overlayState = OverlayState()`); `CampfireApp` provides it with
   `CompositionLocalProvider(LocalOverlayState provides viewModel.overlayState)` around everything it composes (menus
   in popups inherit composition locals, so the dropdowns see it). `OverflowMenu` reads
   `val overlayState = LocalOverlayState.current` and keys its effect on it —
   `DisposableEffect(overlayState) { overlayState.onMenuOpened(); onDispose { overlayState.onMenuClosed() } }` — so the
   close always goes to the instance the open went to, even if the provided state changes while a menu is up
   (`app/desktop` holds the view model in a `mutableStateOf` and can hand `CampfireApp` a new one). The view model's four reads, `handleKeyEvent` and
   `BrowserRoutes` read `overlayState.isAnyMenuOpen`; `SetlistsScreen` and `SongKeyboardShortcuts` read
   `LocalOverlayState.current.isAnyMenuOpen`. Delete the global. The default value of the local keeps a composable that
   is used outside `CampfireApp` working. One commit.
2. **The persistence request is passed in, not called.** Do this as part of plan 01's `PreferencesController` step if
   that is landing (the holder is constructed by the view model, so it can take
   `requestPersistence: suspend () -> LibraryPersistence` and the view model passes `::requestLibraryPersistence`).
   If plan 01 is not landing, leave it: the alternative (a Koin-bound `LibraryPersistenceRequester` interface with four
   platform `@Single` implementations, like `DesktopSystemBrowser`) costs more than it saves while the view model has
   no tests.
3. **Move the shared types out of `screens/`** (pure moves, one commit; skip any the package-move pass has already
   relocated, and prefer one of its new packages where one fits — e.g. `ui.songLayout` for `hasSongInfo` if that is
   where the song-info helpers went): `SettingsTab` → `ui/navigation/SettingsTab.kt` (it stays **public**: `Shot.kt` in
   `tools/screenshots` names it — update that import in the same commit); `FontScaleAccumulator` and `PINCH_SENSITIVITY`
   → `ui/fontScale/` (`FontScaleGestures.kt` keeps importing them); `SONG_METADATA_FIELDS` → the file that holds
   `DialogType`; `ChordProMetadata.hasSongInfo` → a model-level file such as `ui/songInfo/SongInfo.kt`;
   `ImportResult.followingLibraryFileNames` → the file that holds `ImportReport`. Update imports and the moved tests'
   packages.

## Tests

Add `OverlayStateTest` (open twice, close once → still open; close again → closed). Existing:
`FontScaleAccumulatorTest`, `ImportReportSectionsTest`, `SongMetadataTest`, `navigation/FeatureDestinationsTest`.

## Manual check

Desktop: open a song card's overflow menu and press Escape (the menu closes, the screen stays); with a menu open press
Ctrl+F (nothing), Ctrl+plus on a song (nothing), Space on the Metronome tab (nothing). Web: open a menu and press the
browser's Back (the menu closes, the address stays). Setlists in reorder mode with a row menu open: Back closes the menu
first.
