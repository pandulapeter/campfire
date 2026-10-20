/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_report_converted
import com.pandulapeter.campfire.presentation.resources.import_review_repeated
import com.pandulapeter.campfire.presentation.resources.import_review_setlist_songs
import com.pandulapeter.campfire.presentation.resources.import_review_setlist_songs_in_import
import com.pandulapeter.campfire.presentation.resources.import_review_will_be_numbered
import com.pandulapeter.campfire.presentation.resources.import_review_will_replace
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * One entry of the review. A row with a box is ticked or unticked by a tap anywhere on it, and a name already taken
 * carries its own choice at its end; a row without one (already in the library, or a file that cannot be imported) is
 * only read, and opens the library's song where it names one while the songs are switched on.
 *
 * Each row reads only its own tick and choice out of [choices], so that a tap recomposes the row it was made on and
 * the summary rather than the whole list.
 */
@Composable
internal fun ImportReviewRowItem(
    modifier: Modifier = Modifier,
    row: ImportReviewRow,
    isConflict: Boolean,
    choices: () -> ImportChoices,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
    onOpenLibrarySong: ((String) -> Unit)?,
) {
    val key = row.key
    if (key == null) {
        ImportReviewInfoRow(
            modifier = modifier,
            row = row,
            onClick = onOpenLibrarySong?.let { open -> row.openableLibraryFileName?.let { fileName -> { open(fileName) } } },
        )
        return
    }
    val isChosen by remember(key) { derivedStateOf { choices().isChosen(key) } }
    val resolution by remember(key) { derivedStateOf { choices().resolutionOf(key) } }
    val libraryName = row.libraryTitle ?: row.libraryFileName.orEmpty()
    val description = listOfNotNull(
        when {
            !isConflict -> null
            resolution == ImportConflictResolution.REPLACE -> textResource(Res.string.import_review_will_replace, libraryName)
            else -> stringResource(Res.string.import_review_will_be_numbered)
        },
        row.setlistSongCount?.let { count -> pluralStringResource(Res.plurals.import_review_setlist_songs, count, count) },
        // Said only where it differs: a setlist whose songs all arrive with it has nothing to warn about.
        row.setlistSongsInImport?.takeIf { it != row.setlistSongCount }?.let { stringResource(Res.string.import_review_setlist_songs_in_import, it) },
        if (row.isConverted) stringResource(Res.string.import_report_converted) else null,
        if (row.repeatCount > 0) pluralStringResource(Res.plurals.import_review_repeated, row.repeatCount + 1, row.repeatCount + 1) else null,
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    CheckboxListItem(
        modifier = modifier,
        title = row.headline,
        description = description,
        state = ToggleableState(isChosen),
        trailingContent = if (isConflict) {
            {
                ConflictResolutionDropdown(
                    resolution = resolution,
                    onResolutionSelected = { onResolutionSelected(key, it) },
                    onShowLibrarySong = onOpenLibrarySong?.let { open -> row.openableLibraryFileName?.let { fileName -> { open(fileName) } } },
                )
            }
        } else {
            null
        },
        onClick = { onChosenChange(key, !isChosen) },
    )
}

@Composable
private fun ImportReviewInfoRow(
    modifier: Modifier = Modifier,
    row: ImportReviewRow,
    onClick: (() -> Unit)?,
) = ListItem(
    modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = {
        Text(
            text = row.headline,
            maxLines = 1,
            overflow = if (row.title == null) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
        )
    },
    supportingContent = if (row.repeatCount > 0) {
        { Text(pluralStringResource(Res.plurals.import_review_repeated, row.repeatCount + 1, row.repeatCount + 1)) }
    } else {
        null
    },
)

/** The library's song the row names, where the library still holds it, which is what a tap can open. */
private val ImportReviewRow.openableLibraryFileName get() = libraryFileName?.takeIf { isSong && libraryTitle != null }

/** "Artist - Title" where the header names both, the title alone where it names no artist, and the file name otherwise. */
private val ImportReviewRow.headline
    get() = when {
        title == null -> fileName
        subtitle == null -> title
        else -> "$subtitle - $title"
    }
