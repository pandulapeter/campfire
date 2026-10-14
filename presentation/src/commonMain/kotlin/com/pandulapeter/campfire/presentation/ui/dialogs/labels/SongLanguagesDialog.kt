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
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.dialogs.songLabel
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/** The languages of one song ([LanguagesSheet]), named under the sheet's title the way its tags' sheet names it. */
@Composable
internal fun SongLanguagesDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongLanguages,
) = LanguagesSheet(
    viewModel = viewModel,
    songs = listOf(dialog.song),
    sheetKey = dialog.song.fileName,
    subtitle = songLabel(dialog.song),
    confirmLabel = stringResource(if (dialog.target is SongEditTarget.EditorDraft) Res.string.done else Res.string.save),
    onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    onSave = { draft -> viewModel.songMetadataEditing.setSongLanguages(target = dialog.target, codes = draft.onKeys.toList()) },
)
