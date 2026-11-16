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
import com.pandulapeter.campfire.presentation.ui.pictures.OrphanedPicturesPlace
import com.pandulapeter.campfire.presentation.ui.pictures.orphanedPicturesPlace
import com.pandulapeter.campfire.presentation.ui.platform.AndroidFilePicker

/**
 * Puts pictures picked for a view model that is gone (`AndroidFilePicker.orphanedPictures`) where they were meant to go
 * ([orphanedPicturesPlace]): into the editor on top of the restored back stack, or into the missing picture whose Add
 * image picked them, which the view model's saved state remembers. Anywhere else they are let go rather than guessed at,
 * and the user picks again.
 */
@Composable
internal fun OrphanedPicturesEffect(filePicker: AndroidFilePicker, viewModel: CampfireViewModel) = LaunchedEffect(filePicker, viewModel) {
    filePicker.orphanedPictures.collect { pictures ->
        val place = orphanedPicturesPlace(
            top = viewModel.navigator.backStack.lastOrNull(),
            missingImagePick = viewModel.songTextStore.takePendingMissingImagePick(),
            isReadOnly = viewModel.preferencesController.isPerformanceModeEnabled.value,
        )
        when (place) {
            is OrphanedPicturesPlace.Editor -> viewModel.editorSession.images.pickImages(place.fileName) { pictures }
            is OrphanedPicturesPlace.MissingImage -> viewModel.songTextStore.addOrphanedMissingImage(place.pick, pictures)
            OrphanedPicturesPlace.Nowhere -> println("Let go of ${pictures.size} picture(s) picked for a screen that is gone.")
        }
    }
}
