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

import com.pandulapeter.campfire.domain.api.images.DownloadPictureUseCase
import com.pandulapeter.campfire.domain.api.images.ExamineImageUseCase
import com.pandulapeter.campfire.domain.api.images.GetImageChangesUseCase
import com.pandulapeter.campfire.domain.api.images.GetImageSizeUseCase
import com.pandulapeter.campfire.domain.api.images.GetImageUseCase
import com.pandulapeter.campfire.domain.api.images.ReadImageSizeUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.PendingMissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.platform.reencodePicture
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
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
    internal val examineImage: ExamineImageUseCase,
    internal val downloadPicture: DownloadPictureUseCase,
) {

    /**
     * How a picture picked or downloaded for a song's missing one is made to fit (tracked as [pictureWork]) and stored, the
     * pick under way kept in [savedStateStore], a download that brings nothing said through [messageSink].
     */
    internal fun imageAdding(pictureWork: PictureWork, savedStateStore: SavedStateStore, messageSink: MessageSink) = SongImageAdding(
        fitter = PictureFitter(examine = examineImage::invoke, reencode = ::reencodePicture, work = pictureWork),
        saveSongImage = saveSongImage,
        pendingPick = PendingMissingImagePick(savedStateStore),
        linkDownloader = PictureLinkDownloader(download = downloadPicture::invoke, work = pictureWork, messageSink = messageSink),
    )
}
