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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield

/**
 * A songs directory held in a map, whose reads and writes can be held back until the test lets them through. A song
 * is titled with what its file holds, so that a list entry shows whether it was read before or after a write.
 */
internal class FakeSongLocalSource(files: Map<String, String> = emptyMap()) : SongLocalSource {

    val files = files.toMutableMap()

    /** Every file whose text [loadSongContent] read, in order. */
    val reads = mutableListOf<String>()

    /** Awaited by [loadSongContent] once it has taken the file's text, before it hands it back. */
    var readGate: CompletableDeferred<Unit>? = null

    /** Awaited by a read of the file it is filed under, as a slow storage would keep a refresh reading. */
    val loadGates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Awaited once a save or a deletion has reached the map: the file changed, the caller has not heard yet. */
    var afterWriteGate: CompletableDeferred<Unit>? = null

    /** A file whose deletion fails, as one held open by another program would on Windows. */
    var undeletableFileName: String? = null

    /** The name [renameSong] moves each file to; a file not in it is one whose header already gives its name. */
    val renames = mutableMapOf<String, String>()

    override suspend fun loadSongs(onProgress: (List<Song>) -> Unit) = files.keys.map(::song)

    override suspend fun loadSongFileSizes() = files.mapValues { (_, text) -> text.length.toLong() }

    override suspend fun loadSong(fileName: String): Song? {
        loadGates[fileName]?.await()
        return if (fileName in files) song(fileName) else null
    }

    override suspend fun loadSongContent(fileName: String): SongContent? {
        reads += fileName
        val text = files[fileName]
        readGate?.await()
        return text?.let { SongContent(fileName = fileName, text = it) }
    }

    override suspend fun saveSongContent(content: SongContent) {
        files[content.fileName] = content.text
        afterWriteGate?.await()
    }

    override suspend fun createSong(title: String, artist: String, text: String): Song {
        val fileName = generateSequence(1) { it + 1 }.map { if (it == 1) "$title.cho" else "${title}_$it.cho" }.first { it !in files }
        // The storage finds the name free on one trip and writes under it on another, and this is the gap between them.
        yield()
        files[fileName] = text
        return song(fileName)
    }

    override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()

    override suspend fun renameSong(song: Song): Song? {
        val newName = renames[song.fileName] ?: return null
        files[newName] = files.remove(song.fileName) ?: return null
        return song(newName)
    }

    override suspend fun deleteSong(fileName: String) {
        if (fileName == undeletableFileName) throw IllegalStateException("The file is in use.")
        files.remove(fileName)
        afterWriteGate?.await()
    }

    override suspend fun exists(fileName: String) = fileName in files

    fun song(fileName: String) = Song(
        fileName = fileName,
        title = files[fileName] ?: fileName,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = false,
        canUpdateFileName = false,
        lastModified = 0L,
        size = 0L,
    )
}
