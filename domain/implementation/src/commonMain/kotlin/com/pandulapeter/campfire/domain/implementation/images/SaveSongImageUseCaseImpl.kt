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

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.images.SaveSongImageResult
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class SaveSongImageUseCaseImpl(
    private val imageRepository: ImageRepository,
    private val songRepository: SongRepository,
) : SaveSongImageUseCase {

    override suspend operator fun invoke(fileName: String, expectedText: String, missingName: String, bytes: ByteArray) = withContext(NonCancellable) {
        imageRepository.withImagesKept { writer ->
            when (val intake = writer.addImage(bytes)) {
                is ImageIntake.Refused -> SaveSongImageResult.Refused(intake.reason)
                is ImageIntake.Accepted -> {
                    val text = ChordProImages.withSourcesRewritten(expectedText) { source -> intake.name.takeIf { source == missingName } }
                    if (songRepository.saveSong(content = SongContent(fileName = fileName, text = text), expectedText = expectedText)) {
                        SaveSongImageResult.Added(text)
                    } else {
                        SaveSongImageResult.SongChanged
                    }
                }
            }
        }
    }
}
