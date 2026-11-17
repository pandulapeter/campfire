/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.ImageLocalSource
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor

/** Both shelves in memory, holding anything a test puts there and listing only what the real source lists. */
internal class FakeImageLocalSource : ImageLocalSource {

    val shelves = ImageShelf.entries.associateWith { mutableMapOf<String, ByteArray>() }

    /** The dispatcher of every read made of a file that was there, which is how a name taken is compared. */
    val comparingReads = mutableListOf<ContinuationInterceptor?>()

    override suspend fun readImage(shelf: ImageShelf, name: String) = shelves.getValue(shelf)[name]
        ?.also { comparingReads += currentCoroutineContext()[ContinuationInterceptor] }

    override suspend fun hasImage(shelf: ImageShelf, name: String) = name in shelves.getValue(shelf)

    override suspend fun imageSize(shelf: ImageShelf, name: String) = shelves.getValue(shelf)[name]?.size?.toLong()

    override suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray) {
        shelves.getValue(shelf)[name] = bytes
    }

    override suspend fun listImageNames(shelf: ImageShelf) = shelves.getValue(shelf).keys.filter(LibraryFiles::isLibraryFileName)

    override suspend fun listImageSizes(shelf: ImageShelf) = listImageNames(shelf).associateWith { shelves.getValue(shelf).getValue(it).size.toLong() }

    override suspend fun deleteImage(shelf: ImageShelf, name: String) {
        shelves.getValue(shelf).remove(name)
    }
}
