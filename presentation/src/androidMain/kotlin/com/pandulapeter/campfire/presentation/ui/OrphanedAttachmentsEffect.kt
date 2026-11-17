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
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.pictures.OrphanedAttachmentPlace
import com.pandulapeter.campfire.presentation.ui.pictures.orphanedAttachmentPlace
import com.pandulapeter.campfire.presentation.ui.platform.AndroidFilePicker
import kotlinx.coroutines.flow.first

/**
 * Puts a file picked as an attachment for a view model that is gone (`AndroidFilePicker.orphanedAttachments`) where it
 * was meant to go ([orphanedAttachmentPlace]): the Attachments sheet reopened from the saved state, else the editor on
 * top of the restored back stack. Anywhere else it is let go, and a message asks for it again. The sheet is reopened
 * only once the library has been read, so the decision waits for that restoration to settle, never for a set time: the
 * file waits in the picker's channel meanwhile.
 */
@Composable
internal fun OrphanedAttachmentsEffect(filePicker: AndroidFilePicker, viewModel: CampfireViewModel) = LaunchedEffect(filePicker, viewModel) {
    filePicker.orphanedAttachments.collect { file ->
        viewModel.savedDialogRestorer.isSettled.first { it }
        // Read only mode and the Features switch are read from them.
        viewModel.preferencesController.arePreferencesLoaded.first { it }
        val place = orphanedAttachmentPlace(
            top = viewModel.navigator.backStack.lastOrNull(),
            openDialogs = viewModel.dialogHost.openDialogs.value,
            isReadOnly = viewModel.preferencesController.isPerformanceModeEnabled.value,
            areAttachmentsEnabled = viewModel.userPreferences.value?.isAttachmentsEnabled != false,
        )
        when (place) {
            is OrphanedAttachmentPlace.Sheet -> viewModel.songAttachmentEditing.pick(place.target) { file }
            is OrphanedAttachmentPlace.Editor -> viewModel.editorSession.images.attach(place.fileName) { file }
            OrphanedAttachmentPlace.Nowhere -> viewModel.messageSink.sendMessage(Message.AttachmentPickLost)
        }
    }
}
