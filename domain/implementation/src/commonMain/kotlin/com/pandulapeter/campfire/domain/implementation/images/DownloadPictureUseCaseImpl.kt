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

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.domain.api.images.DownloadPictureUseCase
import com.pandulapeter.campfire.domain.api.images.PictureDownload
import org.koin.core.annotation.Factory

@Factory
internal class DownloadPictureUseCaseImpl(
    private val importDownloadRepository: ImportDownloadRepository,
    private val imageRepository: ImageRepository,
) : DownloadPictureUseCase {

    override suspend operator fun invoke(url: String) = when (
        val download = importDownloadRepository.downloadPicture(url = url, maxSizeBytes = ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE)
    ) {
        is ImportDownload.File -> if (imageRepository.libraryNameOf(download.bytes) == ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)) {
            PictureDownload.NotAPicture
        } else {
            PictureDownload.Picture(download.bytes)
        }
        ImportDownload.TooLarge -> PictureDownload.TooLarge
        ImportDownload.Missing -> PictureDownload.Missing
        ImportDownload.Unreachable -> PictureDownload.Unreachable
    }
}
