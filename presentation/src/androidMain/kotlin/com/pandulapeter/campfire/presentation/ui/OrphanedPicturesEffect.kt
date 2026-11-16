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
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.AndroidFilePicker

/**
 * Puts pictures picked for a view model that is gone (`AndroidFilePicker.orphanedPictures`) where they were most likely
 * meant to go: into the editor, where it is the screen on top of the restored back stack, since its Image button is the
 * only way to pick pictures there. Anywhere else (a missing picture's Add image on a song's page, whose name is not known
 * any more, or a screen the user has since left) they are let go rather than guessed at, and the user picks again.
 */
@Composable
internal fun OrphanedPicturesEffect(filePicker: AndroidFilePicker, viewModel: CampfireViewModel) = LaunchedEffect(filePicker, viewModel) {
    filePicker.orphanedPictures.collect { pictures ->
        val editor = viewModel.navigator.backStack.lastOrNull() as? CampfireDestination.SongEditor
        if (editor != null && !viewModel.preferencesController.isPerformanceModeEnabled.value) {
            viewModel.editorSession.images.pickImages(editor.fileName) { pictures }
        } else {
            println("Let go of ${pictures.size} picture(s) picked for a screen that is gone.")
        }
    }
}
