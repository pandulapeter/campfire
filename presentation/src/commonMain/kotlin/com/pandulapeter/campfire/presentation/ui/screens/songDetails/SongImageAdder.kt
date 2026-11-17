/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.ui.pictures.MissingImageAdder
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.pickPictures
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore

/**
 * What a page's missing pictures' Add image and Download do (`LocalSongImageAdder`): picks or downloads one picture and
 * stores it under the song [fileName] ([SongTextStore.addMissingImage], [SongTextStore.downloadMissingImage]). Null where
 * the song may not be written ([isWritable] false: read only mode, or a preview's file that is not written yet), which
 * hides the buttons.
 */
@Composable
internal fun rememberSongImageAdder(songTextStore: SongTextStore, fileName: String, isWritable: Boolean): MissingImageAdder? {
    val filePicker = LocalFilePicker.current
    return if (isWritable) {
        remember(songTextStore, fileName, filePicker) {
            MissingImageAdder(
                canPickPhotos = filePicker.canPickPhotos,
                add = { name, source ->
                    songTextStore.addMissingImage(fileName = fileName, missingName = name, pickFiles = { filePicker.pickPictures(source, isMultiple = false) })
                },
                download = { name -> songTextStore.downloadMissingImage(fileName = fileName, missingName = name) },
            )
        }
    } else {
        null
    }
}
