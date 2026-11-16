/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.domain.api.images.GetImageChangesUseCase
import com.pandulapeter.campfire.domain.api.images.GetImageSizeUseCase
import com.pandulapeter.campfire.domain.api.images.GetImageUseCase
import com.pandulapeter.campfire.domain.api.images.ReadImageSizeUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import org.koin.core.annotation.Factory

/**
 * The use cases [SongTextStore] keeps its texts in step with the files by, and the ones the pictures those texts name are
 * read and added with, which the view model passes on as one.
 */
@Factory
class SongTextUseCases(
    internal val getSongContentInvalidations: GetSongContentInvalidationsUseCase,
    internal val getImageSize: GetImageSizeUseCase,
    internal val getImage: GetImageUseCase,
    internal val getImageChanges: GetImageChangesUseCase,
    internal val saveSongImage: SaveSongImageUseCase,
    internal val readImageSize: ReadImageSizeUseCase,
)
