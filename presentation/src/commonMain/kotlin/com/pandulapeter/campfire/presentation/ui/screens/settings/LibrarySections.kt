/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.joinIsolated
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_demo_library
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.settings_export_all
import com.pandulapeter.campfire.presentation.resources.settings_group_import_export
import com.pandulapeter.campfire.presentation.resources.settings_group_on_this_device
import com.pandulapeter.campfire.presentation.resources.settings_import
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear
import com.pandulapeter.campfire.presentation.resources.settings_library_delete
import com.pandulapeter.campfire.presentation.resources.settings_library_size
import com.pandulapeter.campfire.presentation.resources.settings_library_size_attachments
import com.pandulapeter.campfire.presentation.resources.settings_library_size_pictures
import com.pandulapeter.campfire.presentation.resources.settings_library_size_setlists
import com.pandulapeter.campfire.presentation.resources.settings_library_size_songs
import com.pandulapeter.campfire.presentation.resources.settings_library_storage
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_offline_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_offline_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_online_only
import com.pandulapeter.campfire.presentation.resources.settings_library_summary
import com.pandulapeter.campfire.presentation.ui.components.formattedSize
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibraryOffer
import com.pandulapeter.campfire.presentation.ui.firstRun.FirstRunController
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isAppAvailableOffline
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.state.LibrarySummary
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource

/**
 * The library tab after its sync section: what can be brought into the library and taken out of it, then what it holds
 * on this device. The rows that say what is on the device are also how it is taken off, last on the page since they
 * are the ones that cannot be undone: the library's row deletes every song and setlist, behind a dialog that wants a
 * word typed, and the cover cache's row deletes the copies of the covers, behind an ordinary one.
 *
 * The actions are disabled rather than hidden by performance mode, like the chord spelling with the chords switched
 * off: this screen is the one place the mode can be switched back off, and a settings screen whose rows come and go
 * with a switch on it is one nobody can find their way around.
 */
@Composable
internal fun LibrarySections(
    firstRunController: FirstRunController,
    showDialog: (DialogType) -> Unit,
    onImportFiles: (FilePicker) -> Unit,
    onExportLibrary: (FilePicker) -> Unit,
    librarySummary: StateFlow<LibrarySummary?>,
    libraryPersistence: StateFlow<LibraryPersistence?>,
    coverArtCacheSize: StateFlow<Long?>,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
) {
    ImportExportSection(
        firstRunController = firstRunController,
        onImportFiles = onImportFiles,
        onExportLibrary = onExportLibrary,
        isImporting = isImporting,
        isPerformanceModeEnabled = isPerformanceModeEnabled,
    )
    OnThisDeviceSection(
        showDialog = showDialog,
        librarySummaryFlow = librarySummary,
        libraryPersistenceFlow = libraryPersistence,
        coverArtCacheSizeFlow = coverArtCacheSize,
        isImporting = isImporting,
        isPerformanceModeEnabled = isPerformanceModeEnabled,
    )
}

@Composable
private fun ImportExportSection(
    firstRunController: FirstRunController,
    onImportFiles: (FilePicker) -> Unit,
    onExportLibrary: (FilePicker) -> Unit,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection(title = stringResource(Res.string.settings_group_import_export)) {
    val demoLibraryOffer by firstRunController.demoLibraryOffer.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    ActionListItem(
        title = stringResource(Res.string.settings_import),
        icon = painterResource(Res.drawable.ic_import),
        isEnabled = !isImporting && !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { onImportFiles(filePicker) },
    )
    // Only until they are all in the library, which is also what brings it back for the ones a user who wanted none
    // of them has deleted. Null while the library is still being read, so the offer never appears for a moment over
    // a library that turns out to hold them.
    AnimatedSettingsRow(value = demoLibraryOffer) { offer ->
        ActionListItem(
            title = stringResource(Res.string.add_demo_library),
            icon = painterResource(Res.drawable.ic_songs),
            isEnabled = offer == DemoLibraryOffer.AVAILABLE && !isPerformanceModeEnabled,
            isEmphasized = false,
            onClick = firstRunController::importDemoLibrary,
        )
    }
    ActionListItem(
        title = stringResource(Res.string.settings_export_all),
        icon = painterResource(Res.drawable.ic_export),
        isEnabled = !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { onExportLibrary(filePicker) },
    )
}

@Composable
private fun OnThisDeviceSection(
    showDialog: (DialogType) -> Unit,
    librarySummaryFlow: StateFlow<LibrarySummary?>,
    libraryPersistenceFlow: StateFlow<LibraryPersistence?>,
    coverArtCacheSizeFlow: StateFlow<Long?>,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection(title = stringResource(Res.string.settings_group_on_this_device)) {
    // Null until the library has been read, so that the row arrives with real counts instead of showing zeroes.
    val librarySummary by librarySummaryFlow.collectAsStateWithLifecycle()
    val libraryPersistence by libraryPersistenceFlow.collectAsStateWithLifecycle()
    val coverArtCacheSize by coverArtCacheSizeFlow.collectAsStateWithLifecycle()
    AnimatedSettingsRow(value = librarySummary) { summary ->
        DeletableSettingsRow(
            title = stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount),
            description = { LibrarySizeDescription(summary) },
            deleteLabel = stringResource(Res.string.settings_library_delete),
            isEnabled = summary.songCount + summary.setlistCount > 0 && !isImporting && !isPerformanceModeEnabled,
            onClick = { showDialog(DialogType.DeleteLibrary) },
        )
    }
    // Not while there is nothing in it: an empty cache says nothing a library without covers does not.
    AnimatedSettingsRow(value = coverArtCacheSize?.takeIf { it > 0 }) { size ->
        DeletableSettingsRow(
            title = stringResource(Res.string.settings_library_cover_art_cache),
            description = { Text(stringResource(Res.string.settings_library_size, formattedSize(size))) },
            deleteLabel = stringResource(Res.string.settings_library_cover_art_cache_clear),
            isEnabled = !isPerformanceModeEnabled,
            onClick = { showDialog(DialogType.ClearCoverArtCache) },
        )
    }
    // Only where the answer is not a foregone conclusion, which is the web: the other three platforms keep the
    // library in a file system of their own. One row for the library and the copy of the app the page keeps next to
    // it, since the browser keeps or evicts the two together, and this is where somebody checks whether the page
    // opens without a connection.
    AnimatedSettingsRow(value = libraryPersistence.takeIf { it != LibraryPersistence.GUARANTEED }) { persistence ->
        val isGranted = persistence == LibraryPersistence.GRANTED
        val isAvailableOffline = isAppAvailableOffline()
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_library_storage)) },
            supportingContent = {
                Text(
                    text = when {
                        isAvailableOffline && isGranted -> stringResource(Res.string.settings_library_storage_offline_granted)
                        isAvailableOffline -> stringResource(Res.string.settings_library_storage_offline_best_effort)
                        else -> stringResource(Res.string.settings_library_storage_online_only) + " " + stringResource(
                            if (isGranted) Res.string.settings_library_storage_granted else Res.string.settings_library_storage_best_effort,
                        )
                    },
                    color = if (isAvailableOffline && isGranted) Color.Unspecified else MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}

/**
 * The library's size on disk, and under it what that is made of once more than one part takes up space, the line coming
 * and going as a row of the section does.
 */
@Composable
private fun LibrarySizeDescription(summary: LibrarySummary) = Column {
    Text(stringResource(Res.string.settings_library_size, formattedSize(summary.size)))
    AnimatedSettingsRow(value = sizeBreakdownOf(summary).takeIf { it.isNotEmpty() }) { breakdown ->
        Text(breakdown.map { (part, size) -> stringResource(part.label, formattedSize(size)) }.joinIsolated(" · "))
    }
}

private val LibraryPart.label
    get() = when (this) {
        LibraryPart.SONGS -> Res.string.settings_library_size_songs
        LibraryPart.SETLISTS -> Res.string.settings_library_size_setlists
        LibraryPart.PICTURES -> Res.string.settings_library_size_pictures
        LibraryPart.ATTACHMENTS -> Res.string.settings_library_size_attachments
    }

/**
 * A row that says how much of something is on the device and deletes it when tapped, which the icon at its end says
 * before anybody taps it. The dialog it opens is the confirmation, so the row itself asks nothing.
 *
 * @param deleteLabel What the icon is called to a screen reader, since the icon is the only thing that names the action.
 */
@Composable
private fun DeletableSettingsRow(
    title: String,
    description: @Composable () -> Unit,
    deleteLabel: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = ListItem(
    modifier = Modifier.clickable(enabled = isEnabled, onClickLabel = deleteLabel, onClick = onClick).alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description,
    trailingContent = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = deleteLabel) },
)
