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
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.domain.api.images.SaveWithDraftImagesUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
internal class SaveWithDraftImagesUseCaseImpl(
    private val imageRepository: ImageRepository,
) : SaveWithDraftImagesUseCase {

    override suspend operator fun <T> invoke(text: String, save: suspend (text: String) -> T): T = withContext(NonCancellable) {
        val names = ChordProImages.referencedSources(text)
        // A song without pictures is saved exactly as it was before there were any, the pruning's hold not waited for.
        if (names.isEmpty()) return@withContext save(text)
        imageRepository.withImagesKept { writer ->
            val renamed = writer.moveDraftImages(names)
            save(if (renamed.isEmpty()) text else ChordProImages.withSourcesRewritten(text) { renamed[it] })
        }
    }
}
