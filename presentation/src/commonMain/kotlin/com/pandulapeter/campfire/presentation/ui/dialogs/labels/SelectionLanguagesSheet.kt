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
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.songs_selection_count
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The languages of the songs ticked in the selection mode ([LanguagesSheet]), saved the way [SelectionTagsSheet] saves
 * their tags: each song keeps its own languages, less the ones turned off and with the ones turned on after them.
 */
@Composable
internal fun SelectionLanguagesSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SelectionLanguages,
) {
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val songs = remember(dialog) { dialog.songFileNames.mapNotNull { songsByFileName[it] } }
    LanguagesSheet(
        viewModel = viewModel,
        songs = songs,
        sheetKey = dialog.songFileNames.joinToString(),
        subtitle = pluralStringResource(Res.plurals.songs_selection_count, dialog.songFileNames.size, dialog.songFileNames.size),
        confirmLabel = stringResource(Res.string.save),
        onDismiss = { viewModel.dismissSheet(dialog) },
        onSave = { draft -> viewModel.setSelectionLanguages(fileNames = dialog.songFileNames, added = draft.added, removed = draft.removed) },
    )
}
