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

import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.SheetHeader
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * The tags of one song ([TagsSheet]). It names the song it tags under its title ([SheetHeader]) wherever it was opened
 * from: a tag put on the row next to the one that was meant is a file quietly rewritten, and saying it over the song
 * details screen as well keeps the sheet reading the same from both places.
 */
@Composable
internal fun SongTagsDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongTags,
) = TagsSheet(
    viewModel = viewModel,
    songs = listOf(dialog.song),
    sheetKey = dialog.song.fileName,
    subtitle = songLabel(dialog.song),
    confirmLabel = stringResource(if (dialog.target is SongEditTarget.EditorDraft) Res.string.done else Res.string.save),
    onDismiss = { viewModel.dismissSheet(dialog) },
    onSave = { draft, spellings ->
        viewModel.setSongTags(
            target = dialog.target,
            tags = draft.onKeys.mapNotNull(spellings::get),
            offeredTags = spellings.values.toList(),
        )
    },
)
