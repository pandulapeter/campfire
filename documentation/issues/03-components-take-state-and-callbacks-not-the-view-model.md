# Make the shared components take state and callbacks instead of the concrete `CampfireViewModel`

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Challenged:** amended — `SongFilters` is also composed by the filters sheet in the dialogs host, outside `SongsScreen`,
so its state and actions come from a shared `remember…` helper rather than being built in `SongsScreen`; the panel's
time signature becomes a pure function the callers feed with collected state (a view-model getter reading
`StateFlow.value` would not be observed by composition, and a combined flow would lag a page change by a hop); the
setter count is nine; callers listed as they are after the file splits.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongActions.kt`
(`SongActions`, `SongEditingActions`, `editSongAction`, `SetlistAssignmentsButton`, `setlistAssignmentsAction`),
`components/SetlistActions.kt` (`SetlistActions`), `components/NewItemMenu.kt` (`NewItemMenu`), `components/Controls.kt`
(`SongFilters`, `FilterGroups`), `components/SortMenu.kt` (`SongSortMenu`, `SetlistSortMenu`), `metronome/MetronomePanel.kt`
(`MetronomePanel`); their callers `screens/songs/SongsScreen.kt`, `screens/setlists/SetlistsScreen.kt`,
`screens/songDetails/SongDetailsScreen.kt`, `screens/metronome/MetronomeScreen.kt`, `dialogs/Dialogs.kt`;
`presentation/CLAUDE.md` (only if a paragraph names a component's parameters)
**Depends on:** none (lands before or after 01; the package-move pass has already made the `CampfireViewModel.DialogType`
references read `DialogType`). Callers after the file splits: `SongActions` in `screens/songs/SongList.kt`,
`screens/setlists/SetlistEntryActions.kt` and `SongDetailsScreen`; `SetlistActions` in `SetlistList.kt` and
`PushedSetlistHeader.kt`; `setlistAssignmentsAction` in `SetlistEntryActions.kt` and `SongDetailsScreen`; `SongFilters`
in `SongsScreen` and the filters sheet of the dialogs host (`Dialogs.kt` or the file it was split into); the sort menus
in both list screens and both picker sheets.

## Problem

Components in `ui/components/` and `ui/metronome/` are meant to be reusable building blocks, but six of them take the
whole `CampfireViewModel` and reach into it, so each can only be used, previewed or rendered offscreen with a fully
wired view model, and each silently couples to whatever the view model's API is this week:

```kotlin
internal fun SongActions(modifier: Modifier = Modifier, state: OverflowMenuState = …, viewModel: CampfireViewModel, song: Song, …) {
    …onClick = { viewModel.updateSongFileName(song) }
    …onClick = { viewModel.showDialog(CampfireViewModel.DialogType.Export(song = song, songSetlistFileName = setlistFileName)) }
    …onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(song)) }
```

- `SongActions`, `SongEditingActions`, `editSongAction`, `SetlistAssignmentsButton`, `setlistAssignmentsAction`
  (`SongActions.kt:314-478`): `updateSongFileName`, `showDialog(Export|DeleteSong|SetlistPicker)`, `openEditor`.
- `SetlistActions` (`SetlistActions.kt:53`): `showDialog(EditSetlist|SongPicker|DuplicateSetlist|Export|DeleteSetlist)`,
  `setSetlistArchived`.
- `NewItemMenu` (`NewItemMenu.kt:45`): `importFiles(filePicker)` — while `onCreate` is already a callback.
- `SongFilters` → `FilterGroups` (`Controls.kt:242-322`): collects `userPreferences`, `songFilter`, `tags`, `languages`,
  `isSongFilterActive` and calls ten filter/sorting setters; the inner `TagFilters`/`LanguageFilters` already take state
  and lambdas, so only this one layer is coupled.
- `SongSortMenu`/`SetlistSortMenu` (`SortMenu.kt:39-80`): collect `userPreferences` for one field and call one setter;
  the generic `SortMenu` they wrap already takes `selected` + `onSelected`.
- `MetronomePanel` (`MetronomePanel.kt:69`): collects `metronomePlayback`, `metronomeSettings`, `songsByFileName`,
  `songsBeingRenamed`, reads `metronomeContext` and `metronomeBeats`, calls `updateMetronomeSettings` and
  `toggleMetronome` — i.e. it computes the time signature the click counts, which is metronome state, not panel UI.

## Fix

Keep each component's look and behaviour identical; change only where its data comes from. Each bullet is one commit.

Performance constraint: song rows are recomposed while lists scroll, so a row must not receive freshly allocated
lambdas per item that defeat skipping. Use **one handler object per screen**, remembered once, whose methods take the
song (or setlist), rather than per-row lambdas:

1. `SongActions` family: add
   ```kotlin
   @Stable internal interface SongActionHandler {
       fun edit(song: Song); fun updateFileName(song: Song); fun export(song: Song, setlistFileName: String?)
       fun delete(song: Song); fun chooseSetlists(song: Song, setlistFileName: String?)
   }
   ```
   and a `@Composable fun rememberSongActionHandler(viewModel: CampfireViewModel): SongActionHandler = remember(viewModel) { object : SongActionHandler { … } }`
   next to the screens (e.g. `screens/SongActionHandlers.kt`), whose bodies are exactly today's `onClick` bodies. The
   components take `actions: SongActionHandler` instead of `viewModel`. Callers: `SongsScreen`, `SetlistsScreen`,
   `SongDetailsScreen`.
2. `SetlistActions`: same pattern, `SetlistActionHandler` (`edit`, `chooseSongs`, `duplicate`, `setArchived`, `export`,
   `delete`), remembered in `SetlistsScreen`.
3. `NewItemMenu`: replace `viewModel` with `onImport: (FilePicker) -> Unit`; the component still reads
   `LocalFilePicker` and passes it. Callers pass `viewModel::importFiles` (a function reference is stable).
4. `SongSortMenu`/`SetlistSortMenu`: take `selected: SortingMode?` / `SetlistSortingMode?` and `onSelected`; the callers
   (two screens, two picker sheets in `Dialogs.kt`) already collect `userPreferences` or can. Keep the
   "`if (it != selected) onSortingModeChanged()`" logic inside `SetlistSortMenu`.
5. `SongFilters`: introduce `@Immutable internal data class SongFilterUiState(tags, languages, selectedTags,
   selectedLanguages, tagMatchMode, languageMatchMode, tagSortingMode, languageSortingMode, isActive)` and a
   `SongFilterActions` handler (the nine setters: `toggleTagFilter`, `clearTagFilter`, `setTagMatchMode`,
   `setTagSortingMode`, `toggleLanguageFilter`, `clearLanguageFilter`, `setLanguageMatchMode`, `setLanguageSortingMode`,
   `clearSongFilter`). `SongFilters` has two callers that do not share a composition — the side panel in `SongsScreen`
   and the filters bottom sheet, which the dialogs host composes — so add
   `@Composable internal fun rememberSongFilterUiState(viewModel): SongFilterUiState` and
   `rememberSongFilterActions(viewModel): SongFilterActions` next to the screens and call them at both sites. Build the
   state with `remember` keyed on the five collected values (`userPreferences`, `songFilter`, `tags`, `languages`,
   `isSongFilterActive`), with the same `?: MatchMode.ANY` / `?: LabelSortingMode.BY_USAGE` defaults `FilterGroups`
   applies today, so a recomposition does not hand the filters a new instance when nothing changed
   (`FilterGroupsLayout` measures in one pass and is not cheap).
6. `MetronomePanel`: take `isPlaying: Boolean`, `beatLevels: List<BeatLevel>`, `beats: Flow<…>` (the type
   `metronomeBeats` has), `isFlashEnabled`, `onBeatLevelsChanged: (List<BeatLevel>) -> Unit`, `onPlayStop: () -> Unit`.
   Move the time-signature resolution (the `when (val context = viewModel.metronomeContext)` block) into a pure
   `internal fun metronomeTimeSignatureOf(context: MetronomeContext, settings: MetronomeSettings, songOf: (String) -> Song?): TimeSignature`
   in `ui/metronome/MetronomeContext.kt` (body unchanged; `songOf` is `{ songsByFileName[it] ?: songsBeingRenamed[it] }`).
   Do **not** make it a view-model getter: it would read `songsByFileName.value`, `songsBeingRenamed.value` and
   `metronomeSettings.value`, which composition does not observe, so a `{time}` saved or synced while the panel is up
   would not redraw the bar. Not a combined `StateFlow` either: that would reach the panel a hop after the pager's
   page change, drawing the previous song's bar for a frame. Add
   `@Composable internal fun rememberMetronomePanelState(viewModel)` (next to the panel's callers) that collects
   `metronomePlayback`, `metronomeSettings`, `songsByFileName` and `songsBeingRenamed` exactly as the panel does today,
   reads `viewModel.metronomeContext`, and returns the time signature with the derived `beatLevels`, `isPlaying` and
   `isFlashEnabled`; the panel's two callers (`SongDetailsScreen`, `MetronomeScreen`) call it and pass
   `onBeatLevelsChanged = { levels -> viewModel.updateMetronomeSettings { withBeatLevels(timeSignature, levels) } }` and
   `onPlayStop = viewModel::toggleMetronome`.

Screens keep taking the view model (or, after plan 01, the holders they need); only components stop.

## Tests

`MetronomeContextTest`: `metronomeTimeSignatureOf` takes the stretch's time signature over the song's, the song's
(or the renamed song's) over the default, and the settings' for `Standalone`. Otherwise none new (UI code; the repo
does not test composables). Guard: `:presentation:desktopTest`, and
`./gradlew :tools:screenshots:run` must render the same images as before (it composes every screen offscreen; compare
a few renders by eye or with a pixel diff).

## Manual check

On a phone: open a song card's menu on the Songs and the Setlists screen and use every entry (rename, export, delete,
choose setlists); the setlist header menu's every entry; New → Import on both list screens; change the sort on both
screens and in both picker sheets; filter by tag and language from the side panel (wide window) and from the sheet
(phone); open the metronome panel on a song in 6/8, tap a beat to accent it, start and stop. Fling the Songs list of a
few hundred songs and confirm scrolling is as smooth as before (Android Studio's recomposition counts on a song row
should be unchanged).
