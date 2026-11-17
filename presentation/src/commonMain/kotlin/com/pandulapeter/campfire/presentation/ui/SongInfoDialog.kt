/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SongInfoSheet

/** The About the song sheet of [CampfireDialogs], handed the holders of [viewModel] it reads. */
@Composable
internal fun SongInfoDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongInfo,
    onDismiss: () -> Unit,
    urlOpener: (String) -> Unit,
) = SongInfoSheet(
    libraryState = viewModel.libraryState,
    songTextsFlow = viewModel.songTextStore.songTexts,
    editorSession = viewModel.editorSession,
    preferencesController = viewModel.preferencesController,
    backStack = viewModel.navigator.backStack,
    songMetadataEditing = viewModel.songMetadataEditing,
    songAttachmentEditing = viewModel.songAttachmentEditing,
    songRenderer = viewModel.songRenderer,
    playingOverrides = viewModel.overrides,
    onDismiss = onDismiss,
    dialog = dialog,
    urlOpener = urlOpener,
)
