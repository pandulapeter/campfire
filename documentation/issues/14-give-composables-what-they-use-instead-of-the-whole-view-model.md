# Give composables the holders, or narrow state and callbacks, they use instead of the whole `CampfireViewModel`

**Kind:** structure · **Severity:** medium · **Priority:** P2 · **Platforms:** all · **Lane:** P (after 13) · **Effort:** L (10 steps)
**Files:** every file under `presentation/src/*/kotlin/com/pandulapeter/campfire/presentation/ui/` that declares a
`viewModel: CampfireViewModel` parameter (113 files at `76f3a2c9b`, listed by
`grep -rl 'viewModel: CampfireViewModel' presentation/src`), their tests, `tools/screenshots/src/**` call sites,
`.github/scripts/check_layers.py` and `test_check_layers.py` (last step), `.claude/skills/code-style/SKILL.md`
("Composable structure"), `presentation/CLAUDE.md`

**Challenged:** amended — the check of each step no longer leans on the golden renders, which plan 01 deletes; plan
11's step 1 already removes the dead `CampfireViewModel` imports of the moved state files, so step 10 meets only real
uses.

`ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Problem

The code-style skill already says "Shared components take state and lambdas, not the whole `CampfireViewModel` …;
only a screen receives the view model", but nothing enforces it, and 145 `viewModel: CampfireViewModel` parameters in
113 files reach into its holders: `viewModel.dialogHost` 133 times, `preferencesController` 79, `navigator` 71,
`metronomeController` 40, `libraryState` 38, `editorSession` 32. Every one of those composables depends on the whole
app, cannot be previewed or tested without it (there is no `@Preview` in the module), and keeps the package graph tied
in one knot (plan 11).

Screens also decide behaviour in composition, where no unit test reaches it. `screens/songDetails/SongDetailsScreen.kt`
collects 12 flows from 9 holders and closes itself:

```kotlin
LaunchedEffect(resolvedSongs?.isEmpty()) {
    …
    if (!isImportPreview && resolvedSongs?.isEmpty() == true && viewModel.navigator.backStack.lastOrNull() == destination) onBack()
}
…
val isUncovered = visibleDialog == null && viewModel.navigator.backStack.lastOrNull() is CampfireDestination.SongDetails
```

and `screens/settings/SharingSections.kt` looks up the library in the UI:

```kotlin
private fun exportDialogOf(viewModel: CampfireViewModel, share: Share) = when (share.kind) {
    SharedLinkKind.SONG -> viewModel.libraryState.songsByFileName.value.let { songs -> … }
```

## Fix

The rule, as each step applies it:

- A destination's entry composable (`<X>Screen.kt`, `ExportHost.kt`) and the files of the `ui` package keep the view
  model. They collect state and hand down only what is used.
- A composable under a screen takes the holders it uses when it uses several members of one (`dialogHost: DialogHost`),
  otherwise the values and callbacks (`onDismiss: () -> Unit`). A shared component (`ui.components`, `ui.dialogs.sheet`,
  `ui.songView`, …) takes values and callbacks only. Callbacks are remembered or method references, so lists do not
  recompose.
- A decision made in composition from plain data becomes an `internal` function next to its composable with a test
  (code-style "Decisions are pure functions"): `shouldCloseSongDetails(isImportPreview, resolvedSongs, top,
  destination)` and `isSongDetailsUncovered(visibleDialog, top)` beside `SongDetailsScreen`; `exportDialogOf(share,
  songsByFileName, setlists)` in `ui/share/`.
- **No per-screen view models.** The app's state is app-scoped and must be right from the first frame
  (`stateIn(viewModelScope, Eagerly)`, `ui/CLAUDE.md`); a screen-scoped view model would start its flows at first
  collection and animate the real value in.
- Behaviour is preserved: the same state read at the same place, no new `remember`, no changed keys. A composable that
  collected a flow itself keeps collecting it (pass the `StateFlow`, not a value read higher up, where moving the read
  would recompose a larger scope).

### Steps (one commit each, widest first; each leaves every platform compiling and the tests green)

1. Settings: `screens/settings` (14 files) and `ui/settingsPage`; `exportDialogOf` moves to `ui/share/`.
2. Song details: `screens/songDetails` (10), `songView/firstSection` (2), `songInfo` (2); the two decisions above.
3. Editor: `screens/songEditor` (8).
4. Instrument and labels dialogs: `dialogs/instrument` (6), `dialogs/labels` (7).
5. The other dialogs: `dialogs/pickers` (4), `dialogs/lookup` (4), `dialogs/songDisplay` (3), `dialogs/forms` (3),
   `dialogs/about` (2), `dialogs/scales` (1), and the `ui.dialogs` package (`DeleteLibraryDialog`,
   `DeleteSetlistConfirmation`, `DeleteSongConfirmation`, …); `CampfireDialogs.kt` (in the `ui` package after plan 11)
   keeps the view model and passes holders down.
6. Lists: `screens/songs` (4), `screens/setlists` (5), the `ui.screens` handlers (`SongActionHandlers.kt`,
   `SetlistActionHandlers.kt`, `SongFilterHandlers.kt`), `components/labels/LabelsOnEverySong.kt`.
7. Export and import: `screens/export` (3), `screens/importReport` (3).
8. Tools: `screens/tuner`, `screens/scales`, `screens/chords`, `screens/metronome`, `screens/onboarding`.
9. App-layer effects and chrome: `metronome/MetronomeHapticsEffect.kt`, `MetronomeNotificationEffect.kt`,
   `MetronomePanelState.kt`, `navigation/BrowserHistory.kt`, `BrowserRouteInputs.kt`, `messages/Message.kt`,
   `Messages.kt`, `fontScale/FontScaleGestures.kt`, `PinchSensitivity.kt`, `update/AppUpdateGate.kt`,
   `sync/SyncNotifications.kt`, `launch/LaunchScreenGate.kt`, `state/importing/DropAvailability.kt`, `chrome/*`
   (2 files). The platform shells and the `ui` package's effects (`*ShortcutEffect.kt`, `FaviconEffect.kt`, …) keep it.
10. Enable the rule: `VIEW_MODEL_RULE = True` in `.github/scripts/check_layers.py` (plan 11), its tests updated, and the
    whole tree passing it. In the code-style skill's "Composable structure", drop "(older ones that still take it are
    converted when a change touches them)" and name the check; one line in `presentation/CLAUDE.md`.

**Size note (`check_sizes.py`):** the entry composables and dialogs that hand down holders are mostly past 120 lines already (`ExportScreen` 385, `CampfireScreens` 345, `SongList` 343, `LoadedSongEditor` 317, `CampfireDialog` 313, `SetlistList` 310, `SongDetailsScreen` 294, …), so a call site that grows from `viewModel = viewModel` to several arguments must be paid for in the same function (or the commit splits it) — none may end a step longer.

Run the step's grep afterwards (`grep -rn 'viewModel: CampfireViewModel' <the step's folders>`) and expect only entry
files. Plan 11's script, with the view model rule switched on locally, lists what is left after each step.

## Tests

- `SongDetailsDecisionsTest` (`commonTest`, `ui/screens/songDetails/`): `` `a song the library lost closes its screen when it is on top` ``,
  `` `an import preview never closes itself` ``, `` `a screen under another one stays open` ``,
  `` `a song is uncovered only with no dialog over it` ``.
- `ExportDialogOfTest` (`ui/share/`): `` `a shared song opens the export screen on the library's song of that name, case and form folded` ``,
  `` `a share whose file is gone opens nothing` ``.
- Each step: `./gradlew desktopTest :tools:screenshots:test`; the UI tests (and plan 01's fit and contrast assertions,
  if landed) are the check that nothing behaves differently. Plan 01 removes the golden renders, so the verbatim-move
  discipline (same reads at the same places) is what keeps rendering unchanged.
