/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import com.pandulapeter.campfire.domain.api.images.AddDraftImageUseCase
import com.pandulapeter.campfire.domain.api.images.ClearDraftImagesUseCase
import com.pandulapeter.campfire.domain.api.images.DownloadPictureUseCase
import com.pandulapeter.campfire.domain.api.images.ExamineImageUseCase
import com.pandulapeter.campfire.domain.api.images.HoldEditorImagesUseCase
import com.pandulapeter.campfire.domain.api.images.SaveWithDraftImagesUseCase
import com.pandulapeter.campfire.domain.api.songs.CreateSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.DraftSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveEditorDraftUseCase
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.platform.reencodePicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.Factory

/**
 * The use cases [EditorSession] keeps its draft, makes new songs from texts, and handles the pictures the editor's text
 * names with, which the view model passes on as one.
 */
@Factory
class EditorUseCases(
    internal val getEditorDraft: GetEditorDraftUseCase,
    internal val saveEditorDraft: SaveEditorDraftUseCase,
    internal val draftSongFromText: DraftSongFromTextUseCase,
    internal val createSongFromText: CreateSongFromTextUseCase,
    internal val addDraftImage: AddDraftImageUseCase,
    internal val examineImage: ExamineImageUseCase,
    internal val holdEditorImages: HoldEditorImagesUseCase,
    internal val clearDraftImages: ClearDraftImagesUseCase,
    internal val saveWithDraftImages: SaveWithDraftImagesUseCase,
    internal val downloadPicture: DownloadPictureUseCase,
) {

    /**
     * The editor's draft pictures, kept and let go of in [scope], their refusals said through [messageSink], their fitting
     * tracked as [pictureWork].
     */
    internal fun imagesFor(scope: CoroutineScope, messageSink: MessageSink, pictureWork: PictureWork) = EditorImages(
        scope = scope,
        messageSink = messageSink,
        addDraftImage = addDraftImage,
        pictureFitter = PictureFitter(examine = examineImage::invoke, reencode = ::reencodePicture, work = pictureWork),
        linkDownloader = PictureLinkDownloader(download = downloadPicture::invoke, work = pictureWork, messageSink = messageSink),
        holdEditorImages = holdEditorImages,
        clearDraftImages = clearDraftImages,
        computation = Dispatchers.Default,
    )
}
