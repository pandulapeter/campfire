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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.implementation.base.recovering
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
internal class SongContentRepositoryImpl(
    private val songLocalSource: SongLocalSource,
    private val logger: Logger,
) : SongContentRepository {

    /**
     * The least recently used first: a hit is moved to the end, and what is dropped to stay within [MAXIMUM_CACHED_SONGS]
     * and [MAXIMUM_CACHED_CHARACTERS] is taken from the start. The count is what keeps a long session from holding every
     * song it opened; the characters are what keep a handful of songbooks pasted into one file each from doing the
     * same. Either is several times what the pages of a setlist around the one on screen need.
     */
    private val cache = LinkedHashMap<String, SongContent>()
    private var cachedCharacters = 0L
    private val mutex = Mutex()

    /**
     * Bumped by every [invalidate], so that a read which started before one cannot put the text it read back into the
     * cache afterwards: the file was written in the meantime, and that text is not the file any more.
     */
    private var generation = 0L

    /** Set from inside the lock, which a state never suspends for, so a slow collector can never hold up a write. */
    private val _invalidations = MutableStateFlow(generation)
    override val invalidations = _invalidations.asStateFlow()

    /**
     * The file is read outside the lock: the lock only guards the map, so one slow file does not hold up the pages
     * next to it in a setlist, and an export walking the library does not block the song being opened meanwhile.
     */
    override suspend fun loadSongContent(fileName: String, useCache: Boolean): SongContent? {
        val generationBeforeRead = mutex.withLock {
            if (useCache) cache.remove(fileName)?.let { content ->
                cache[fileName] = content
                return content
            }
            generation
        }
        val content = logger.recovering(
            describe = { "Could not read the song \"$fileName\": ${it.message}" },
            fallback = { null },
        ) { songLocalSource.loadSongContent(fileName) } ?: return null
        if (useCache) {
            mutex.withLock {
                if (generation == generationBeforeRead) put(content)
            }
        }
        return content
    }

    override suspend fun invalidate(fileName: String?) = mutex.withLock {
        if (fileName == null) {
            cache.clear()
            cachedCharacters = 0
        } else {
            remove(fileName)
        }
        _invalidations.value = ++generation
    }

    override suspend fun invalidate(fileNames: Set<String>) = mutex.withLock {
        fileNames.forEach(::remove)
        _invalidations.value = ++generation
    }

    /** A text larger than the whole budget is handed out without being kept, rather than emptying the cache for it. */
    private fun put(content: SongContent) {
        remove(content.fileName)
        if (content.text.length > MAXIMUM_CACHED_CHARACTERS) return
        cache[content.fileName] = content
        cachedCharacters += content.text.length
        while (cache.size > MAXIMUM_CACHED_SONGS || cachedCharacters > MAXIMUM_CACHED_CHARACTERS) {
            remove(cache.keys.first())
        }
    }

    private fun remove(fileName: String) {
        cache.remove(fileName)?.let { cachedCharacters -= it.text.length }
    }

    private companion object {
        const val MAXIMUM_CACHED_SONGS = 32
        const val MAXIMUM_CACHED_CHARACTERS = 1L shl 20
    }
}
