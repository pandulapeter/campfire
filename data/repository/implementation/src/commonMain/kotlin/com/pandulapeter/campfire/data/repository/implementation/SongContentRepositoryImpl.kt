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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SongContentRepositoryImpl(
    private val songLocalSource: SongLocalSource,
) : SongContentRepository {

    private val cache = mutableMapOf<String, SongContent>()
    private val mutex = Mutex()

    /**
     * Bumped by every [invalidate], so that a read which started before one cannot put the text it read back into the
     * cache afterwards: the file was written in the meantime, and that text is not the file any more.
     */
    private var generation = 0L

    /**
     * The file is read outside the lock: the lock only guards the map, so one slow file does not hold up the pages
     * next to it in a setlist, and an export walking the library does not block the song being opened meanwhile.
     */
    override suspend fun loadSongContent(fileName: String, shouldCache: Boolean): SongContent? {
        val generationBeforeRead = mutex.withLock {
            cache[fileName]?.let { return it }
            generation
        }
        val content = try {
            songLocalSource.loadSongContent(fileName)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not read the song \"$fileName\": ${exception.message}")
            null
        } ?: return null
        if (shouldCache) {
            mutex.withLock {
                if (generation == generationBeforeRead) cache[fileName] = content
            }
        }
        return content
    }

    override suspend fun invalidate(fileName: String?) = mutex.withLock {
        generation++
        if (fileName == null) cache.clear() else cache.remove(fileName)
        Unit
    }
}
