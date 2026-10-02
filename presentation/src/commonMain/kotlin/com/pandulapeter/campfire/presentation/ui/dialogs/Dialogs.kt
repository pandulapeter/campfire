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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.ImportConflictResolution
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
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
import com.pandulapeter.campfire.presentation.resources.ic_calendar
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace_description
import com.pandulapeter.campfire.presentation.resources.import_replace_title
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
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
import com.pandulapeter.campfire.presentation.resources.setlists_song_assignments
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
import com.pandulapeter.campfire.presentation.resources.song_details_tag_create
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_tags_search
import com.pandulapeter.campfire.presentation.resources.song_editor_discard
import com.pandulapeter.campfire.presentation.resources.song_editor_revert
import com.pandulapeter.campfire.presentation.resources.song_editor_revert_confirmation
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes
import com.pandulapeter.campfire.presentation.resources.song_editor_unsaved_changes_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_artist_and_title
import com.pandulapeter.campfire.presentation.resources.songs_clear
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_delete_song_confirmation
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
import com.pandulapeter.campfire.presentation.resources.welcome_get_started
import com.pandulapeter.campfire.presentation.resources.welcome_message
import com.pandulapeter.campfire.presentation.resources.welcome_open_settings
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint_sync
import com.pandulapeter.campfire.presentation.resources.welcome_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.PickerFilterOptions
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.CountedFilterChip
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.LabelSortingToggle
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.SortableChipRow
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
import com.pandulapeter.campfire.presentation.ui.components.orderedBy
import com.pandulapeter.campfire.presentation.ui.components.pickableLanguages
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongInfoBody
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberSongInfoEditing
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
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
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importReport by viewModel.importReport.collectAsStateWithLifecycle()
    // The import screen shows the progress of the imports it reports on itself.
    ImportProgressDialogHost(progress = importProgress.takeIf { importReport == null }, canShow = visibleDialog == null)
    when (val dialog = visibleDialog) {
        // Drawn by ExportHost, which deals it over the screens rather than in a window of its own.
        is CampfireViewModel.DialogType.Export -> Unit
        CampfireViewModel.DialogType.NewSetlist -> SetlistDetailsDialog(
            title = stringResource(Res.string.setlists_new_setlist),
            confirmLabel = stringResource(Res.string.create),
            onDismiss = viewModel::dismissDialog,
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
            initialTitle = dialog.setlist.title,
            initialDescription = dialog.setlist.description,
            initialDate = dialog.setlist.date,
            initialIsCountdownShown = dialog.setlist.isCountdownShown,
            confirmLabel = stringResource(Res.string.save),
            onDismiss = viewModel::dismissDialog,
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.editSetlist(
                    setlistFileName = dialog.setlist.fileName,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
                viewModel.dismissDialog()
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
            onDismiss = viewModel::dismissDialog,
            onConfirm = { setlistTitle, description, date, isCountdownShown ->
                viewModel.duplicateSetlist(
                    setlist = dialog.setlist,
                    title = setlistTitle,
                    description = description,
                    date = date,
                    isCountdownShown = isCountdownShown,
                )
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.NewSong -> NewSongDialog(
            onDismiss = viewModel::dismissDialog,
            onCreate = { title, artist ->
                viewModel.createSong(title = title, artist = artist)
                viewModel.dismissDialog()
            },
        )

        CampfireViewModel.DialogType.SongFilters -> CampfireBottomSheet(
            title = stringResource(Res.string.songs_filter),
            onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.SongFilters) },
        ) { contentPadding ->
            SongFilters(
                viewModel = viewModel,
                contentPadding = contentPadding,
                uncoveredTopInset = uncoveredTopInset,
            )
        }

        is CampfireViewModel.DialogType.SongInfo -> SongInfoSheet(
            viewModel = viewModel,
            dialog = dialog,
            urlOpener = urlOpener,
        )

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

        is CampfireViewModel.DialogType.CoverArtSearch -> CoverArtSearchSheet(
            viewModel = viewModel,
            dialog = dialog,
        )

        is CampfireViewModel.DialogType.RemoveSongCoverArt -> ConfirmationDialog(
            title = stringResource(Res.string.cover_art_search_remove),
            text = textResource(Res.string.song_details_remove_cover_art_confirmation, dialog.song.title),
            confirmLabel = stringResource(Res.string.cover_art_search_remove),
            onDismiss = viewModel::dismissDialog,
            onConfirm = {
                viewModel.setSongCoverArt(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, url = null)
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

        null -> Unit
    }
}

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
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
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
) = Column(
    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(contentPadding),
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
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(Res.string.song_editor_unsaved_changes)) },
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
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val isConfirmed = value.text.trim() == DELETE_LIBRARY_CONFIRMATION
    val focusRequester = rememberFirstFieldFocusRequester()
    val confirmOnce = rememberSingleConfirmation()
    val deleteLibrary = {
        confirmOnce {
            viewModel.deleteLibrary()
            viewModel.dismissDialog()
        }
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = { Text(stringResource(Res.string.settings_library_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (isConfirmed) deleteLibrary() }),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isConfirmed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = deleteLibrary,
            ) { Text(stringResource(Res.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The [FocusRequester] of the field a dialog opens onto. A dialog that is there to be typed into puts the caret in
 * its first field rather than asking for one more tap, which on a touch platform is also what brings the keyboard
 * up with it - and every such dialog here holds that field as the first thing under the title, so there is only ever
 * the one field to open on. The forms that are opened to be looked over as much as to be typed into are the exception,
 * opening on none of their fields: the song metadata form, and a setlist's details being edited. The song picker's
 * sheet opens onto its search field the same way.
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
 * already reads it.
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
 */
@Composable
private fun SetlistDetailsDialog(
    title: String,
    subtitle: String = "",
    isTitleFocused: Boolean = true,
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
        mutableStateOf(TextFieldValue(text = initialTitle, selection = TextRange(0, initialTitle.length)))
    }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    // Saved as its ISO text, since a LocalDate is nothing the saved instance state of every platform can hold.
    var dateText by rememberSaveable { mutableStateOf((initialDate ?: today()).toString()) }
    val date = LocalDate.parse(dateText)
    var isCountdownShown by rememberSaveable { mutableStateOf(initialIsCountdownShown) }
    val isValid = setlistTitle.text.isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester(isFocused = isTitleFocused)
    val confirmOnce = rememberSingleConfirmation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { if (subtitle.isBlank()) Text(title) else SubjectDialogTitle(title = title, subtitle = subtitle) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { confirmOnce { onConfirm(setlistTitle.text, description, date, isCountdownShown) } },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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
) = BoxWithConstraints {
    if (maxWidth >= MIN_DATE_ROW_WIDTH) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SetlistDateField(
                modifier = Modifier.weight(1f),
                date = date,
                onDateChange = onDateChange,
            )
            Spacer(modifier = Modifier.width(8.dp))
            // An outlined field keeps room above its border for the label to sit in, and it is the border the box is
            // meant to be centered against, not the field with that room.
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
                onDateChange = onDateChange,
            )
            Spacer(modifier = Modifier.height(4.dp))
            SetlistCountdownCheckbox(
                isChecked = isCountdownShown,
                onCheckedChange = onCountdownShownChange,
            )
        }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetlistDateField(
    modifier: Modifier = Modifier,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var isPickerVisible by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) isPickerVisible = true }
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
            IconButton(onClick = { isPickerVisible = true }) {
                Icon(painter = painterResource(Res.drawable.ic_calendar), contentDescription = stringResource(Res.string.setlists_pick_date))
            }
        },
        interactionSource = interactionSource,
    )
    if (isPickerVisible) {
        // The picker counts in milliseconds of UTC midnights, whatever the device's time zone, so the day goes in and
        // comes out through UTC rather than through the local zone, which would move it by a day on one side of it.
        var selectedMillis by rememberSaveable { mutableStateOf(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()) }
        // The calendar is given the app's language rather than the system's, which is what rememberDatePickerState
        // would take. The state built here is not saveable, so the day picked but not yet confirmed is carried
        // through a rotation by selectedMillis instead.
        val languageCode = currentLanguage.value.code
        val locale = remember(languageCode) { calendarLocale(languageCode) }
        val state = remember(locale) { DatePickerState(locale = locale, initialSelectedDateMillis = selectedMillis) }
        LaunchedEffect(state) { snapshotFlow { state.selectedDateMillis }.collect { it?.let { millis -> selectedMillis = millis } } }
        val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
        val dismiss = { isPickerVisible = false }
        DatePickerDialog(
            onDismissRequest = dismiss,
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                        dismiss()
                    },
                ) { Text(stringResource(Res.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) }
            },
        ) {
            DatePicker(
                state = state,
                dateFormatter = dateFormatter,
                title = {
                    Text(
                        modifier = Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp)),
                        text = stringResource(Res.string.setlists_pick_date),
                    )
                },
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
}

private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * The title and the artist of a song about to be created. Only the title is required: it is what the file is named
 * after, and a song without a known artist is a normal thing to have.
 */
@Composable
private fun NewSongDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, artist: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    val isValid = title.isNotBlank()
    val focusRequester = rememberFirstFieldFocusRequester()
    val confirmOnce = rememberSingleConfirmation()
    val create = { if (isValid) confirmOnce { onCreate(title, artist) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.songs_new_song)) },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = title,
                    onValueChange = { title = it.asSingleLine().take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_title)) },
                    singleLine = true,
                    // Sentences rather than words for the title: only English capitalizes every word of one, and a
                    // letter the keyboard raised is one more to correct in every other language. An artist is a name.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = artist,
                    onValueChange = { artist = it.asSingleLine().take(MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(Res.string.songs_new_song_artist)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = create,
            ) { Text(stringResource(Res.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * Every tag of a song, managed in one place: the library's tags as a checklist with the song's own ticked and at the
 * top, and a field that narrows the list and creates a tag the library does not have yet. As with the languages
 * ([SongLanguagesDialog]), the whole set is written when the dialog is confirmed, so a file the user owns is rewritten
 * once rather than once per checkbox, and the order is decided as the dialog opens rather than by what is ticked: a row
 * that moved under the finger that has just ticked it would be worse than a list that has to be scrolled.
 *
 * Offering the library's tags before anything is typed is the point of the list, because a library where the same idea
 * is filed under "christmas", "Christmas" and "xmas" is a library whose tags filter nothing. For the same reason what is
 * typed ticks the tag it spells, whatever its case, and a tag is only created where there is none to tick.
 *
 * It names the song it tags under its title ([SubjectDialogTitle]) wherever it was opened from: a tag put on the row next
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
    // Saved, since the dialog outlives a recreated Activity and Done writes whatever is ticked at that moment.
    var selectedTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(dialog.song.tags) }
    var createdTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    // The song's own tags first, in the order its file lists them, then the ones created here, then the rest of the
    // library in the order the filter shows them. Two spellings of a tag are one tag, and the song's own spelling is the
    // one kept.
    val offeredTags = remember(dialog.song, createdTags, libraryTags, sortingMode) {
        (dialog.song.tags + createdTags + libraryTags.orderedBy(sortingMode).map { it.name }).distinctBy { it.lowercase() }
    }
    val searchableTags = remember(offeredTags) { offeredTags.map { it to viewModel.normalizeForSearch(it) } }
    val matches = remember(searchableTags, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        searchableTags.mapNotNull { (tag, name) -> tag.takeIf { normalizedQuery in name } }
    }
    val typedTag = query.trim()
    val spelledTag = offeredTags.firstOrNull { it.equals(typedTag, ignoreCase = true) }
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val enterTypedTag = {
        when {
            typedTag.isEmpty() -> keyboardController?.hide()
            spelledTag != null -> if (spelledTag !in selectedTags) selectedTags = selectedTags + spelledTag
            else -> {
                createdTags = createdTags + typedTag
                selectedTags = selectedTags + typedTag
            }
        }
        query = ""
    }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = {
            SubjectDialogTitle(
                title = stringResource(Res.string.song_details_tags_manage),
                subtitle = songLabel(dialog.song),
                action = {
                    LabelSortingToggle(
                        sortingMode = sortingMode,
                        onSortingModeSelected = viewModel::setTagSortingMode,
                    )
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.asSingleLine().take(MAX_TAG_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_tags_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // Handling Done keeps the keyboard up, which is what lets the next tag be typed straight away.
                    keyboardActions = KeyboardActions(onDone = { enterTypedTag() }),
                )
                val isCreatable = typedTag.isNotEmpty() && spelledTag == null
                if (isCreatable || matches.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    HideKeyboardWhenScrolledDown(listState)
                    ScrollToStartWhenChanged(
                        listState = listState,
                        key = sortingMode,
                        contents = matches,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .reachingDialogEdges()
                            .heightIn(max = MAX_CHECKLIST_HEIGHT)
                            .fadingVerticalEdges(listState),
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
                        items(
                            items = matches,
                            key = { "tag:$it" },
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
        confirmButton = {
            TextButton(
                onClick = {
                    // A tag typed but not entered yet is still one the user meant to put on the song, spelled as the library
                    // spells it.
                    val tags = when {
                        typedTag.isEmpty() -> selectedTags
                        spelledTag != null -> if (spelledTag in selectedTags) selectedTags else selectedTags + spelledTag
                        else -> selectedTags + typedTag
                    }
                    viewModel.setSongTags(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, tags = tags, offeredTags = offeredTags)
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(Res.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The title of a dialog about one song or one setlist, with that song or setlist named under it the way a sheet's
 * [SheetHeader] names it, wherever the dialog was opened from: one opened from a row of a list would otherwise not say
 * which row it is about. The title itself is the label of the entry that opened the dialog, so that what was tapped is
 * what comes up.
 *
 * @param subtitle What the dialog acts on: [songLabel], or the setlist's title.
 * @param action A small control about the dialog's list as a whole, at the end of the title's row.
 */
@Composable
internal fun SubjectDialogTitle(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    action?.invoke()
}

/**
 * The languages of one song, asked about all at once: the whole set is written when the dialog is confirmed, so a
 * file the user owns is rewritten once rather than once per checkbox.
 *
 * The list is ordered by the name the platform gives each language in the language the app is set to (see
 * [languageName]), with the ones the song already declares held at the top for as long as the dialog is open: a row
 * that reordered itself under the finger that has just ticked it would be worse than a list that has to be scrolled.
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
    // Saved, since the dialog outlives a recreated Activity and Done writes whatever is ticked at that moment.
    var selectedCodes by rememberSaveable(
        dialog.song.fileName,
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(dialog.song.languages.toSet()) }
    val languages = remember(dialog.song, libraryLanguages, appLanguageCode, sortingMode) {
        val declared = dialog.song.languages
        val libraryCodes = libraryLanguages.map { it.code }.filterNot { it == SongLanguage.UNKNOWN }
        val pickable = pickableLanguages(appLanguageCode = appLanguageCode, alsoOffer = declared + libraryCodes, normalize = viewModel::normalize)
        // The song's own languages come first and stay there, in the order the file lists them, whichever order the
        // rest are in.
        val leading = when (sortingMode) {
            UserPreferences.LabelSortingMode.BY_USAGE -> (declared + libraryCodes).distinct()
            UserPreferences.LabelSortingMode.ALPHABETICAL -> declared
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
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog,
        title = {
            SubjectDialogTitle(
                title = stringResource(Res.string.song_details_languages_edit),
                subtitle = songLabel(dialog.song),
                action = {
                    LabelSortingToggle(
                        sortingMode = sortingMode,
                        onSortingModeSelected = viewModel::setLanguageSortingMode,
                    )
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_language_search)) },
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
                        key = sortingMode,
                        contents = matches,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .reachingDialogEdges()
                            .heightIn(max = MAX_CHECKLIST_HEIGHT)
                            .fadingVerticalEdges(listState),
                        state = listState,
                    ) {
                        items(
                            items = matches,
                            key = { it.code },
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
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setSongLanguages(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, codes = selectedCodes.toList())
                    viewModel.dismissDialog()
                },
            ) { Text(stringResource(Res.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDialog) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

/**
 * The setlists one song can be put into, and the way to make a new one. Naming that new setlist happens in a dialog
 * on top of the sheet rather than instead of it: the setlist is only being created so that this song can go into it,
 * so the sheet staying where it is, with a ticked row appearing in it, is what says that it worked.
 *
 * A library that holds no setlists at all skips the sheet and asks for the name of the first one straight away,
 * since a sheet offering nothing to tick is one tap in the way of the only thing that can be done there. Whether
 * that is what happened is decided once, as the dialog opens ([isCreatingFirstSetlist]) rather than read from the
 * list as it stands: creating the setlist fills the list, and the sheet must not slide in behind a dialog that is
 * on its way out. It is also what the naming dialog is closed by, since there is nothing behind it to return to.
 */
@Composable
private fun SetlistPicker(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SetlistPicker,
) {
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // An archived setlist has been put away, so it is not offered here - unless the song is in it already, which is
    // the only thing this sheet could still have to say about one. In the order the setlists screen is sorted by,
    // which the sheet's own sort button changes as well.
    val pickableSetlists = remember(setlists, dialog.song.fileName) {
        setlists.filter { setlist -> !setlist.isArchived || setlist.entries.any { it.songFileName == dialog.song.fileName } }
    }
    // Answered by the title or the description, the way the setlists screen's own search answers, but not by the
    // songs inside: the song this sheet is about is the only one that matters here.
    val matches = remember(pickableSetlists, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSetlists.filter { setlist ->
            normalizedQuery in viewModel.normalizeForSearch(setlist.title) || normalizedQuery in viewModel.normalizeForSearch(setlist.description)
        }
    }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isCreatingFirstSetlist = rememberSaveable { setlists.isEmpty() }
    var isNamingNewSetlist by rememberSaveable { mutableStateOf(isCreatingFirstSetlist) }
    val closeNamingDialog = { if (isCreatingFirstSetlist) viewModel.dismissDialog() else isNamingNewSetlist = false }
    if (!isCreatingFirstSetlist) {
        CampfireBottomSheet(
            title = stringResource(Res.string.songs_setlist_assignments),
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
                sortingMode = userPreferences?.setlistSortingMode,
                contents = matches,
                noResultsText = if (matches.isEmpty() && query.isNotBlank()) stringResource(Res.string.setlists_no_search_results) else null,
            ) { listState ->
                items(
                    items = matches,
                    key = { it.fileName },
                ) { setlist ->
                    CheckboxListItem(
                        modifier = listItemAnimation(listState),
                        title = setlist.title,
                        isChecked = setlist.entries.any { it.songFileName == dialog.song.fileName },
                        isEnabled = setlist.fileName != dialog.setlistFileName,
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
                closeNamingDialog()
            },
        )
    }
}

/**
 * Every song in the library with a box each, which is how a setlist is filled from its own side: the setlist picker
 * puts one song into any number of setlists, and this puts any number of songs into one setlist.
 *
 * What is ticked is held here rather than read back from the setlist, and the whole of it is written on every tick
 * ([CampfireViewModel.setSetlistSongs]): a list of songs is ticked faster than a write comes back through the
 * library, and boxes that followed the library would each flick back for the length of a round trip. A song ticked
 * here goes to the end of the setlist, so a setlist built from nothing is in the order its songs were picked, and an
 * entry whose file has gone missing stays in it, since it is not listed here to be unticked.
 *
 * The songs the setlist already holds come first, in the setlist's own order, and stay there for as long as the sheet
 * is open for the reason the language picker keeps its own at the top: a row that jumped away from under the finger
 * that had just ticked it would be worse than a list that has to be scrolled. The rest are in the order the songs screen
 * is sorted by, which the sheet's own sort button changes as well.
 *
 * The list can be narrowed by the library's languages and tags as well as by the search ([PickerFilters]). Those are
 * the picker's own and start empty every time rather than following the songs screen's filters: what that screen is
 * narrowed to is a view somebody set up to browse, and a setlist is filled from the whole library.
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
    // with: the dialog outlives a recreated Activity, and a selection seeded again from that snapshot would drop
    // every song ticked before it from the next write.
    val initialSongFileNames = rememberSaveable(setlist.fileName) { setlist.entries.map { it.songFileName }.distinct() }
    var selectedSongFileNames by rememberSaveable(setlist.fileName) { mutableStateOf(initialSongFileNames) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedTags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedLanguages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Sorted, normalized for the search and counted for the chips by the view model, once per library rather than as
    // the sheet opens or on every keystroke, since the search runs over every song on every character typed and the
    // sheet's first frames are its slide up.
    val pickerSongs by viewModel.pickerSongs.collectAsStateWithLifecycle()
    val pickableSongs = remember(pickerSongs, initialSongFileNames) {
        val initial = initialSongFileNames.toSet()
        initialSongFileNames.mapNotNull { pickerSongs.byFileName[it] } + pickerSongs.list.filterNot { it.song.fileName in initial }
    }
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
        val normalizedQuery = viewModel.normalizeForSearch(query)
        pickableSongs.filter { pickableSong ->
            (normalizedQuery in pickableSong.title || normalizedQuery in pickableSong.artist ||
                    pickableSong.searchableTags.any { normalizedQuery in it }) &&
                    (activeTags.isEmpty() || activeTags.any { it in pickableSong.tags }) &&
                    (activeLanguages.isEmpty() || activeLanguages.any { it in pickableSong.languages })
        }
    }
    CampfireBottomSheet(
        title = stringResource(Res.string.setlists_song_assignments),
        subtitle = setlist.title,
        actions = { SongSortMenu(viewModel = viewModel) },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        PickerSearchField(
            modifier = Modifier.focusRequester(rememberFirstFieldFocusRequester()),
            query = query,
            placeholder = stringResource(Res.string.songs_search),
            onQueryChange = { query = it },
        )
        PickerFilters(
            filters = filters,
            selectedTags = activeTags,
            selectedLanguages = activeLanguages,
            tagSortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            languageSortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            onTagClicked = { tag -> selectedTags = if (tag in activeTags) selectedTags - tag else selectedTags + tag },
            onLanguageClicked = { code -> selectedLanguages = if (code in activeLanguages) selectedLanguages - code else selectedLanguages + code },
            onTagSortingModeSelected = viewModel::setTagSortingMode,
            onLanguageSortingModeSelected = viewModel::setLanguageSortingMode,
        )
        PickerList(
            contentPadding = contentPadding,
            sortingMode = userPreferences?.sortingMode,
            contents = matches,
            noResultsText = when {
                // Reached from an empty setlist's "Add songs" in an empty library, which the setlists screen lists as well.
                songs.isEmpty() -> stringResource(Res.string.songs_empty_title)
                matches.isEmpty() && (query.isNotBlank() || isFiltered) -> stringResource(Res.string.songs_no_search_results)
                else -> null
            },
        ) { listState ->
            items(
                items = matches,
                key = { it.song.fileName },
            ) { pickableSong ->
                val fileName = pickableSong.song.fileName
                CheckboxListItem(
                    modifier = listItemAnimation(listState),
                    title = pickableSong.song.title,
                    description = pickableSong.song.artist.ifBlank { null },
                    isChecked = fileName in selectedSongFileNames,
                    onCheckedChange = { isChecked ->
                        selectedSongFileNames = if (isChecked) selectedSongFileNames + fileName else selectedSongFileNames - fileName
                        viewModel.setSetlistSongs(setlistFileName = setlist.fileName, songFileNames = selectedSongFileNames)
                    },
                )
            }
        }
    }
}

/**
 * The tags of the library as a row of chips under the [SongPicker]'s search field, and its languages as a second row
 * under them, each where there are any to offer. Rows that scroll sideways rather than the wrapping groups of the songs
 * screen's filters: a library can carry a hundred tags, and the sheet is there for the songs under them, which a
 * wrapping block of chips would push off the screen. The languages carry their mark the way they do under a song in
 * the lists, since neither row has a section title to name it.
 *
 * The list's rows fade out under them as they scroll up. A library with nothing to filter by gets neither row, and its
 * list starts right under the search field.
 *
 * Selected chips stay where they are rather than moving to the front, for the reason the picker's rows do: a chip that
 * jumped away from under the finger that had just tapped it would have to be found again to be turned off. The order
 * they are in is the one the songs screen's filters show them in, and each row starts with the same toggle that
 * switches it ([SortableChipRow]).
 */
@Composable
private fun PickerFilters(
    modifier: Modifier = Modifier,
    filters: PickerFilterOptions,
    selectedTags: Set<String>,
    selectedLanguages: Set<String>,
    tagSortingMode: UserPreferences.LabelSortingMode,
    languageSortingMode: UserPreferences.LabelSortingMode,
    onTagClicked: (String) -> Unit,
    onLanguageClicked: (String) -> Unit,
    onTagSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    onLanguageSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) {
    if (filters.languages.isEmpty() && filters.tags.isEmpty()) return
    val appLanguageCode = currentLanguage.value.code
    val tags = remember(filters.tags, tagSortingMode) { filters.tags.orderedBy(tagSortingMode) }
    val languages = remember(filters.languages, languageSortingMode, appLanguageCode) {
        filters.languages.orderedBy(languageSortingMode) { code -> languageName(code = code, appLanguageCode = appLanguageCode) ?: code.uppercase() }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(top = CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        if (filters.tags.isNotEmpty()) {
            SortableChipRow(
                items = tags,
                key = { "tag_${it.name.lowercase()}" },
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
                key = { "language_${it.code}" },
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

/**
 * The search field of a picker sheet. The setlist picker leaves it unfocused as the sheet opens, since the handful of
 * setlists under it is what that sheet is opened for and is ticked by sight. The song picker opens with the caret in
 * it, since a song is looked for in a library of hundreds by typing its name; the keyboard that comes up with it goes
 * away again as soon as the list is scrolled down ([HideKeyboardWhenScrolledDown] in [PickerList]).
 */
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
        // Always given a slot, with the button fading in and out of it, so that the text is not pushed around by
        // the slot itself appearing with the first character.
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_clear),
                        contentDescription = stringResource(Res.string.songs_clear),
                    )
                }
            }
        },
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
 * It never gets shorter while the sheet is open, only taller: narrowing the list by a search would otherwise shrink
 * the sheet with every character typed, moving the field being typed into down the screen along with it.
 *
 * @param contentPadding What the sheet's content keeps clear at the bottom, applied inside the scroll so that the last
 *   rows pass under the navigation bar and the keyboard on their way up.
 * @param sortingMode The order the rows are in, a change of which sends the list back to its first row once the
 *   reordered [contents] arrive ([ScrollToStartWhenChanged]).
 * @param noResultsText What to say in place of the rows, null while there is nothing to say.
 */
@Composable
private fun ColumnScope.PickerList(
    contentPadding: PaddingValues,
    sortingMode: Any?,
    contents: Any?,
    noResultsText: String?,
    content: LazyListScope.(LazyListState) -> Unit,
) {
    val density = LocalDensity.current
    var tallestHeight by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    HideKeyboardWhenScrolledDown(listState)
    ScrollToStartWhenChanged(
        listState = listState,
        key = sortingMode,
        contents = contents,
    )
    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .heightIn(min = with(density) { tallestHeight.toDp() })
            .onSizeChanged { tallestHeight = maxOf(tallestHeight, it.height) }
            // The rows fade out as they scroll up under the search field and the filter chips, which is the edge
            // between the two everywhere else in the app too.
            .fadingTopEdge(listState),
        state = listState,
        // The gap under the search field is the list's own content padding rather than a padding around the list, so
        // that a scrolled row goes under the field itself instead of being cut off a few pixels short of it.
        contentPadding = PaddingValues(
            top = PICKER_LIST_TOP_PADDING,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
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
 * Every sheet of the app, drawn edge to edge: the sheet runs down under the navigation bar and the keyboard instead
 * of stopping above them, and only its content is kept clear of them. [ModalBottomSheet] pads the whole content by
 * the bottom inset by default, which leaves a scrolling list ending on a band of the sheet's color above the bar
 * rather than scrolling on under it, so that inset is left out of the sheet's own insets and handed to the content
 * instead, as the `contentPadding` scrolling content applies inside its scroll and the rest leaves under its last row.
 * The top inset stays with the sheet, which only pads by it once it has been dragged up against the status bar.
 *
 * @param title What the sheet is about, named in its [SheetHeader].
 * @param subtitle What the sheet acts on, under [title]: the song or the setlist it was opened for. Left out when blank.
 * @param actions Buttons at the end of the [SheetHeader], across from the close button, such as the order of a list.
 * @param onDismiss Has to dismiss this sheet's own dialog and nothing else (`CampfireViewModel.dismissSheet`): it is
 *   called from the end of a hide animation, by which time another dialog may have taken the sheet's place.
 * @param content Can close the sheet the way its close button does ([BottomSheetContentScope.close]), for a sheet
 *   with a button of its own that is done with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CampfireBottomSheet(
    title: String,
    subtitle: String = "",
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    actions: (@Composable RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    content: @Composable BottomSheetContentScope.(contentPadding: PaddingValues) -> Unit,
) {
    // No partially expanded state: these sheets are short, and they open at their full height.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        // In the light theme the screens' own background rather than Material's surface container, so a sheet reads as
        // part of the app. In the dark one that background is too close to the scrim to tell the sheet from the screen
        // it covers, so the sheet keeps Material's lighter container there. The scheme on screen is asked rather than the
        // preference, so the sheet follows the theme's fade like everything else.
        containerColor = MaterialTheme.colorScheme.background.let { background ->
            if (background.luminance() < 0.5f) BottomSheetDefaults.ContainerColor else background
        },
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
    ) {
        // Hiding the sheet by hand does not count as dismissing it, so the dialog state is cleared once it is gone: left
        // as it was, the invisible sheet's modal layer would stay over the screen, swallowing the next tap. Only a hide
        // that ran to its end counts: one cut short by a finger taking hold of the sheet leaves the sheet where Material
        // settles it, and one cut short by another dialog replacing the sheet has nothing left to dismiss.
        val close = { coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { cause -> if (cause == null) onDismiss() }; Unit }
        SheetHeader(
            title = title,
            subtitle = subtitle,
            actions = actions,
            onClose = close,
        )
        // Read inside the sheet, which is a window of its own on Android and gets the insets of that window.
        val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        val density = LocalDensity.current
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
        ).content(PaddingValues(bottom = bottomInset + SHEET_BOTTOM_PADDING))
    }
}

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
    actions: (@Composable RowScope.() -> Unit)?,
    onClose: () -> Unit,
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
    actions?.invoke(this)
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
private val MAX_CHECKLIST_HEIGHT = 320.dp

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
 * What a song says about itself beyond how it is played, read from its text as it is now. Outside performance mode every
 * group is there with a button that edits it, which opens that group's dialog in place of the sheet; in performance
 * mode, which edits nothing, only what the song has.
 */
@Composable
private fun SongInfoSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongInfo,
    urlOpener: (String) -> Unit,
) = CampfireBottomSheet(
    title = stringResource(Res.string.song_details_song_info),
    subtitle = songLabel(dialog.song),
    onDismiss = { viewModel.dismissSheet(dialog) },
) { contentPadding ->
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val text = songTexts[dialog.song.fileName]
    val metadata = remember(text) { text?.let(viewModel::songMetadataOf) }
    val editing = rememberSongInfoEditing(viewModel = viewModel, song = dialog.song, isEditorDraft = false)
    if (metadata != null) {
        val scrollState = rememberScrollState()
        SongInfoBody(
            modifier = Modifier
                .weight(1f, fill = false)
                .fadingVerticalEdges(scrollState)
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(vertical = 8.dp),
            metadata = metadata,
            horizontalPadding = 16.dp,
            editing = editing.takeUnless { isPerformanceModeEnabled },
            onOpenLink = urlOpener,
        )
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
