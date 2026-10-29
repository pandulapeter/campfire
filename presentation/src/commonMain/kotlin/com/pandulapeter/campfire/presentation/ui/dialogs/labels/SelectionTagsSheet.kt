/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.labels

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.songs_selection_count
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The tags of the songs ticked in the selection mode ([TagsSheet]), subtitled with how many there are. Save puts the
 * tags turned on onto every one of them and takes the ones turned off off every one of them; a row left half ticked
 * is left alone in every file. A song that left the library while the sheet was open is skipped.
 */
@Composable
internal fun SelectionTagsSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SelectionTags,
) {
    val songsByFileName by viewModel.libraryState.songsByFileName.collectAsStateWithLifecycle()
    val tagsPerSong = remember(dialog) { dialog.songFileNames.mapNotNull { songsByFileName[it]?.tags } }
    TagsSheet(
        viewModel = viewModel,
        tagsPerSong = tagsPerSong,
        sheetKey = dialog.songFileNames.joinToString(),
        subtitle = pluralTextResource(Res.plurals.songs_selection_count, dialog.songFileNames.size, dialog.songFileNames.size.toString()),
        confirmLabel = stringResource(Res.string.save),
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
        onSave = { draft, spellings ->
            viewModel.selectionEditing.setSelectionTags(
                fileNames = dialog.songFileNames,
                added = draft.added.mapNotNull(spellings::get),
                removed = draft.removed,
            )
        },
    )
}
