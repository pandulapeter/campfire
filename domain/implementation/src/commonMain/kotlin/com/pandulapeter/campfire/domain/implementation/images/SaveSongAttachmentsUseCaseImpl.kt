/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.images

import com.pandulapeter.campfire.chordpro.edit.ChordProAttachments
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsResult
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class SaveSongAttachmentsUseCaseImpl(
    private val imageRepository: ImageRepository,
    private val songRepository: SongRepository,
) : SaveSongAttachmentsUseCase {

    override suspend operator fun invoke(fileName: String, expectedText: String, attachments: List<ChordProAttachment>) = withContext(NonCancellable) {
        imageRepository.withImagesKept { writer ->
            // The library's own files are not on the draft shelf, so only the ones picked in the sheet move.
            val renamed = writer.moveDraftImages(attachments.mapTo(mutableSetOf()) { it.source })
            val text = ChordProAttachments.setAttachments(
                text = expectedText,
                attachments = attachments.map { attachment -> renamed[attachment.source]?.let { attachment.copy(source = it) } ?: attachment },
            )
            if (songRepository.saveSong(content = SongContent(fileName = fileName, text = text), expectedText = expectedText)) {
                SaveSongAttachmentsResult.Saved(text)
            } else {
                SaveSongAttachmentsResult.SongChanged
            }
        }
    }
}
