/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Counts what sync tells the pictures about, and runs what sync runs inside [withImagesKept] (with no writer: the app
 * stores pictures, never sync); nothing else of it is sync's to call.
 */
internal class RecordingImageRepository : ImageRepository {

    var changeCount = 0

    override val changes: StateFlow<Long> = MutableStateFlow(0L)

    override suspend fun loadImage(name: String): ByteArray? = throw UnsupportedOperationException()

    override suspend fun loadImageSize(name: String): ImageSize? = throw UnsupportedOperationException()

    override suspend fun loadLibraryImageNames(): List<String> = throw UnsupportedOperationException()

    override suspend fun libraryNameOf(bytes: ByteArray): ImageIntake = throw UnsupportedOperationException()

    override suspend fun addDraftImage(bytes: ByteArray): ImageIntake = throw UnsupportedOperationException()

    override fun holdEditorImages(names: Set<String>) = throw UnsupportedOperationException()

    override suspend fun clearDraftImages() = throw UnsupportedOperationException()

    override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T = block(
        object : LibraryImageWriter {
            override suspend fun addImage(bytes: ByteArray): ImageIntake = throw UnsupportedOperationException()

            override suspend fun moveDraftImages(names: Set<String>): Map<String, String> = throw UnsupportedOperationException()
        },
    )

    override fun onLibraryImagesChanged() {
        changeCount++
    }
}
