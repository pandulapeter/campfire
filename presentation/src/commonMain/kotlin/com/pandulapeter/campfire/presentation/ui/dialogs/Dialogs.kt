/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.song_details_remove_cover_art_confirmation
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.ic_calendar
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_choose_songs
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist_confirmation
import com.pandulapeter.campfire.presentation.resources.setlists_countdown
import com.pandulapeter.campfire.presentation.resources.setlists_date
import com.pandulapeter.campfire.presentation.resources.setlists_date_value
import com.pandulapeter.campfire.presentation.resources.setlists_description
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_duplicate_title
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results
import com.pandulapeter.campfire.presentation.resources.setlists_pick_date
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_action
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_library_delete
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_confirmation
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_confirmation_sync
import com.pandulapeter.campfire.presentation.resources.settings_library_delete_prompt
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect
import com.pandulapeter.campfire.presentation.resources.settings_sync_disconnect_confirmation
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_language_no_search_results
import com.pandulapeter.campfire.presentation.resources.song_details_language_search
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_tag_create
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_tags_search
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_artist_and_title
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.welcome_get_started
import com.pandulapeter.campfire.presentation.resources.welcome_message
import com.pandulapeter.campfire.presentation.resources.welcome_open_settings
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint_sync
import com.pandulapeter.campfire.presentation.resources.welcome_title
import com.pandulapeter.campfire.presentation.resources.whats_new_title
import com.pandulapeter.campfire.presentation.resources.whats_new_message
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.PickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.songPickerMatches
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.CountedFilterChip
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.LabelSortingToggle
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.SortableChipRow
import com.pandulapeter.campfire.presentation.ui.components.ChecklistLayout
import com.pandulapeter.campfire.presentation.ui.components.ChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.KeepChecklistRowsInPlace
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.ScrollToStartWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.SongFilters
import com.pandulapeter.campfire.presentation.ui.components.SongSortMenu
import com.pandulapeter.campfire.presentation.ui.components.THEME_COLOR_CHOICE_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.languageName
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.orderedBy
import com.pandulapeter.campfire.presentation.ui.components.pickableLanguages
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDefaults
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongInfoBody
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberSongInfoEditing
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Hosts whichever dialog or bottom sheet the view model asks for.
 */
@Composable
internal fun CampfireDialogs(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val underlyingSongInfo by viewModel.underlyingSongInfo.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    // The import screen shows the progress of the imports it reports on itself.
    ImportProgressDialogHost(
        progress = importProgress.takeIf { importReport == null },
        canShow = visibleDialog == null,
        onCancel = viewModel::cancelImportPreparation,
    )
    // A stable composition slot keeps the parent sheet mounted while its editor opens in another modal window.
    val songInfo = underlyingSongInfo ?: (visibleDialog as? CampfireViewModel.DialogType.SongInfo)
    if (songInfo != null) {
        SongInfoSheet(viewModel = viewModel, dialog = songInfo, urlOpener = urlOpener)
    }
    when (val dialog = visibleDialog) {
        // Drawn by ExportHost, which deals it over the screens rather than in a window of its own.
        is CampfireViewModel.DialogType.Export -> Unit
        CampfireViewModel.DialogType.NewSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = { viewModel.dismissSheet(dialog) },
            // Dismissed before the setlist is created rather than after, since creating it is what opens the song
            // picker for it, and a dismissal arriving after that would close the picker instead.
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.dismissDialog()
                viewModel.createSetlist(title = setlistTitle, description = description, date = date, isCountdownShown = isCountdownShown)
            },
        )

        is CampfireViewModel.DialogType.EditSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_edit_details),
            subtitle = dialog.setlist.title,
            isTitleFocused = false,
            requiresChanges = true,
            initialTitle = dialog.setlist.title,
            initialDescription = dialog.setlist.description,
            initialDate = dialog.setlist.date,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.save),
            onDismiss = { viewModel.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.editSetlist(
                    offered = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        // Named before it is made rather than after: two setlists can carry the same title (a setlist is identified
        // by its file name), so a copy nobody named would sit under the original's title until somebody noticed.
        is CampfireViewModel.DialogType.DuplicateSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_duplicate_setlist),
            subtitle = dialog.setlist.title,
            initialTitle = textResource(Res.string.setlists_duplicate_title, dialog.setlist.title),
            initialDescription = dialog.setlist.description,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.setlists_duplicate),
            onDismiss = { viewModel.dismissSheet(dialog) },
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.duplicateSetlist(
                    setlist = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
            },
        )

        CampfireViewModel.DialogType.NewSong -> NewSongDialog(
            onDismiss = { viewModel.dismissSheet(dialog) },
            onCreate = { values -> viewModel.createSong(values = values) },
        )

        CampfireViewModel.DialogType.SongFilters -> CampfireBottomSheet(
            title = stringResource(Res.string.songs_filter),
            onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SongFilters) },
        ) { contentPadding ->
            SongFilters(
                viewModel = viewModel,
                contentPadding = contentPadding,
                uncoveredTopInset = uncoveredTopInset,
                fadeBackgroundColor = sheetContainerColor(),
            )
        }

        is CampfireViewModel.DialogType.SongInfo -> Unit
        is CampfireViewModel.DialogType.ChordShapes -> ChordShapesSheet(viewModel = viewModel, dialog = dialog)

        is CampfireViewModel.DialogType.SetlistPicker -> SetlistPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongPicker -> SongPicker(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.DeleteSong -> ConfirmationDialog(
            title = stringResource(Res.string.songs_delete_song),
            text = textResource(Res.string.songs_delete_song_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSong(dialog.song.fileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.SongTags -> SongTagsDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongMetadata -> SongMetadataDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongLinks -> SongLinksDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongLanguages -> SongLanguagesDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.SongPlaying -> SongPlayingDialog(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.CoverArtSearch -> CoverArtSearchSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.RemoveSongCoverArt -> ConfirmationDialog(
            title = stringResource(Res.string.cover_art_search_remove),
            text = textResource(Res.string.song_details_remove_cover_art_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.cover_art_search_remove),
            // Not removing the cover is not being done with it: the sheet the bin was tapped in comes back.
            onDismiss = { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song = dialog.song, isEditorDraft = dialog.isEditorDraft)) },
            onConfirm = {
                viewModel.setSongCoverArt(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, url = null)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.RemoveSongFromSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_remove_song),
            text = textResource(Res.string.setlists_remove_song_confirmation, dialog.songTitle),
            confirmLabel = stringResource(Res.string.setlists_remove_song),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.removeSongFromSetlist(songFileName = dialog.songFileName, setlistFileName = dialog.setlistFileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.DeleteSetlist -> ConfirmationDialog(
            title = stringResource(Res.string.setlists_delete_setlist),
            text = textResource(Res.string.setlists_delete_setlist_confirmation, dialog.setlist.title),
            confirmLabel = stringResource(Res.string.delete),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.deleteSetlist(dialog.setlist.fileName)
                viewModel.dismissDialog()
            },
        )

        is CampfireViewModel.DialogType.DisconnectSync -> ConfirmationDialog(
            title = stringResource(Res.string.settings_sync_disconnect),
            text = textResource(Res.string.settings_sync_disconnect_confirmation, dialog.accountName),
            confirmLabel = stringResource(Res.string.settings_sync_disconnect),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.disconnectSyncProvider()
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.ClearCoverArtCache -> ConfirmationDialog(
            title = stringResource(Res.string.settings_library_cover_art_cache_clear),
            text = stringResource(Res.string.settings_library_cover_art_cache_clear_confirmation),
            confirmLabel = stringResource(Res.string.settings_library_cover_art_cache_clear_action),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.clearCoverArtCache()
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.DeleteLibrary -> DeleteLibraryDialog(
            viewModel = viewModel,
        )

        CampfireViewModel.DialogType.RevertChanges -> ConfirmationDialog(
            title = stringResource(Res.string.song_editor_revert),
            text = stringResource(Res.string.song_editor_revert_confirmation),
            confirmLabel = stringResource(Res.string.song_editor_revert),
            onDismiss = viewModel::dismissDialog,
            onConfirm = viewModel::revertEditorChanges,
        )

        is CampfireViewModel.DialogType.ConfirmImportReplace -> ConfirmationDialog(
            title = pluralStringResource(Res.plurals.import_replace_title, dialog.count, dialog.count),
            text = stringResource(Res.string.import_conflicts_replace_description),
            confirmLabel = stringResource(Res.string.import_conflicts_replace),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.dismissDialog()
                viewModel.resolveImport(ImportConflictResolution.REPLACE)
            },
        )

        CampfireViewModel.DialogType.UnsavedChanges -> UnsavedChangesDialog(
            isSaving = viewModel.isSavingSong.collectAsStateWithLifecycle().value,
            onCancel = viewModel::dismissDialog,
            onDiscard = viewModel::leaveEditorWithoutSaving,
            onSave = viewModel::saveEditorChangesAndLeave,
        )

        CampfireViewModel.DialogType.Welcome -> WelcomeDialog(
            viewModel = viewModel,
        )

        CampfireViewModel.DialogType.WhatsNew -> {
            // Only once it has stayed up for a moment: Play's answer can arrive after the app is on screen and cover the
            // dialog with the update required screen, which takes it out of the composition and cancels this, and whose
            // flow ends the process (see CampfireViewModel.showWhatsNewOnVersionChange).
            LaunchedEffect(Unit) {
                delay(WHATS_NEW_SEEN_DELAY)
                viewModel.onWhatsNewShown()
            }
            WhatsNewDialog(onDismiss = viewModel::dismissDialog)
        }

        null -> Unit
    }
}

/**
 * Each change gets its own row so wrapped lines stay aligned with the words rather than the bullet, a bold headline
 * with its description under it (`• **Headline** Description` in the resource; a row without the bold part is all
 * description). Only the list scrolls: the title and the way back to the songbook stay visible, even with a long
 * release or larger interface text.
 */
@Composable
private fun WhatsNewDialog(
    onDismiss: () -> Unit,
) = AlertDialog(
    modifier = Modifier.widthIn(max = 480.dp),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.whats_new_title, CAMPFIRE_VERSION_NAME)) },
    text = {
        val scrollState = rememberScrollState()
        val message = stringResource(Res.string.whats_new_message)
        val changes = remember(message) {
            message.lineSequence().map { it.trim().removePrefix("•").trim() }.filter { it.isNotBlank() }.map { change ->
                val headline = WHATS_NEW_HEADLINE.matchEntire(change)
                if (headline == null) null to change else headline.groupValues[1] to headline.groupValues[2]
            }.toList()
        }
        val bulletColor = MaterialTheme.colorScheme.primary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .fadingVerticalEdges(scrollState)
                .bounceVerticalScroll(scrollState)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            changes.forEach { (headline, description) ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Canvas(modifier = Modifier.padding(top = 10.dp).size(5.dp)) {
                        drawCircle(color = bulletColor)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (headline != null) {
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (description.isNotEmpty()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    },
    confirmButton = {
        Button(onClick = onDismiss) { Text(stringResource(Res.string.welcome_get_started)) }
    },
)

/** How long What's new has to stay uncovered before it counts as seen. */
private val WHATS_NEW_SEEN_DELAY = 3.seconds

private val WHATS_NEW_HEADLINE = Regex("""^\*\*(.+?)\*\*\s*(.*)$""")

/**
 * The first run's one screen of its own: a line about what the app is, the two choices that decide how all of it
 * looks, and where everything else is. It is kept to exactly that on purpose - the library behind it already holds
 * the demo songs, which say more about the app than a tour could, and a first run that opened on a series of pages
 * would stand between somebody and the songbook they came for. The colors are the settings screen's own controls,
 * and the dialog is drawn over the app in the app's theme, so every tap on them shows its answer on the songs behind.
 *
 * Sync is the one setting it names, and only where this build has any: it is the only thing in the app a new user
 * cannot find by using it, since it stays off and silent until somebody goes to Settings to connect it.
 */
@Composable
private fun WelcomeDialog(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        var isSmallScreen by remember { mutableStateOf(maxWidth < 500.dp || maxHeight < 500.dp) }
        LaunchedEffect(maxWidth, maxHeight) {
            isSmallScreen = maxWidth < 500.dp || maxHeight < 500.dp
        }
        if (isSmallScreen) {
            CampfireBottomSheet(
                title = stringResource(Res.string.welcome_title),
                // Every color in one row, which is as wide as the sheet is ever worth being: the other rows are a line
                // of text and a choice of three.
                sheetMaxWidth = THEME_COLOR_CHOICE_WIDTH,
                onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.Welcome) },
            ) { contentPadding ->
                WelcomeContent(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onGetStarted = { close() },
                    onOpenSettings = {
                        viewModel.openSettingsFromWelcome()
                        close()
                    },
                )
            }
        } else {
            AlertDialog(
                modifier = Modifier.widthIn(max = 366.dp),
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(Res.string.welcome_title)) },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState),
                    ) {
                        Text(
                            text = stringResource(Res.string.welcome_message),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSubsection(
                            shouldApplyPadding = false,
                        ) {
                            UiModeChoice(
                                shouldApplyPadding = false,
                                selected = userPreferences?.uiMode,
                                onSelected = viewModel::setUiMode,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSubsection(
                            shouldApplyPadding = false,
                        ) {
                            ThemeColorChoice(
                                shouldApplyPadding = false,
                                uiMode = userPreferences?.uiMode,
                                selected = userPreferences?.themeColor,
                                onSelected = viewModel::setThemeColor,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(if (viewModel.syncProviders.isEmpty()) Res.string.welcome_settings_hint else Res.string.welcome_settings_hint_sync),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                    ) { Text(stringResource(Res.string.welcome_get_started)) }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            viewModel.openSettingsFromWelcome()
                            viewModel.dismissDialog()
                        },
                    ) { Text(stringResource(Res.string.welcome_open_settings)) }
                },
            )
        }
    }
}

/**
 * The first run's one screen of its own: a line about what the app is, the two choices that decide how all of it
 * looks, and where everything else is. It is kept to exactly that on purpose - the library behind it already holds
 * the demo songs, which say more about the app than a tour could, and a first run that opened on a series of pages
 * would stand between somebody and the songbook they came for. The colors are the settings screen's own controls,
 * and the sheet is drawn over the app in the app's theme, so every tap on them shows its answer on the songs behind.
 *
 * Sync is the one setting it names, and only where this build has any: it is the only thing in the app a new user
 * cannot find by using it, since it stays off and silent until somebody goes to Settings to connect it.
 */
@Composable
private fun ColumnScope.WelcomeContent(
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    onGetStarted: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.weight(1f, fill = false).fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
    ) {
        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        SettingsSubsection(
            shouldApplyPadding = false,
        ) {
            UiModeChoice(
                selected = userPreferences?.uiMode,
                onSelected = viewModel::setUiMode,
            )
        }
        Spacer(
            modifier = Modifier.height(8.dp),
        )
        SettingsSubsection(
            shouldApplyPadding = false,
        ) {
            ThemeColorChoice(
                uiMode = userPreferences?.uiMode,
                selected = userPreferences?.themeColor,
                onSelected = viewModel::setThemeColor,
            )
        }
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(if (viewModel.syncProviders.isEmpty()) Res.string.welcome_settings_hint else Res.string.welcome_settings_hint_sync),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(Res.string.welcome_open_settings)) }
            Button(onClick = onGetStarted) { Text(stringResource(Res.string.welcome_get_started)) }
        }
    }
}

/**
 * Asked when the editor is left with text in it that has not been written yet. Three answers rather than the usual
 * two: the editor only ever writes when it is told to, so throwing what was typed away has to be asked for just as
 * explicitly as keeping it, and staying in the editor has to be possible without picking either. While the text is
 * being written only Cancel is left, which still means staying: the write is not something to be pressed twice, and
 * Discard would be answering a question the write is already answering.
 */
@Composable
private fun UnsavedChangesDialog(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    // A dialog is a window of its own, which holds no focus until one of its buttons is clicked: without a target
    // taken as it opens (requested from the title, inside the dialog), Ctrl / Cmd + S, the same key the editor saves
    // with, would go unheard.
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        modifier = Modifier
            .saveShortcut { if (!isSaving) onSave() }
            .focusRequester(focusRequester)
            .focusTarget(),
        onDismissRequest = onCancel,
        title = {
            // Here rather than next to the requester: on Android the dialog's content is composed a frame after the
            // composition that shows it, and a request made from there finds no target yet.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                focusRequester.requestFocus()
            }
            Text(stringResource(Res.string.song_editor_unsaved_changes))
        },
        text = { Text(stringResource(Res.string.song_editor_unsaved_changes_confirmation)) },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onSave,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
                TextButton(
                    enabled = !isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = onDiscard,
                ) { Text(stringResource(Res.string.song_editor_discard)) }
            }
        },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
        TextButton(
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = onConfirm,
        ) { Text(confirmLabel) }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
    },
)

/**
 * The one confirmation that is typed rather than tapped: every song and setlist on the device goes with it, and a
 * button in the place where every other dialog's confirmation sits is one a hand reaches out of habit. The word is
 * [DELETE_LIBRARY_CONFIRMATION] in every language, so that it is the same word whatever the app is set to, and what is
 * typed is put in capitals as it is typed, so that the only thing left to get right is the word itself.
 *
 * The sync sentence is there only while an account is connected, and in the error color: the deletion starts a run
 * that carries it to the cloud folder without asking again (`DeleteLibraryUseCase`), so every other device loses the
 * library too, and that is the part of the dialog somebody who means "this phone" must not skim past.
 */
@Composable
private fun DeleteLibraryDialog(
    viewModel: CampfireViewModel,
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val isConfirmed = value.text.trim() == DELETE_LIBRARY_CONFIRMATION
    // Files dropped or opened with the app while the sheet is up are imported against the library it would delete, so
    // Delete waits, greyed, until that import and its question are over.
    val canDelete = isConfirmed && !isImporting && importReport !is CampfireViewModel.ImportReport.Review
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val deleteLibrary = { close: () -> Unit ->
        confirmOnce {
            viewModel.deleteLibrary()
            close()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(CampfireViewModel.DialogType.DeleteLibrary) },
        title = stringResource(Res.string.settings_library_delete),
        text = { contentPadding ->
            val closeSheet = { close() }
            val isClosing = LocalIsSheetClosing.current
            Column(
                modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.settings_library_delete_confirmation))
                if (syncState is SyncState.Connected) {
                    Text(
                        text = stringResource(Res.string.settings_library_delete_confirmation_sync),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = value,
                    onValueChange = { typed ->
                        // A letter whose capital is longer than itself (ß) makes the text longer than the selection
                        // was measured against.
                        val text = typed.text.asSingleLine().uppercase().take(MAX_DELETE_LIBRARY_CONFIRMATION_LENGTH)
                        value = typed.copy(
                            text = text,
                            selection = TextRange(typed.selection.start.coerceAtMost(text.length), typed.selection.end.coerceAtMost(text.length)),
                        )
                    },
                    label = { Text(stringResource(Res.string.settings_library_delete_prompt, DELETE_LIBRARY_CONFIRMATION)) },
                    trailingIcon = rememberClearTextButton(isVisible = value.text.isNotEmpty(), onClear = { value = TextFieldValue() }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    // The close button leaves the keyboard up, so a Done during the slide that follows it is dropped.
                    keyboardActions = KeyboardActions(
                        onDone = {
                            when {
                                isClosing() -> Unit
                                canDelete -> deleteLibrary(closeSheet)
                                else -> keyboardController?.hide()
                            }
                        },
                    ),
                )
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = canDelete,
                isSaveShortcut = false,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = { deleteLibrary(close) },
            ) { Text(stringResource(Res.string.delete)) }
        },
    )
}

/**
 * The [FocusRequester] of the field a dialog opens onto. A dialog that is there to be typed into puts the caret in
 * its first field rather than asking for one more tap, which on a touch platform is also what brings the keyboard
 * up with it - and every such dialog here has one field that obviously comes first (its first field, or its only one,
 * under whatever the dialog has to say before it), so there is only ever the one field to open on. The forms that are opened to be looked over as much as to be typed into are the exception,
 * opening on none of their fields: the song metadata form, and a setlist's details being edited. Both assignment
 * sheets leave their search fields unfocused.
 *
 * @param isFocused Whether the field is given the caret as the dialog opens, for a dialog that does so only some of the
 *   ways it is opened.
 */
@Composable
internal fun rememberFirstFieldFocusRequester(isFocused: Boolean = true): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isFocused) focusRequester.requestFocus() }
    return focusRequester
}

/**
 * Lets a dialog's confirmation through the first time and never again. A dialog that confirms does not leave with
 * the tap but with the next frame, and until then its button and the keyboard's Done key are both still live: on a
 * frame that comes late a second tap lands on a dialog that has already been answered, and creates the song or
 * the setlist a second time. The state is written as the event is handled, so the second event of the same frame
 * already reads it. That is the keyboard's Done and the button landing on the same frame; a Done that comes after the
 * sheet has started closing is dropped by the form itself, through [LocalIsSheetClosing]. The sheet's close after a
 * confirmation cannot be taken back ([CampfireBottomSheet]), so the one confirmation let through is never left on a
 * sheet that stays up.
 */
@Composable
private fun rememberSingleConfirmation(): (confirm: () -> Unit) -> Unit {
    var hasConfirmed by remember { mutableStateOf(false) }
    return { confirm ->
        if (!hasConfirmed) {
            hasConfirmed = true
            confirm()
        }
    }
}

/**
 * Everything the user gets to say about a setlist: its title, the description that goes under its header on the
 * setlists screen, and the day it is for, with whether its header counts down to that day. Creating one, editing one and
 * naming a copy of one are the same dialog with different labels, since all three are answering the same questions.
 *
 * Only the title is required. The description is what somebody writes for their own sake ("acoustic, two sets, no
 * encore"), and most setlists never get one - but the setlists screen's search reads it, so a setlist that is hard
 * to name can still be found by what it is for. The date starts as today unless the setlist already has one - a
 * copy is made for another evening, so it starts as today too - and a setlist written before there were dates gets
 * today's the first time it is edited, since there is no creation date left to fall back on.
 *
 * @param subtitle The setlist being edited or copied, named under the title. A new setlist has none to name.
 * @param isTitleFocused Whether the dialog opens with the caret in the title. A new setlist and a copy are opened to be
 *   named, while an edit is as often opened to look the setlist's details over or to change its date, which a keyboard
 *   coming up over the dialog would only be in the way of.
 * @param requiresChanges Whether the confirm button waits for something to be changed. Only an edit has nothing to
 *   save as it opens; a copy and a setlist named after a search are opened on a title that is meant to be accepted as
 *   it is.
 */
@Composable
private fun SetlistDetailsDialog(
    title: String,
    subtitle: String = "",
    isTitleFocused: Boolean = true,
    requiresChanges: Boolean = false,
    initialTitle: String = "",
    initialDescription: String = "",
    initialDate: LocalDate? = null,
    initialIsCountdownShown: Boolean = false,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, date: LocalDate, isCountdownShown: Boolean) -> Unit,
) {
    // A TextFieldValue rather than a String, for the selection: a dialog that opens on a title the user is meant to
    // replace ("Summer set (copy)", a search that found nothing) has all of it selected, so the first key typed writes the
    // new name instead of appending to the old one. Nothing is lost by it either, since a tap or an arrow key puts the
    // caret where it was aimed. The description is opened on for editing rather than for replacing, so its caret goes
    // to the end of what is already written instead.
    var setlistTitle by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialTitle, selection = TextRange(initialTitle.length)))
    }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    // Saved as its ISO text, since a LocalDate is nothing the saved instance state of every platform can hold.
    val startingDateText = rememberSaveable { (initialDate ?: today()).toString() }
    var dateText by rememberSaveable { mutableStateOf(startingDateText) }
    val date = LocalDate.parse(dateText)
    var isCountdownShown by rememberSaveable { mutableStateOf(initialIsCountdownShown) }
    val isValid = setlistTitle.text.isNotBlank()
    val hasChanges = !requiresChanges || setlistTitle.text.trim() != initialTitle.trim() ||
        description.trim() != initialDescription.trim() || dateText != startingDateText ||
        isCountdownShown != initialIsCountdownShown
    var hasTitleBeenFocused by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusRequester = rememberFirstFieldFocusRequester(isFocused = isTitleFocused)
    val confirmOnce = rememberSingleConfirmation()
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = subtitle,
        text = { contentPadding ->
            Column(modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                        if (it.isFocused && !hasTitleBeenFocused) {
                            hasTitleBeenFocused = true
                            setlistTitle = setlistTitle.copy(selection = TextRange(0, setlistTitle.text.length))
                        }
                    },
                    value = setlistTitle,
                    onValueChange = { newValue ->
                        val text = newValue.text.replace("\n", "").take(MAX_TITLE_LENGTH)
                        // Rebuilt only where the text had to be cut, or every keystroke would throw away the
                        // selection the field is reporting - which is the caret itself, and the run of text a drag
                        // is picking out.
                        setlistTitle = if (text == newValue.text) {
                            newValue
                        } else {
                            TextFieldValue(text = text, selection = TextRange(newValue.selection.end.coerceAtMost(text.length)))
                        }
                    },
                    label = { Text(stringResource(Res.string.setlists_new_setlist_title)) },
                    trailingIcon = rememberClearTextButton(isVisible = setlistTitle.text.isNotEmpty(), onClear = { setlistTitle = TextFieldValue() }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Several lines rather than one, and no Done action on the keyboard: this is a sentence somebody
                // writes about a setlist, and the return key belongs to it rather than to the dialog.
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(Res.string.setlists_description)) },
                    trailingIcon = rememberClearTextButton(isVisible = description.isNotEmpty(), onClear = { description = "" }),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = DESCRIPTION_LINES,
                    maxLines = DESCRIPTION_LINES,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SetlistDateRow(
                    date = date,
                    onDateChange = { dateText = it.toString() },
                    isCountdownShown = isCountdownShown,
                    onCountdownShownChange = { isCountdownShown = it },
                )
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = isValid && hasChanges,
                onClick = {
                    confirmOnce {
                        onConfirm(setlistTitle.text, description, date, isCountdownShown)
                        close()
                    }
                },
            ) { Text(confirmLabel) }
        },
    )
}

/**
 * The date field with the switch for its countdown next to it, which moves under the field where the two do not fit
 * side by side - on a phone the dialog is narrower than a date and a label, and a date cut short is no date.
 */
@Composable
private fun SetlistDateRow(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    isCountdownShown: Boolean,
    onCountdownShownChange: (Boolean) -> Unit,
) {
    // Above the two layouts rather than in the field, so that the calendar stays open, on the day picked in it, when
    // the sheet crosses the width between them - a rotation, a window resized.
    var isPickerVisible by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints {
        if (maxWidth >= MIN_DATE_ROW_WIDTH) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetlistDateField(
                    modifier = Modifier.weight(1f),
                    date = date,
                    onPickerRequested = { isPickerVisible = true },
                )
                Spacer(modifier = Modifier.width(8.dp))
                // An outlined field keeps room above its border for the label to sit in, and it is the border the box
                // is meant to be centered against, not the field with that room.
                SetlistCountdownCheckbox(
                    modifier = Modifier.padding(top = OUTLINED_FIELD_LABEL_ROOM),
                    isChecked = isCountdownShown,
                    onCheckedChange = onCountdownShownChange,
                )
            }
        } else {
            Column {
                SetlistDateField(
                    modifier = Modifier.fillMaxWidth(),
                    date = date,
                    onPickerRequested = { isPickerVisible = true },
                )
                Spacer(modifier = Modifier.height(4.dp))
                SetlistCountdownCheckbox(
                    isChecked = isCountdownShown,
                    onCheckedChange = onCountdownShownChange,
                )
            }
        }
    }
    if (isPickerVisible) {
        SetlistDatePickerSheet(
            date = date,
            onDateChange = onDateChange,
            onDismiss = { isPickerVisible = false },
        )
    }
}

/** The label toggles the box too, so the whole row is the one control a screen reader lands on. */
@Composable
private fun SetlistCountdownCheckbox(
    modifier: Modifier = Modifier,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = Row(
    modifier = modifier
        .clip(MaterialTheme.shapes.small)
        .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(end = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(checked = isChecked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    Text(text = stringResource(Res.string.setlists_countdown), style = MaterialTheme.typography.bodyLarge)
}

/**
 * The day a setlist is for, as a field that is never typed into: the calendar it opens is the one way to change it,
 * so there is no text that could fail to be a date. The whole field opens it on a touch, and its icon is the button
 * the keyboard reaches, since a read-only field does nothing with Enter.
 */
@Composable
private fun SetlistDateField(
    modifier: Modifier = Modifier,
    date: LocalDate,
    onPickerRequested: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPickerRequested by rememberUpdatedState(onPickerRequested)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnPickerRequested() }
    }
    OutlinedTextField(
        modifier = modifier,
        value = stringResource(
            Res.string.setlists_date_value,
            date.year.toString(),
            date.month.number.toString().padStart(length = 2, padChar = '0'),
            date.day.toString().padStart(length = 2, padChar = '0'),
        ),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(Res.string.setlists_date)) },
        trailingIcon = {
            IconButton(onClick = onPickerRequested) {
                Icon(painter = painterResource(Res.drawable.ic_calendar), contentDescription = stringResource(Res.string.setlists_pick_date))
            }
        },
        interactionSource = interactionSource,
    )
}

/**
 * The calendar the date field opens, in a sheet of its own like every other modal with a value to choose, whose Save
 * hands the picked day to [onDateChange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetlistDatePickerSheet(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker counts in milliseconds of UTC midnights, whatever the device's time zone, so the day goes in and
    // comes out through UTC rather than through the local zone, which would move it by a day on one side of it.
    var selectedMillis by rememberSaveable { mutableStateOf(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()) }
    // The day the calendar was opened on: the form's date changes as Save is tapped, and the button sliding away with
    // the sheet must not turn grey in its last frames.
    val openedMillis = rememberSaveable { date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
    // The calendar is given the app's language rather than the system's, which is what rememberDatePickerState
    // would take. The state built here is not saveable, so the day picked but not yet confirmed is carried
    // through a rotation by selectedMillis instead.
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val state = remember(locale) { DatePickerState(locale = locale, initialSelectedDateMillis = selectedMillis) }
    LaunchedEffect(state) { snapshotFlow { state.selectedDateMillis }.collect { it?.let { millis -> selectedMillis = millis } } }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    CampfireBottomSheet(
        title = stringResource(Res.string.setlists_pick_date),
        onDismiss = onDismiss,
        actions = { close ->
            BottomSheetConfirmButton(
                enabled = state.selectedDateMillis != null && state.selectedDateMillis != openedMillis,
                onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    close()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
    ) { contentPadding ->
        val calendarScrollState = rememberScrollState()
        DatePicker(
            modifier = Modifier
                .weight(1f, fill = false)
                .fadingTopEdge(calendarScrollState, sheetContainerColor())
                .bounceVerticalScroll(calendarScrollState)
                .padding(contentPadding.only(bottom = true)),
            state = state,
            dateFormatter = dateFormatter,
            colors = DatePickerDefaults.colors(containerColor = sheetContainerColor()),
            title = null,
            // Typed entry is left out: its field's label, pattern and errors are Material's own strings, read in the
            // system's language rather than the app's, and there is no parameter for any of them.
            showModeToggle = false,
            // Material's own headline formats the day in the system's locale whatever the state's is, so it is
            // drawn here in the calendar's, with the paddings and the color Material gives it.
            headline = {
                val pickDate = stringResource(Res.string.setlists_pick_date)
                val description = dateFormatter.formatDate(state.selectedDateMillis, locale, forContentDescription = true) ?: pickDate
                Text(
                    modifier = Modifier
                        .padding(PaddingValues(start = 24.dp, end = 12.dp, bottom = 12.dp))
                        .semantics { contentDescription = description },
                    text = dateFormatter.formatDate(state.selectedDateMillis, locale, forContentDescription = false) ?: pickDate,
                    color = DatePickerDefaults.colors().headlineContentColor,
                    maxLines = 1,
                )
            },
        )
    }
}

private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** A new song's metadata. Only its title is required; the optional values are written into its initial file. */
@Composable
private fun NewSongDialog(
    onDismiss: () -> Unit,
    onCreate: (Map<Field, String>) -> Unit,
) {
    var values by rememberSaveable(stateSaver = songMetadataSaver) { mutableStateOf(emptyMap<Field, String>()) }
    val isValid = values[Field.TITLE].orEmpty().isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val confirmOnce = rememberSingleConfirmation()
    val create = { close: () -> Unit ->
        if (isValid) {
            confirmOnce {
                onCreate(values.fromSongMetadataDraft())
                close()
            }
        } else {
            keyboardController?.hide()
        }
    }
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.songs_new_song),
        text = { contentPadding ->
            val closeSheet = { close() }
            val isClosing = LocalIsSheetClosing.current
            val field: @Composable (Modifier, Field) -> Unit = { modifier, field ->
                SongMetadataField(
                    modifier = modifier,
                    field = field,
                    value = values[field].orEmpty(),
                    onValueChange = { values = values + (field to it) },
                    isOptional = field != Field.TITLE,
                    maxLength = if (field == Field.TITLE || field == Field.ARTIST) MAX_TITLE_LENGTH else Int.MAX_VALUE,
                    // The close button leaves the keyboard up, so a Done during the slide that follows it is dropped.
                    onDone = { if (!isClosing()) create(closeSheet) },
                )
            }
            Column(
                modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field(Modifier.fillMaxWidth().focusRequester(focusRequester), Field.TITLE)
                field(Modifier.fillMaxWidth(), Field.SUBTITLE)
                field(Modifier.fillMaxWidth(), Field.ARTIST)
                field(Modifier.fillMaxWidth(), Field.ALBUM)
                field(Modifier.fillMaxWidth(), Field.COMPOSER)
                field(Modifier.fillMaxWidth(), Field.LYRICIST)
                SongMetadataShortFields(field)
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(enabled = isValid, onClick = { create(close) }) { Text(stringResource(Res.string.create)) }
        },
    )
}

/**
 * Every tag of a song, managed in one place: the library's tags as a checklist with the song's own ticked and at the
 * top, and a field that narrows the list and creates a tag the library does not have yet. As with the languages
 * ([SongLanguagesDialog]), the whole set is written when the dialog is confirmed, so a file the user owns is rewritten
 * once rather than once per checkbox. The checked tags lead the list as a selected group ([ChecklistOrder]), which a
 * tag checked later joins as a copy while the row itself stays where it was tapped.
 *
 * Offering the library's tags before anything is typed is the point of the list, because a library where the same idea
 * is filed under "christmas", "Christmas" and "xmas" is a library whose tags filter nothing. For the same reason what is
 * typed ticks the tag it spells, whatever its case, and a tag is only created where there is none to tick.
 *
 * It names the song it tags under its title ([SheetHeader]) wherever it was opened from: a tag put on the row next
 * to the one that was meant is a file quietly rewritten, and saying it over the song details screen as well keeps the
 * dialog reading the same from both places.
 */
@Composable
private fun SongTagsDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongTags,
) {
    val libraryTags by viewModel.tags.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val sortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE
    var query by rememberSaveable { mutableStateOf("") }
    // Saved, since the dialog outlives a recreated Activity and Save writes whatever is ticked at that moment.
    var selectedTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(dialog.song.tags) }
    var createdTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    // Keep the library's sorting order, using the song's spelling where the same tag differs only by case.
    val offeredTags = remember(dialog.song, createdTags, libraryTags, sortingMode) {
        val ownTags = dialog.song.tags + createdTags
        val tags = (libraryTags.orderedBy(sortingMode).map { tag ->
            ownTags.firstOrNull { it.equals(tag.name, ignoreCase = true) } ?: tag.name
        } + ownTags).distinctBy { it.lowercase() }
        if (sortingMode == UserPreferences.LabelSortingMode.ALPHABETICAL) tags.sortedAlphabeticallyBy { it } else tags
    }
    val searchableTags = remember(offeredTags) { offeredTags.map { it to viewModel.normalizeForSearch(it) } }
    val matches = remember(searchableTags, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        searchableTags.mapNotNull { (tag, name) -> tag.takeIf { normalizedQuery in name } }
    }
    val checkedTagKeys = selectedTags.toSet()
    val tagOrder = rememberChecklistOrder(checkedTagKeys, sortingMode to query)
    val tagLayout = remember(matches, tagOrder) { tagOrder.layout(matches) { it } }
    val typedTag = query.trim()
    val spelledTag = offeredTags.firstOrNull { it.equals(typedTag, ignoreCase = true) }
    // Include a tag still in the field, since Save commits it too.
    val tagsToSave = when {
        typedTag.isEmpty() -> selectedTags
        spelledTag != null -> if (spelledTag in selectedTags) selectedTags else selectedTags + spelledTag
        else -> selectedTags + typedTag
    }
    val hasTagChanges = tagsToSave.map { it.lowercase() }.toSet() != dialog.song.tags.map { it.lowercase() }.toSet()
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    // The keyboard is put away two frames after Done rather than straight from it: the web build focuses its text input
    // again whenever the field's text changes, which entering a tag does, once at once and once more on the next frame,
    // and either would bring the keyboard back up the moment it had gone.
    var keyboardHideRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(keyboardHideRequests) {
        if (keyboardHideRequests > 0) {
            repeat(2) { withFrameNanos { } }
            keyboardController?.hide()
        }
    }
    val enterTypedTag = {
        when {
            typedTag.isEmpty() -> Unit
            spelledTag != null -> if (spelledTag !in selectedTags) selectedTags = selectedTags + spelledTag
            else -> {
                createdTags = createdTags + typedTag
                selectedTags = selectedTags + typedTag
            }
        }
        query = ""
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_tags_manage),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        startButton = {
            LabelSortingToggle(
                sortingMode = sortingMode,
                onSortingModeSelected = viewModel::setTagSortingMode,
            )
        },
        text = { contentPadding ->
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.asSingleLine().take(MAX_TAG_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_tags_search)) },
                    trailingIcon = rememberClearTextButton(isVisible = query.isNotEmpty(), onClear = { query = "" }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // Done enters what is typed and puts the keyboard away, so that the list it narrowed is in view to
                    // be ticked; the next tag is a tap on the field away.
                    keyboardActions = KeyboardActions(
                        onDone = {
                            enterTypedTag()
                            keyboardHideRequests++
                        },
                    ),
                )
                val isCreatable = typedTag.isNotEmpty() && spelledTag == null
                if (isCreatable || matches.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    HideKeyboardWhenScrolledDown(listState)
                    ScrollToStartWhenChanged(
                        listState = listState,
                        key = sortingMode to query,
                        contents = matches,
                    )
                    KeepChecklistRowsInPlace(listState, tagLayout)
                    LazyColumn(
                        modifier = Modifier.bounceScrollableContent(listState)
                            .padding(top = 8.dp)
                            .reachingDialogEdges()
                            .weight(1f, fill = false)
                            .fadingTopEdge(listState, sheetContainerColor())
                            .followSheetGrowth(),
                        contentPadding = contentPadding,
                        state = listState,
                    ) {
                        if (isCreatable) {
                            item(key = CREATE_TAG_KEY) {
                                ActionListItem(
                                    modifier = listItemAnimation(listState),
                                    title = textResource(Res.string.song_details_tag_create, typedTag),
                                    icon = painterResource(Res.drawable.ic_add),
                                    horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                    onClick = enterTypedTag,
                                )
                            }
                        }
                        checklistItems(
                            items = matches,
                            order = tagOrder,
                            key = { it },
                            listState = listState,
                            horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                        ) { tag ->
                            CheckboxListItem(
                                modifier = listItemAnimation(listState),
                                title = tag,
                                isChecked = tag in selectedTags,
                                horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                onCheckedChange = { isChecked ->
                                    selectedTags = if (isChecked) selectedTags + tag else selectedTags - tag
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = hasTagChanges,
                onClick = {
                    viewModel.setSongTags(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, tags = tagsToSave, offeredTags = offeredTags)
                    close()
                },
            ) { Text(stringResource(if (dialog.isEditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

/**
 * The languages of one song, asked about all at once: the whole set is written when the dialog is confirmed, so a
 * file the user owns is rewritten once rather than once per checkbox.
 *
 * The list is ordered by the name the platform gives each language in the language the app is set to (see
 * [languageName]), under a selected group of the checked ones ([ChecklistOrder]), which a language checked later joins
 * as a copy while the row itself stays where it was tapped.
 *
 * What is listed before anything is typed is what can be named, plus the languages the song and the library already
 * use. Ordered by usage, the library's come first, most used first: the next song to be filed is far likelier to be in
 * one of them than in any of the six hundred the library has never held. Ordered alphabetically, they are among the
 * rest, since that order is asked for by somebody who looks a language up by its name. Everything else — on the web that is most languages, see
 * [pickableLanguages] — is found by typing its code, which is also what such a row is labelled with. A language
 * nobody can name is still a language the file can be filed under, and the code is the one thing the app always
 * knows about it.
 */
@Composable
private fun SongLanguagesDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongLanguages,
) {
    val appLanguageCode = currentLanguage.value.code
    val libraryLanguages by viewModel.languages.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val sortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE
    var query by rememberSaveable { mutableStateOf("") }
    // Saved, since the dialog outlives a recreated Activity and Save writes whatever is ticked at that moment.
    var selectedCodes by rememberSaveable(
        dialog.song.fileName,
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(dialog.song.languages.toSet()) }
    val languages = remember(dialog.song, libraryLanguages, appLanguageCode, sortingMode) {
        val declared = dialog.song.languages
        val libraryCodes = libraryLanguages.map { it.code }.filterNot { it == SongLanguage.UNKNOWN }
        val pickable = pickableLanguages(appLanguageCode = appLanguageCode, alsoOffer = declared + libraryCodes, normalize = viewModel::normalize)
        val leading = when (sortingMode) {
            UserPreferences.LabelSortingMode.BY_USAGE -> libraryCodes.distinct()
            UserPreferences.LabelSortingMode.ALPHABETICAL -> emptyList()
        }
        leading.mapNotNull { code -> pickable.firstOrNull { it.code == code } } + pickable.filterNot { it.code in leading }
    }
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val matches = remember(languages, query) {
        val normalizedQuery = viewModel.normalize(query)
        // What was typed may be a code, and not the one the library files the language under: `HUN`, `hu-HU` and
        // `hu` all name Hungarian, and whichever of them a reader knows has to find the single row that is.
        val queryCode = viewModel.languageCode(query)
        if (normalizedQuery.isEmpty()) {
            languages.filter { it.isListed }
        } else {
            languages.filter { it.code == queryCode || normalizedQuery in it.sortKey || it.code.startsWith(normalizedQuery) }
        }
    }
    val languageOrder = rememberChecklistOrder(selectedCodes, listOf(sortingMode, query, appLanguageCode))
    val languageLayout = remember(matches, languageOrder) { languageOrder.layout(matches) { it.code } }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_languages_edit),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        startButton = {
            LabelSortingToggle(
                sortingMode = sortingMode,
                onSortingModeSelected = viewModel::setLanguageSortingMode,
            )
        },
        text = { contentPadding ->
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_language_search)) },
                    trailingIcon = rememberClearTextButton(isVisible = query.isNotEmpty(), onClear = { query = "" }),
                    singleLine = true,
                    // What is typed here is as often a code as a name, and autocorrect would make a word of either.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                )
                if (matches.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = stringResource(Res.string.song_details_language_no_search_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val listState = rememberLazyListState()
                    HideKeyboardWhenScrolledDown(listState)
                    ScrollToStartWhenChanged(
                        listState = listState,
                        key = sortingMode to query,
                        contents = matches,
                    )
                    KeepChecklistRowsInPlace(listState, languageLayout)
                    LazyColumn(
                        modifier = Modifier.bounceScrollableContent(listState)
                            .padding(top = 8.dp)
                            .reachingDialogEdges()
                            .weight(1f, fill = false)
                            .fadingTopEdge(listState, sheetContainerColor())
                            .followSheetGrowth(),
                        contentPadding = contentPadding,
                        state = listState,
                    ) {
                        checklistItems(
                            items = matches,
                            order = languageOrder,
                            key = { it.code },
                            listState = listState,
                            horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                        ) { language ->
                            CheckboxListItem(
                                modifier = listItemAnimation(listState),
                                title = language.label,
                                // The code is under the name, and is the name itself where there is none to put above it.
                                description = language.name?.let { language.code.uppercase() },
                                isChecked = language.code in selectedCodes,
                                horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                onCheckedChange = { isChecked ->
                                    selectedCodes = if (isChecked) selectedCodes + language.code else selectedCodes - language.code
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = selectedCodes != dialog.song.languages.toSet(),
                onClick = {
                    viewModel.setSongLanguages(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, codes = selectedCodes.toList())
                    close()
                },
            ) { Text(stringResource(if (dialog.isEditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

/**
 * The setlists one song can be put into, and the way to make a new one. Naming that new setlist happens in a dialog
 * on top of the sheet rather than instead of it: the setlist is only being created so that this song can go into it,
 * so the sheet staying where it is, with a ticked row appearing in it, is what says that it worked.
 *
 * A library with no setlist the song could be put in - none at all, or only archived ones it is not in - skips the
 * sheet and asks for the name of a new one straight away, since a sheet offering nothing to tick is one tap in the way
 * of the only thing that can be done there. Whether that is what happened is decided once, as the dialog opens
 * ([isSkippingToNewSetlist], through [hasListableSetlist]) rather than read from the list as it stands: creating the
 * setlist fills the list, and the sheet must not slide in behind a dialog that is on its way out. It is also what the
 * naming dialog is closed by, since there is nothing behind it to return to.
 */
@Composable
private fun SetlistPicker(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SetlistPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val checkedSetlistKeys = setlists.filter { setlist -> setlist.entries.any { it.songFileName == dialog.song.fileName } }
        .mapTo(mutableSetOf()) { it.fileName }
    val refreshKey = userPreferences?.setlistSortingMode to query
    val setlistOrder = rememberChecklistOrder(checkedSetlistKeys, refreshKey)
    // Archived setlists are offered only while checked or retained after an uncheck. A refresh drops the latter,
    // just as it returns the other unchecked rows to the regular sorting order.
    val pickableSetlists = remember(setlists, setlistOrder.heldKeys) {
        setlists.filter { setlist -> !setlist.isArchived || setlist.fileName in setlistOrder.heldKeys }
    }
    // Answered by the title or the description, the way the setlists screen's own search answers, but not by the
    // songs inside: the song this sheet is about is the only one that matters here.
    val matches = remember(pickableSetlists, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSetlists.filter { setlist ->
            normalizedQuery in viewModel.normalizeForSearch(setlist.title) || normalizedQuery in viewModel.normalizeForSearch(setlist.description)
        }
    }
    val isSkippingToNewSetlist = rememberSaveable { !hasListableSetlist(setlists, dialog.song.fileName) }
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(isSkippingToNewSetlist) }
    val closeNamingDialog = { if (isSkippingToNewSetlist) viewModel.dismissSheet(dialog) else isNamingNewSetlist = false }
    if (!isSkippingToNewSetlist) {
        CampfireBottomSheet(
            title = stringResource(Res.string.songs_choose_setlists),
            subtitle = songLabel(dialog.song),
            actions = { SetlistSortMenu(viewModel = viewModel) },
            onDismiss = { viewModel.dismissSheet(dialog) },
        ) { contentPadding ->
            PickerSearchField(
                query = query,
                placeholder = stringResource(Res.string.setlists_search),
                onQueryChange = { query = it },
            )
            PickerList(
                contentPadding = contentPadding,
                refreshKey = refreshKey,
                contents = matches,
                checklistLayout = remember(matches, setlistOrder) { setlistOrder.layout(matches) { it.fileName } },
                noResultsText = if (matches.isEmpty() && query.isNotBlank()) stringResource(Res.string.setlists_no_search_results) else null,
            ) { listState ->
                checklistItems(
                    items = matches,
                    order = setlistOrder,
                    key = { it.fileName },
                    listState = listState,
                ) { setlist ->
                    CheckboxListItem(
                        modifier = listItemAnimation(listState),
                        title = setlist.title,
                        isChecked = setlist.entries.any { it.songFileName == dialog.song.fileName },
                        isEnabled = !setlist.isArchived && setlist.fileName != dialog.setlistFileName,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                viewModel.addSongToSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
                            } else {
                                viewModel.removeSongFromSetlist(songFileName = dialog.song.fileName, setlistFileName = setlist.fileName)
                            }
                        },
                    )
                }
                item(key = "new_setlist") {
                    ActionListItem(
                        modifier = listItemAnimation(listState),
                        title = stringResource(Res.string.setlists_new_setlist),
                        icon = painterResource(Res.drawable.ic_add),
                        onClick = { isNamingNewSetlist = true },
                    )
                }
            }
        }
    }
    if (isNamingNewSetlist) {
        SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            // A search that found nothing is most likely the name of the setlist that is missing, and it opens
            // selected, so typing something else instead costs nothing.
            initialTitle = query.trim(),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = closeNamingDialog,
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.createSetlistWithSong(
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                    songFileName = dialog.song.fileName,
                )
            },
        )
    }
}

/**
 * Every song in the library with a box each, which is how a setlist is filled from its own side: the setlist picker
 * puts one song into any number of setlists, and this puts any number of songs into one setlist.
 *
 * What is ticked is held here rather than read back from the setlist, and each tick writes that one song in or out
 * ([CampfireViewModel.setSetlistSong]): a list of songs is ticked faster than a write comes back through the
 * library, and boxes that followed the library would each flick back for the length of a round trip. A song ticked
 * here goes to the end of the setlist, so a setlist built from nothing is in the order its songs were picked, and an
 * entry whose file has gone missing stays in it, since it is not listed here to be unticked.
 *
 * The songs in the setlist lead the list as a selected group, a divider separating them from the rest in the selected
 * sorting order ([ChecklistOrder]). A song ticked here joins the end of the group as a copy, in the order it was
 * ticked, which is the order it goes into the setlist in, while the row itself stays where it was tapped.
 *
 * The list can be narrowed by the library's languages and tags as well as by the search ([PickerFilters]). Those are
 * the picker's own rather than following the songs screen's filters. Tag and language selections survive reopening
 * the sheet for the current app run; search starts empty each time.
 */
@Composable
private fun SongPicker(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlist = setlists.firstOrNull { it.fileName == dialog.setlist.fileName } ?: dialog.setlist
    // Seeded from the setlist as the library has it and saved, rather than from the snapshot the dialog was opened
    // with: the dialog outlives a recreated Activity, and a selection seeded again from that snapshot would show every
    // song ticked before it unticked.
    val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName } }
    var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedTags by viewModel.songPickerSelectedTags.collectAsStateWithLifecycle()
    val selectedLanguages by viewModel.songPickerSelectedLanguages.collectAsStateWithLifecycle()
    // Sorted, normalized for the search and counted for the chips by the view model, once per library rather than as
    // the sheet opens or on every keystroke, since the search runs over every song on every character typed and the
    // sheet's first frames are its slide up.
    val pickerSongs by viewModel.pickerSongs.collectAsStateWithLifecycle()
    val pickableSongs = pickerSongs.list
    val filters by viewModel.songPickerFilters.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Only what the chips still offer narrows the list: a tag that left the library while the sheet was open would
    // otherwise keep hiding every song with no chip left to turn it off.
    val activeTags = remember(filters, selectedTags) {
        selectedTags.filterTo(mutableSetOf()) { selected -> filters.tags.any { it.name.lowercase() == selected } }
    }
    val activeLanguages = remember(filters, selectedLanguages) {
        selectedLanguages.filterTo(mutableSetOf()) { selected -> filters.languages.any { it.code == selected } }
    }
    val isFiltered = activeTags.isNotEmpty() || activeLanguages.isNotEmpty()
    // Several values of one group widen the list and the two groups narrow each other, which is what the songs
    // screen's filters do by default: "Hungarian or English, and Christmas".
    val matches = remember(pickableSongs, query, activeTags, activeLanguages) {
        songPickerMatches(
            songs = pickableSongs,
            normalizedQuery = viewModel.normalizeForSearch(query),
            activeTags = activeTags,
            activeLanguages = activeLanguages,
        )
    }
    // An entry whose file has gone missing is not listed, so it is neither unticked here nor counted as ticked.
    val checkedSongKeys = remember(selectedSongFileNames, pickerSongs) {
        selectedSongFileNames.filterTo(mutableSetOf()) { it in pickerSongs.byFileName }
    }
    val refreshKey = listOf(userPreferences?.sortingMode, query, activeTags, activeLanguages)
    val songOrder = rememberChecklistOrder(checkedSongKeys, refreshKey)
    // Keep chip retention outside the lazy header, so changes reset it even while the header is off screen.
    val chipRefreshKey = query to userPreferences?.sortingMode
    val tagChipOrder = rememberChecklistOrder(activeTags, chipRefreshKey to userPreferences?.tagSortingMode)
    val languageChipOrder = rememberChecklistOrder(
        activeLanguages,
        listOf(chipRefreshKey, userPreferences?.languageSortingMode, currentLanguage.value.code),
    )
    CampfireBottomSheet(
        title = stringResource(Res.string.setlists_choose_songs),
        subtitle = setlist.title,
        actions = { SongSortMenu(viewModel = viewModel) },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        PickerSearchField(
            query = query,
            placeholder = stringResource(Res.string.songs_search),
            onQueryChange = { query = it },
        )
        PickerList(
            contentPadding = contentPadding,
            refreshKey = refreshKey,
            contents = matches,
            checklistLayout = remember(matches, songOrder) { songOrder.layout(matches) { it.song.fileName } },
            noResultsText = when {
                // Reached from an empty setlist's "Choose songs" in an empty library, which the setlists screen lists as well.
                songs.isEmpty() -> stringResource(Res.string.songs_empty_title)
                matches.isEmpty() && (query.isNotBlank() || isFiltered) -> stringResource(Res.string.songs_no_search_results)
                else -> null
            },
            revealRowsKey = query.takeIf { it.isNotBlank() },
            header = if (filters.tags.isEmpty() && filters.languages.isEmpty()) null else {
                {
                    PickerFilters(
                        filters = filters,
                        selectedTags = activeTags,
                        selectedLanguages = activeLanguages,
                        tagOrder = tagChipOrder,
                        languageOrder = languageChipOrder,
                        refreshKey = chipRefreshKey,
                        tagSortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
                        languageSortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
                        onTagClicked = viewModel::toggleSongPickerTag,
                        onLanguageClicked = viewModel::toggleSongPickerLanguage,
                        onTagSortingModeSelected = viewModel::setTagSortingMode,
                        onLanguageSortingModeSelected = viewModel::setLanguageSortingMode,
                    )
                }
            },
        ) { listState ->
            checklistItems(
                items = matches,
                order = songOrder,
                key = { it.song.fileName },
                listState = listState,
            ) { pickableSong ->
                val fileName = pickableSong.song.fileName
                CheckboxListItem(
                    modifier = listItemAnimation(listState),
                    title = pickableSong.song.title,
                    description = pickableSong.song.artist.ifBlank { null },
                    isChecked = fileName in selectedSongFileNames,
                    coverArtUrl = pickableSong.song.coverArtUrl?.takeIf { userPreferences?.isCoverArtEnabled == true },
                    onCheckedChange = { isChecked ->
                        selectedSongFileNames = if (isChecked) selectedSongFileNames + fileName else selectedSongFileNames - fileName
                        viewModel.setSetlistSong(setlistFileName = setlist.fileName, songFileName = fileName, isTicked = isChecked)
                    },
                )
            }
        }
    }
}

/**
 * The tags of the library as a row of chips at the top of the [SongPicker]'s list, and its languages as a second row
 * under them, each where there are any to offer. Rows that scroll sideways rather than the wrapping groups of the songs
 * screen's filters: a library can carry a hundred tags, and the sheet is there for the songs under them, which a
 * wrapping block of chips would push off the screen. The languages carry their mark the way they do under a song in
 * the lists, since neither row has a section title to name it.
 *
 * They are the first item of the list rather than pinned under the search field: with the keyboard up on a small
 * phone, the header, the field and two rows of chips held still left the list itself no room at all, and a chip is
 * picked once where the field is typed into throughout. A library with nothing to filter by gets neither row.
 *
 * Selected chips lead each row, newest first, with an animated scroll to the start. Deselected chips remain in
 * that group until search or sorting changes. A divider separates it from the remaining chips in the selected
 * sorting order, and each row starts with the toggle that switches that order ([SortableChipRow]).
 */
@Composable
private fun PickerFilters(
    modifier: Modifier = Modifier,
    filters: PickerFilterOptions,
    selectedTags: Set<String>,
    selectedLanguages: Set<String>,
    tagOrder: ChecklistOrder,
    languageOrder: ChecklistOrder,
    refreshKey: Any?,
    tagSortingMode: UserPreferences.LabelSortingMode,
    languageSortingMode: UserPreferences.LabelSortingMode,
    onTagClicked: (String) -> Unit,
    onLanguageClicked: (String) -> Unit,
    onTagSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    onLanguageSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) {
    val appLanguageCode = currentLanguage.value.code
    val tags = remember(filters.tags, tagSortingMode) { filters.tags.orderedBy(tagSortingMode) }
    val languages = remember(filters.languages, languageSortingMode, appLanguageCode) {
        filters.languages.orderedBy(languageSortingMode) { code -> languageName(code = code, appLanguageCode = appLanguageCode) ?: code.uppercase() }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        if (filters.tags.isNotEmpty()) {
            SortableChipRow(
                items = tags,
                key = { it.name.lowercase() },
                order = tagOrder,
                refreshKey = refreshKey,
                sortingMode = tagSortingMode,
                onSortingModeSelected = onTagSortingModeSelected,
            ) { tag ->
                val key = tag.name.lowercase()
                CountedFilterChip(
                    label = tag.name,
                    songCount = tag.songCount,
                    isSelected = key in selectedTags,
                    onClick = { onTagClicked(key) },
                    leadingIcon = painterResource(Res.drawable.ic_label),
                )
            }
        }
        if (filters.languages.isNotEmpty()) {
            SortableChipRow(
                items = languages,
                key = { it.code },
                order = languageOrder,
                refreshKey = refreshKey to appLanguageCode,
                sortingMode = languageSortingMode,
                onSortingModeSelected = onLanguageSortingModeSelected,
            ) { language ->
                CountedFilterChip(
                    label = languageLabel(language.code),
                    songCount = language.songCount,
                    isSelected = language.code in selectedLanguages,
                    onClick = { onLanguageClicked(language.code) },
                    leadingIcon = painterResource(Res.drawable.ic_language),
                )
            }
        }
    }
}

/** Both assignment sheets open with their lists visible; tapping search brings up the keyboard. */
@Composable
private fun PickerSearchField(
    modifier: Modifier = Modifier,
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        value = query,
        onValueChange = { onQueryChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = rememberClearTextButton(isVisible = query.isNotEmpty(), onClear = { onQueryChange("") }),
        singleLine = true,
        // Autocorrect is off for the reason it is off in the list screens' search, see SearchableTopAppBar.
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

/**
 * The scrolling part of a picker sheet, which takes whatever height the sheet has left under its header rather than
 * the height of everything it could list - a library of songs is far taller than any screen.
 *
 * Its content never gets shorter while the sheet is open, only taller: narrowing the list by a search would otherwise
 * move the field being typed into down the screen along with it. The bottom inset is kept separately, so dismissing
 * the keyboard returns the sheet to its content's height instead of retaining empty space below the rows.
 *
 * @param contentPadding What the sheet's content keeps clear at the bottom, applied inside the scroll so that the last
 *   rows pass under the navigation bar and the keyboard on their way up.
 * @param refreshKey Search, sorting and filters; a change sends the list back to its first row once the
 *   refreshed [contents] arrive ([ScrollToStartWhenChanged]).
 * @param checklistLayout Where the rows of the checklist in the content start, which keeps them in place as its
 *   selected group grows ([KeepChecklistRowsInPlace]).
 * @param noResultsText What to say in place of the rows, null while there is nothing to say.
 * @param header What the list starts with and scrolls away with the rows, above what it says in their place, so that a
 *   filter that left nothing can still be turned off.
 * @param revealRowsKey Scrolls the [header] out of the way whenever it changes to something other than null - the
 *   search being typed, since the keyboard is up then and the rows it finds are what there is room for.
 */
@Composable
private fun ColumnScope.PickerList(
    contentPadding: PaddingValues,
    refreshKey: Any?,
    contents: Any?,
    checklistLayout: ChecklistLayout,
    noResultsText: String?,
    revealRowsKey: Any? = null,
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.(LazyListState) -> Unit,
) {
    val listState = rememberLazyListState()
    val hasHeader = header != null
    LaunchedEffect(revealRowsKey, hasHeader) {
        if (revealRowsKey != null && hasHeader) {
            listState.scrollToItem(1)
        }
    }
    HideKeyboardWhenScrolledDown(listState)
    ScrollToStartWhenChanged(
        listState = listState,
        key = refreshKey,
        contents = contents,
    )
    KeepChecklistRowsInPlace(listState, checklistLayout)
    LazyColumn(
        modifier = Modifier.bounceScrollableContent(listState)
            .weight(1f, fill = false)
            .retainSheetContentHeight(contentPadding)
            // The rows fade out as they scroll up under the search field, which is the edge between the two
            // everywhere else in the app too.
            .fadingTopEdge(listState, sheetContainerColor())
            .followSheetGrowth(),
        state = listState,
        // The gap under the search field is the list's own content padding rather than a padding around the list, so
        // that a scrolled row goes under the field itself instead of being cut off a few pixels short of it.
        contentPadding = contentPadding.only(bottom = true, extraTop = PICKER_LIST_TOP_PADDING),
    ) {
        if (header != null) {
            item(key = "header") {
                Column(modifier = Modifier.padding(bottom = PICKER_LIST_TOP_PADDING)) {
                    header()
                }
            }
        }
        if (noResultsText != null) {
            item(key = "no_results") {
                Text(
                    modifier = listItemAnimation(listState).fillMaxWidth().padding(16.dp),
                    text = noResultsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content(listState)
    }
}

/**
 * The same surface color for every sheet and any Material container drawn inside it, including the calendar, and so
 * the color the edge fades of a sheet's scrolling content are painted in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun sheetContainerColor() = MaterialTheme.colorScheme.background.let { background ->
    // Dark sheets keep Material's lighter surface so they remain visible against the scrim.
    if (background.luminance() < 0.5f) BottomSheetDefaults.ContainerColor else background
}

/**
 * Every sheet of the app, drawn edge to edge: the sheet runs down under the navigation bar and the keyboard instead
 * of stopping above them, and only its content is kept clear of them. The keyboard's height pads the sheet's column
 * outside the content's scroll, so that a focused field is brought above the keyboard rather than into a viewport
 * running on under it. [ModalBottomSheet] pads the whole content by the bottom inset by default, which leaves a
 * scrolling list ending on a band of the sheet's color above the bar rather than scrolling on under it, so that inset
 * is left out of the sheet's own insets and handed to the content instead, as the `contentPadding` scrolling content
 * applies inside its scroll and the rest leaves under its last row.
 * The top inset stays with the sheet, which only pads by it once it has been dragged up against the status bar.
 * Horizontally, the whole sheet is centered between the safe edges, up to its maximum width. Side insets never
 * become padding inside a narrow sheet that is already clear of those edges.
 * Bottom padding excludes the insets the keyboard's padding already consumed, which leaves the navigation bar with the
 * keyboard down and nothing with it up, and is read during layout so content follows the current keyboard animation
 * frame.
 *
 * @param title What the sheet is about, named in its [SheetHeader].
 * @param subtitle What the sheet acts on, under [title]: the song or the setlist it was opened for. Left out when blank.
 * @param actions Buttons at the end of the [SheetHeader], across from the close button, such as the order of a list or
 *   the button that finishes what the sheet is for. The close they are handed is final: unlike the header's close
 *   button, a hide that follows an action is not taken back by a finger landing on the sliding sheet. The ones that
 *   write ([BottomSheetConfirmButton]) do nothing once the sheet has started closing ([LocalIsSheetClosing]).
 * @param onDismiss Has to dismiss this sheet's own dialog and nothing else (`CampfireViewModel.dismissSheet`): it is
 *   called from the end of a hide animation, by which time another dialog may have taken the sheet's place.
 * @param content Can close the sheet ([BottomSheetContentScope.close]), for a sheet with a button of its own that is
 *   done with it. That close is final the way the actions' is.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CampfireBottomSheet(
    title: String,
    subtitle: String = "",
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    actions: (@Composable RowScope.(close: () -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable BottomSheetContentScope.(contentPadding: PaddingValues) -> Unit,
) {
    // Open at the content's full height: long lists can use the whole window, and short forms stay compact.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val scrollState = rememberScrollState()
    // Hiding the sheet by hand does not count as dismissing it, so the dialog state is cleared once it is gone: left
    // as it was, the invisible sheet's modal layer would stay over the screen, swallowing the next tap. The close
    // button's hide only counts when it ran to its end: one cut short by a finger taking hold of the sheet leaves the
    // sheet where Material settles it, with the draft still in it, and one cut short by another dialog replacing the
    // sheet has nothing left to dismiss.
    // Set as the tap is handled, so the second tap of the same frame already reads it; Material's own hide (a swipe,
    // the scrim) shows up as the sheet's target becoming Hidden while it is still on screen.
    var isCloseRequested by remember { mutableStateOf(false) }
    // Set by a close that follows something the sheet has already done - a Save, a Create, a Delete. Such a hide is
    // never taken back: the sheet's gestures are off for the slide, and a hide cut short anyway is finished from where
    // it is, since a sheet left up after its action would offer an action that has already happened (a dead Create over
    // the editor).
    var isCloseFinal by remember { mutableStateOf(false) }
    val isClosing = remember(sheetState) {
        { isCloseRequested || (sheetState.targetValue == SheetValue.Hidden && sheetState.currentValue != SheetValue.Hidden) }
    }
    val requestClose = { isFinal: Boolean ->
        if (!isCloseRequested) {
            isCloseRequested = true
            isCloseFinal = isFinal
            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                when {
                    cause == null -> onDismiss()
                    // A finger that took hold of the sheet keeps it, and its actions with it.
                    !isCloseFinal -> isCloseRequested = false
                    else -> coroutineScope.launch {
                        // Cut short after its action: by a hide of Material's own (the scrim, back, Escape), which is
                        // let run to its end, or by a press that took hold of the sheet before the recomposition that
                        // turned its gestures off, from whose hold it is hidden again. A frame first, by which either
                        // has started. Calling hide while Material's own runs would cancel that one, and back's settle
                        // dismisses in one frame when it is cancelled.
                        while (sheetState.isVisible) {
                            withFrameNanos { }
                            if (sheetState.isAnimationRunning && sheetState.targetValue == SheetValue.Hidden) continue
                            try {
                                sheetState.hide()
                            } catch (exception: CancellationException) {
                                // Refused while a press still holds the sheet: tried again on the next frame.
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    }.invokeOnCompletion { onDismiss() }
                }
            }
        }
    }
    val cancel = { requestClose(false) }
    val close = { requestClose(true) }
    val saveShortcut = remember { SheetSaveShortcut() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Material applies sheetMaxWidth after this modifier. First reserve the safe horizontal span, then
        // center the capped surface inside it. Resolve insets in the modal window's composition, not the
        // activity behind it, and leave the inset gaps outside the sheet's background and gesture bounds.
        modifier = Modifier.composed {
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .wrapContentWidth()
        },
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        // Off for the slide of a final close, so that the second press of a double tap does not take hold of it.
        sheetGesturesEnabled = !isCloseFinal,
        containerColor = sheetContainerColor(),
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        // Derived, so that the sheet recomposes as the keyboard comes and goes rather than on every frame it slides.
        val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        val keyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        // Handle back inside this modal window, before Material starts hiding the sheet. Keep the handler registered
        // even with the keyboard down so its priority stays below any nested sheet composed in the content below.
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = isKeyboardVisible,
            onBackCompleted = {
                keyboardController?.hide()
                // Release the focused text input too, so hiding the web keyboard also ends the editing session.
                focusManager.clearFocus(force = true)
            },
        )
        val isCompactKeyboard = windowHeight < SHORT_WINDOW_HEIGHT && isKeyboardVisible
        CompactKeyboardEffect(isEnabled = isCompactKeyboard)
        // A sheet that opens onto none of its fields would otherwise hold no focus, and a key only travels along the
        // focus path, so Ctrl / Cmd + S would go unheard until something in it was clicked. A bare focus target draws
        // nothing and brings no keyboard up. It is taken a frame in, and only where nothing in the sheet has the focus
        // by then: a form's first field asks for it from an effect of its own, and the order the two effects run in is
        // no promise of which one ends up with it.
        val sheetFocus = remember { FocusRequester() }
        val sheetFocusState = remember { SheetFocusState() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!sheetFocusState.hasFocus) sheetFocus.requestFocus()
        }
        // A keyboard in a short window leaves less height than the header and a field take together, so there the
        // header and the pinned controls scroll with the rest, above the keyboard, and bringing the caret into view can
        // move them out of its way. The content keeps the window's height inside that scroll, which is what bounds
        // the lists in it that would otherwise be measured against an infinite one.
        Column(
            modifier = Modifier.fillMaxWidth()
                .saveShortcut { saveShortcut.action?.invoke() }
                .onFocusChanged { sheetFocusState.hasFocus = it.hasFocus }
                .focusRequester(sheetFocus)
                .focusTarget()
                .then(
                    if (isCompactKeyboard) {
                        Modifier.heightIn(max = windowHeight).imePadding()
                            .fadingTopEdge(scrollState, sheetContainerColor())
                            .bounceVerticalScroll(scrollState)
                    } else {
                        // Outside the content's own scroll, so that its viewport ends at the keyboard and a field focused
                        // with Next is scrolled above it: padded inside the scroll, the viewport ran on under the keyboard,
                        // where the field already counted as visible.
                        Modifier.imePadding()
                    },
                ),
        ) {
            val consumedInsets = remember { MutableWindowInsets() }
            val heightAnimation = remember { SheetHeightAnimation() }
            Column(
                modifier = (if (isCompactKeyboard) Modifier.height(windowHeight) else Modifier)
                    .animateSheetContentHeight(heightAnimation)
                    .onConsumedWindowInsetsChanged { consumedInsets.insets = it },
            ) {
                CompositionLocalProvider(
                    LocalIsSheetClosing provides isClosing,
                    LocalSheetSaveShortcut provides saveShortcut,
                    LocalSheetPendingGrowth provides heightAnimation::pendingGrowth,
                ) {
                    SheetHeader(
                        title = title,
                        subtitle = subtitle,
                        actions = actions,
                        onClose = cancel,
                        onActionDone = close,
                    )
                    // Read inside the sheet, which is a window of its own on Android and gets the insets of that window.
                    // asPaddingValues alone ignores consumption. The column above already pads above the IME, which
                    // also covers the navigation bar; reserve only the bottom space still left to this content.
                    val bottomPadding = WindowInsets.safeDrawing.exclude(consumedInsets)
                        .only(WindowInsetsSides.Bottom).asPaddingValues()
                    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                    // Material pads the sheet by as much of the top inset as its offset has not taken up yet (an offset not
                    // decided yet takes up none of it).
                    val uncoveredTopInset = remember(sheetState, topInset, density) {
                        derivedStateOf {
                            val offset = runCatching { sheetState.requireOffset() }.getOrDefault(0f)
                            with(density) { offset.coerceIn(0f, topInset.getTop(density).toFloat()).toDp() }
                        }
                    }
                    BottomSheetContentScope(
                        columnScope = this,
                        close = close,
                        uncoveredTopInset = { uncoveredTopInset.value },
                    ).content(bottomPadding.only(bottom = true, extraBottom = SHEET_BOTTOM_PADDING))
                }
            }
        }
    }
}

/**
 * Grows and shrinks a sheet to its content's new height rather than in one frame, when rows are added to a list in it or
 * taken away, a field's error appears, or the content is swapped for another. The sheet's top edge follows the height,
 * since Material anchors the sheet by it.
 *
 * Only the content's own changes are animated. A change of the room offered (the keyboard sliding, which arrives as a
 * new maximum on every frame of its own animation, or the window being resized) is followed at once, or the sheet
 * would trail behind the keyboard and leave a gap over it. While the sheet is growing the content is already measured
 * at its new height and placed under the animated one, so the content is uncovered from the sheet's bottom edge, which
 * the sheet's shape clips, and how much of it is still covered is [SheetHeightAnimation.pendingGrowth].
 */
@Composable
private fun Modifier.animateSheetContentHeight(state: SheetHeightAnimation): Modifier {
    val coroutineScope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val targetHeight = placeable.height
        val animatable = state.animatable
        val snappedHeight = state.snappedHeight
        val height = if (animatable == null || constraints.maxHeight != state.maxHeight) {
            // The first measure opens the sheet at its full height, which Material slides in on its own.
            if (animatable == null) {
                state.animatable = Animatable(targetHeight.toFloat())
            } else {
                // A snap is a coroutine that only lands after this frame, so the height is held here until it has.
                state.snappedHeight = targetHeight
                coroutineScope.launch { animatable.snapTo(targetHeight.toFloat()) }
            }
            state.maxHeight = constraints.maxHeight
            targetHeight
        } else if (snappedHeight == targetHeight && animatable.value.roundToInt() != snappedHeight) {
            snappedHeight
        } else {
            state.snappedHeight = null
            if (animatable.targetValue != targetHeight.toFloat()) {
                coroutineScope.launch { animatable.animateTo(targetHeight.toFloat(), spec) }
            }
            animatable.value.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        state.pendingGrowthState.intValue = (targetHeight - height).coerceAtLeast(0)
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
}

/**
 * What [animateSheetContentHeight] animates, the maximum height it was offered last and the height it snapped to for
 * that, which is not state: it is only read and written by its measure pass. [pendingGrowth] is, since the content
 * reads it as it is placed ([followSheetGrowth]), in the same frame.
 */
private class SheetHeightAnimation {
    var animatable: Animatable<Float, AnimationVector1D>? = null
    var maxHeight = 0
    var snappedHeight: Int? = null
    val pendingGrowthState = mutableIntStateOf(0)

    fun pendingGrowth() = pendingGrowthState.intValue
}

/**
 * How many pixels the [CampfireBottomSheet] around it still has to grow to its content's height, 0 while it is not
 * growing.
 */
private val LocalSheetPendingGrowth = staticCompositionLocalOf<() -> Int> { { 0 } }

/**
 * Keeps the rows of a list in a growing [CampfireBottomSheet] where they are on screen. The sheet's top edge rises as it
 * grows, and the list, measured at its new height already and hanging from that edge, would carry the rows below a
 * newly inserted one a row lower than where they end up and bring them back up as the sheet grows - away from under the
 * finger that ticked the row and back. Shifted up by what the sheet still has to grow, inside the list's own bounds,
 * the rows stay put and what was inserted slides in from under the controls above the list, as it would in a list
 * already scrolled to its end.
 *
 * Goes after the list's edge fades, so that they stay at the edges of its bounds rather than travel with the rows.
 */
@Composable
private fun Modifier.followSheetGrowth(): Modifier {
    val pendingGrowth = LocalSheetPendingGrowth.current
    return clipToBounds().offset { IntOffset(0, -pendingGrowth()) }
}

/**
 * Whether the [CampfireBottomSheet] around it has started closing - its close button, a Save that closes it, a swipe or
 * the scrim. The sheet stays on screen and live for the length of its slide, and a Save tapped in it would write the
 * draft the close button had just cancelled, so the actions that write ask this first. It is a function rather than a
 * value, read at the tap, so that nothing recomposes on the frames of the slide.
 */
internal val LocalIsSheetClosing = compositionLocalOf<() -> Boolean> { { false } }

/**
 * What Ctrl / Cmd + S presses in a [CampfireBottomSheet]: the [BottomSheetConfirmButton] in its header that writes
 * what the sheet is for, which sets itself here while it is there. Null where the sheet has no such button, or one the
 * key should not press.
 */
internal class SheetSaveShortcut {
    var action: (() -> Unit)? = null
}

/** Whether anything in a [CampfireBottomSheet] has the focus, read once from an effect rather than drawn from. */
private class SheetFocusState {
    var hasFocus = false
}

/** The [SheetSaveShortcut] of the [CampfireBottomSheet] around it, null outside one. */
internal val LocalSheetSaveShortcut = staticCompositionLocalOf<SheetSaveShortcut?> { null }

/**
 * The column of a [CampfireBottomSheet], which its content can also close the sheet from.
 *
 * @param uncoveredTopInset How much of the top inset the sheet is not padded by at the moment: Material pads it by the
 *   part of the inset its top edge has come into, so the height its content is offered changes as it slides up and is
 *   dragged. Content that sizes itself to that height takes this off it to have the height of the sheet at its
 *   tallest, which does not move - or the sheet's height would decide its own offset, and the offset the height.
 */
internal class BottomSheetContentScope(
    columnScope: ColumnScope,
    private val close: () -> Unit,
    val uncoveredTopInset: () -> Dp,
) : ColumnScope by columnScope {

    fun close() = close.invoke()
}

/**
 * The top of every sheet: what it is about, and a close button. A sheet whose list grows past the screen covers the
 * whole of it once it is dragged up, and a sheet that fills the screen has no scrim left to tap and no edge that looks
 * like it could be dragged back down, so the button is on the short sheets too, where the next one opened may not be
 * short. A sheet about one song or one setlist names it in the subtitle ([songLabel], or the setlist's title), even
 * where the screen behind it is that very song: it is opened from rows of other lists as readily as from the thing
 * itself, and one that named what its boxes are about only some of the time would read as two different sheets.
 */
@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    actions: (@Composable RowScope.(close: () -> Unit) -> Unit)?,
    onClose: () -> Unit,
    onActionDone: () -> Unit,
) = Row(
    // An action at the end sits as far from the edge as the close button does from the start.
    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = if (actions == null) 16.dp else 4.dp, top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onClose) {
        Icon(
            painter = painterResource(Res.drawable.ic_clear),
            contentDescription = stringResource(Res.string.close),
        )
    }
    Column(
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (actions != null) {
        // Header actions need the same readable size as the title while retaining button label weight and spacing.
        val typography = MaterialTheme.typography
        val actionTypography = remember(typography) {
            typography.copy(labelLarge = typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
        }
        MaterialTheme(typography = actionTypography) { actions(onActionDone) }
    }
}

private val SHEET_BOTTOM_PADDING = 16.dp
private val PICKER_LIST_TOP_PADDING = 8.dp
private const val MAX_TITLE_LENGTH = 60

/** The tags ticked in the tag dialog, and the ones created there, as a Bundle can hold them. */
private val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/** A tag is a label to filter by, a word or two, and it sits in a pill next to others under a song's title. */
private const val MAX_TAG_LENGTH = 30
/**
 * The key of the tag dialog's create row. A tag row's key is its tag behind a `tag:` prefix, since a tag is text anybody
 * may have written, and only the prefix keeps one spelled like this key from being listed under the same key as the row.
 */
private const val CREATE_TAG_KEY = "create"
private const val DELETE_LIBRARY_CONFIRMATION = "DELETE"
private const val MAX_DELETE_LIBRARY_CONFIRMATION_LENGTH = 30
private const val MAX_DESCRIPTION_LENGTH = 300
private const val DESCRIPTION_LINES = 3

/** What a whole date and the countdown's label take side by side, in either language. */
private val MIN_DATE_ROW_WIDTH = 360.dp

/** What Material's `OutlinedTextField` leaves above its border for the label that sits on it. */
private val OUTLINED_FIELD_LABEL_ROOM = 8.dp

/**
 * What Material's `AlertDialog` pads its content by on every side. Material narrows it for a precision pointer, but only
 * behind a flag the app does not turn on.
 */
private val DIALOG_CONTENT_PADDING = 24.dp

/**
 * What lines the checkboxes of a dialog's checklist up with the edge of the field above them: the list item keeps
 * 16 dp at its start and the checkbox draws its box 2 dp inside its own bounds, which leaves this much of the dialog's
 * padding for the row to add.
 */
private val DIALOG_CHECKLIST_ROW_INSET = DIALOG_CONTENT_PADDING - 18.dp

/**
 * A dialog's checklist running out over the dialog's padding to both of its edges, so that a row lights up across the
 * whole dialog when it is pressed, the way it does in a sheet, while reporting only the width of the content around
 * it so that everything else is laid out as it would be without it. The rows keep their content in line with the rest
 * of the dialog's with [DIALOG_CHECKLIST_ROW_INSET].
 */
private fun Modifier.reachingDialogEdges() = layout { measurable, constraints ->
    val outset = DIALOG_CONTENT_PADDING.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = outset * 2))
    layout((placeable.width - outset * 2).coerceAtLeast(0), placeable.height) {
        placeable.placeRelative(-outset, 0)
    }
}

/**
 * What a song says about itself, read from its text as it is now: its details, the defaults its file declares for how
 * it is played — with a line saying where the song is being played differently from them, which is what the steppers on
 * the page show — and its tags, languages and links. Outside read only mode the header edits details and cover art and
 * the body edits the rest, opening their dialogs on top of the sheet — the cover art button there only while the song has
 * no cover, which is changed from the cover itself once it has one; read only mode shows only what the song has,
 * without editing controls.
 */
@Composable
private fun SongInfoSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongInfo,
    urlOpener: (String) -> Unit,
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val setlistFileName = (viewModel.backStack.lastOrNull() as? CampfireDestination.SongDetails)?.setlistFileName
    val isReadOnly = isPerformanceModeEnabled || setlists.any { it.fileName == setlistFileName && it.isArchived }
    val text = songTexts[dialog.song.fileName]
    val metadata = remember(text) { text?.let(viewModel::songMetadataOf) }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val shouldShowTempo = userPreferences?.isMetronomeEnabled != false
    val editing = rememberSongInfoEditing(viewModel = viewModel, song = song, isEditorDraft = false)
    val overrides = songPlayingOverrides(viewModel = viewModel, song = song, setlistFileName = setlistFileName)
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_song_info),
        subtitle = songLabel(song),
        actions = if (isReadOnly) null else {
            {
                // A song with a cover changes it from the cover itself, which the body draws with a pencil on it.
                val onSetCoverArt = editing.onEditCoverArt?.takeIf { metadata?.coverArt.isNullOrBlank() }
                onSetCoverArt?.let { onEditCoverArt ->
                    IconButton(onClick = onEditCoverArt, enabled = metadata != null) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_album),
                            contentDescription = stringResource(Res.string.song_details_set_cover_art),
                        )
                    }
                }
                IconButton(
                    modifier = if (onSetCoverArt != null) Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp) else Modifier,
                    onClick = editing.onEditMetadata,
                    enabled = metadata != null,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.song_details_metadata_edit),
                    )
                }
            }
        },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        if (metadata != null) {
            val scrollState = rememberScrollState()
            SongInfoBody(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fadingTopEdge(scrollState, sheetContainerColor())
                    .bounceVerticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(vertical = 8.dp),
                metadata = metadata,
                coverArtUrl = metadata.coverArt.takeIf { userPreferences?.isCoverArtEnabled == true },
                horizontalPadding = 16.dp,
                editing = editing.takeUnless { isReadOnly },
                // Each value goes with its feature, and the group with both of them, see SongPlayingDialog.
                defaults = if (shouldShowChords || shouldShowTempo) {
                    SongDefaults(
                        key = metadata.key?.takeIf { shouldShowChords && it.isNotBlank() }?.let(viewModel::editorKeyOf),
                        capo = metadata.capo?.takeIf { shouldShowChords },
                        tempo = ChordProTempo.parse(metadata.tempo)?.takeIf { shouldShowTempo },
                        time = metadata.time?.takeIf { shouldShowTempo },
                        overrides = overrides.labels,
                        isReadFromSetlist = setlistFileName != null,
                        onEdit = if (isReadOnly) null else ({ viewModel.showSongPlayingDialog(song = song, setlistFileName = setlistFileName) }),
                    )
                } else {
                    null
                },
                onOpenLink = urlOpener,
            )
        }
    }
}

/**
 * How a dialog, a sheet or a screen about one song names it under its title: `Artist - Title (Subtitle)`, the subtitle
 * being part of [Song.title] already, or the title alone for a song that names no artist. Every such subtitle goes
 * through here, so that one song is named the same way wherever it is the subject.
 */
@Composable
internal fun songLabel(song: Song) = if (song.artist.isBlank()) song.title else textResource(Res.string.songs_artist_and_title, song.artist, song.title)

/** A field whose value becomes one line of a song file: a pasted line break is the space between two words. */
private fun String.asSingleLine() = replace(lineBreakRegex, " ")

private val lineBreakRegex = Regex("[\\r\\n]+")
