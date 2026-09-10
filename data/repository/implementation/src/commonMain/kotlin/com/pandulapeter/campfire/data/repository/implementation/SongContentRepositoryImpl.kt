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

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SongContentRepositoryImpl(
    private val songLocalSource: SongLocalSource
) : SongContentRepository {

    private val cache = mutableMapOf<String, SongContent>()
    private val mutex = Mutex()

    override suspend fun loadSongContent(fileName: String, shouldCache: Boolean): SongContent? = mutex.withLock {
        cache[fileName] ?: try {
            songLocalSource.loadSongContent(fileName)?.also { if (shouldCache) cache[fileName] = it }
        } catch (exception: Exception) {
            println("Could not read the song \"$fileName\": ${exception.message}")
            null
        }
    }

    override suspend fun invalidate(fileName: String?) = mutex.withLock {
        if (fileName == null) cache.clear() else cache.remove(fileName)
        Unit
    }
}
