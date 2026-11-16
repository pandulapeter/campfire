/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import kotlinx.coroutines.flow.StateFlow

/** An [ImageRepository] whose every member fails as the other stubs' do, for a test to override only what it expects. */
internal open class ImageRepositoryStub : ImageRepository {
    override val changes: StateFlow<Long> get() = throw NotImplementedError("Not expected to be called by this test.")
    override suspend fun loadImage(name: String): ByteArray? = throw NotImplementedError("Not expected to be called by this test.")
    override suspend fun loadImageSize(name: String): ImageSize? = throw NotImplementedError("Not expected to be called by this test.")
    override suspend fun addDraftImage(bytes: ByteArray): ImageIntake = throw NotImplementedError("Not expected to be called by this test.")
    override fun holdEditorImages(names: Set<String>): Unit = throw NotImplementedError("Not expected to be called by this test.")
    override suspend fun clearDraftImages(): Unit = throw NotImplementedError("Not expected to be called by this test.")
    override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T = throw NotImplementedError("Not expected to be called by this test.")
    override fun onLibraryImagesChanged(): Unit = throw NotImplementedError("Not expected to be called by this test.")
}
