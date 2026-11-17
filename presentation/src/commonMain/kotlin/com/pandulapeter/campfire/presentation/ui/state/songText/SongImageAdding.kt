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

import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.presentation.ui.pictures.PendingMissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter

/**
 * How [SongTextStore.addMissingImage] stores a picture: made to fit by [fitter] once, then stored with the song naming it
 * by [saveSongImage], which may be tried again on a song changed meanwhile. [pendingPick] holds which missing picture a
 * pick is under way for, for a picture that arrives after the view model has gone. [linkDownloader] brings the picture a
 * missing one's link names, for its Download.
 */
internal class SongImageAdding(
    val fitter: PictureFitter,
    val saveSongImage: SaveSongImageUseCase,
    val pendingPick: PendingMissingImagePick,
    val linkDownloader: PictureLinkDownloader,
)
