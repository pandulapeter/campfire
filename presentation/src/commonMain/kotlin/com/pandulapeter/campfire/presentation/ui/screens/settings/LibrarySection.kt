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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_demo_songs
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.settings_export_all
import com.pandulapeter.campfire.presentation.resources.settings_import
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache
import com.pandulapeter.campfire.presentation.resources.settings_library_cover_art_cache_clear
import com.pandulapeter.campfire.presentation.resources.settings_library_delete
import com.pandulapeter.campfire.presentation.resources.settings_library_size
import com.pandulapeter.campfire.presentation.resources.settings_library_size_bytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.resources.settings_library_size_gigabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_kilobytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_megabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_storage
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_offline_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_offline_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_online_only
import com.pandulapeter.campfire.presentation.resources.settings_library_summary
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isAppAvailableOffline
import org.jetbrains.compose.resources.painterResource

/**
 * What the library holds and where it is, then what can be done with the whole of it. The two rows that say what is
 * on the device are also how it is taken off: the library's row deletes every song and setlist, behind a dialog that
 * wants a word typed, and the cover cache's row deletes the copies of the covers, behind an ordinary one.
 *
 * The two actions are disabled rather than hidden by performance mode, like the chord spelling with the chords
 * switched off: this screen is the one place the mode can be switched back off, and a settings screen whose rows come and go
 * with a switch on it is one nobody can find their way around.
 */
@Composable
internal fun LibrarySection(
    viewModel: CampfireViewModel,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection {
    // Null until the library has been read, so that the row arrives with real counts instead of showing zeroes.
    val librarySummary by viewModel.librarySummary.collectAsStateWithLifecycle()
    val demoLibraryOffer by viewModel.demoLibraryOffer.collectAsStateWithLifecycle()
    val libraryPersistence by viewModel.libraryPersistence.collectAsStateWithLifecycle()
    val coverArtCacheSize by viewModel.coverArtCacheSize.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    AnimatedSettingsRow(value = librarySummary) { summary ->
        DeletableSettingsRow(
            title = stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount),
            description = stringResource(Res.string.settings_library_size, formattedSize(summary.size)),
            deleteLabel = stringResource(Res.string.settings_library_delete),
            isEnabled = summary.songCount + summary.setlistCount > 0 && !isImporting && !isPerformanceModeEnabled,
            onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteLibrary) },
        )
    }
    // Not while there is nothing in it: an empty cache says nothing a library without covers does not.
    AnimatedSettingsRow(value = coverArtCacheSize?.takeIf { it > 0 }) { size ->
        DeletableSettingsRow(
            title = stringResource(Res.string.settings_library_cover_art_cache),
            description = stringResource(Res.string.settings_library_size, formattedSize(size)),
            deleteLabel = stringResource(Res.string.settings_library_cover_art_cache_clear),
            isEnabled = !isPerformanceModeEnabled,
            onClick = { viewModel.showDialog(CampfireViewModel.DialogType.ClearCoverArtCache) },
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
    ActionListItem(
        title = stringResource(Res.string.settings_import),
        icon = painterResource(Res.drawable.ic_import),
        isEnabled = !isImporting && !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { viewModel.importFiles(filePicker) },
    )
    // Only until they are all in the library, which is also what brings it back for the ones a user who wanted none
    // of them has deleted. Null while the library is still being read, so the offer never appears for a moment over
    // a library that turns out to hold them.
    AnimatedSettingsRow(value = demoLibraryOffer) { offer ->
        ActionListItem(
            title = stringResource(Res.string.add_demo_songs),
            icon = painterResource(Res.drawable.ic_songs),
            isEnabled = offer == CampfireViewModel.DemoLibraryOffer.AVAILABLE && !isPerformanceModeEnabled,
            isEmphasized = false,
            onClick = viewModel::importDemoLibrary,
        )
    }
    ActionListItem(
        title = stringResource(Res.string.settings_export_all),
        icon = painterResource(Res.drawable.ic_export),
        isEnabled = !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { viewModel.exportLibrary(filePicker) },
    )
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
    description: String,
    deleteLabel: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = ListItem(
    modifier = Modifier.clickable(enabled = isEnabled, onClickLabel = deleteLabel, onClick = onClick).alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = { Text(description) },
    trailingContent = { Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = deleteLabel) },
)

/**
 * [bytes] in the largest unit that keeps the number at least one, in decimal units as the file browsers of Android and
 * Apple count them. One decimal below ten and none above, which is as precise as a number that changes with every
 * saved song is worth being. The separator comes from the strings rather than from the platform, so that it follows
 * the language chosen in the app like the words around it.
 */
@Composable
private fun formattedSize(bytes: Long): String {
    // The unit is settled on the rounded number, so that 999 960 bytes read as 1.0 MB rather than as 1000 KB.
    val unit = (1..SIZE_UNITS.lastIndex).firstOrNull { unit -> (bytes + SIZE_STEP.pow(unit) / 2) / SIZE_STEP.pow(unit + 1) == 0L }
        ?: SIZE_UNITS.lastIndex
    val divisor = SIZE_STEP.pow(unit)
    val tenths = (bytes * 10 + divisor / 2) / divisor
    val number = when {
        bytes < SIZE_STEP -> bytes.toString()
        tenths < 100 -> "${tenths / 10}${stringResource(Res.string.settings_library_size_decimal_separator)}${tenths % 10}"
        else -> ((bytes + divisor / 2) / divisor).toString()
    }
    return stringResource(if (bytes < SIZE_STEP) SIZE_UNITS.first() else SIZE_UNITS[unit], number)
}

private fun Long.pow(exponent: Int) = (1..exponent).fold(1L) { result, _ -> result * this }

private const val SIZE_STEP = 1000L

private val SIZE_UNITS = listOf(
    Res.string.settings_library_size_bytes,
    Res.string.settings_library_size_kilobytes,
    Res.string.settings_library_size_megabytes,
    Res.string.settings_library_size_gigabytes,
)
