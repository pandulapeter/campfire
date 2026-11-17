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
import com.pandulapeter.campfire.domain.api.images.GetLibraryFileSizesUseCase
import com.pandulapeter.campfire.domain.api.images.ReadImageSizeUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.AttachmentIntake
import com.pandulapeter.campfire.presentation.ui.pictures.PendingMissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.platform.reencodePicture
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Factory

/**
 * The use cases [SongTextStore] keeps its texts in step with the files by, and the ones the pictures and attachments
 * those texts name are read, added, and opened with (and the size of all of them listed with), which the view model
 * passes on as one.
 */
@Factory
class SongTextUseCases(
    internal val getSongContentInvalidations: GetSongContentInvalidationsUseCase,
    internal val getImageSize: GetImageSizeUseCase,
    internal val getImage: GetImageUseCase,
    internal val getImageChanges: GetImageChangesUseCase,
    internal val getLibraryFileSizes: GetLibraryFileSizesUseCase,
    internal val saveSongImage: SaveSongImageUseCase,
    internal val readImageSize: ReadImageSizeUseCase,
    internal val examineImage: ExamineImageUseCase,
    internal val downloadPicture: DownloadPictureUseCase,
    internal val attachmentUseCases: SongAttachmentUseCases,
) {

    /**
     * The files attached to songs, kept and written for the sheets through [songTextStore] and [songMetadataEditing], the
     * picks of an Attachments sheet held while [openDialogs] has one open.
     */
    internal fun attachments(
        scope: CoroutineScope,
        messageSink: MessageSink,
        pictureWork: PictureWork,
        songTextStore: SongTextStore,
        songMetadataEditing: SongMetadataEditing,
        openDialogs: StateFlow<List<DialogType>>,
    ) = SongAttachmentEditing(
        scope = scope,
        messageSink = messageSink,
        songTextStore = songTextStore,
        songMetadataEditing = songMetadataEditing,
        intake = AttachmentIntake(addDraftAttachment = attachmentUseCases.addDraftAttachment::invoke, messageSink = messageSink, work = pictureWork),
        saveSongAttachments = attachmentUseCases.saveSongAttachments,
        exportAttachment = attachmentUseCases.exportAttachment,
        holdPickedAttachments = attachmentUseCases.holdPickedAttachments,
        librarySizes = getLibraryFileSizes(),
        openDialogs = openDialogs,
    )

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
