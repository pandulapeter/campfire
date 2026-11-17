/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.domain.api.images.PictureDownload
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink

/**
 * Downloads a picture the user asked for by its address (the editor's From a link, a missing picture's Download) and hands
 * it on as a picture picked from the files would be, for the same fitting and storing. The download is part of [work], so
 * that the window's progress bar says something is happening while a slow host answers.
 *
 * @param download The `DownloadPictureUseCase`, replaced by tests.
 */
internal class PictureLinkDownloader(
    private val download: suspend (url: String) -> PictureDownload,
    private val work: PictureWork,
    private val messageSink: MessageSink,
) {

    /**
     * The picture at [url] as a picked file, one left unread where it is too large to read (whose taker says so), or null
     * where there is none, said in a snackbar.
     */
    suspend fun picture(url: String): ImportedFile? = when (val result = work.track { download(url) }) {
        is PictureDownload.Picture -> ImportedFile(name = url, bytes = result.bytes)
        PictureDownload.TooLarge -> ImportedFile.unread(url, isTooLarge = true)
        PictureDownload.NotAPicture -> null.also { messageSink.sendMessage(Message.PictureLinkNotPicture) }
        PictureDownload.Missing -> null.also { messageSink.sendMessage(Message.PictureLinkMissing) }
        PictureDownload.Unreachable -> null.also { messageSink.sendMessage(Message.PictureLinkUnreachable) }
    }
}
