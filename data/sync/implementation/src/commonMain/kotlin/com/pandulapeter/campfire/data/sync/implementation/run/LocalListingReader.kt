/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.LocalFileState
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * What [readLocalListing] found: the files it read, the ones it left alone for their size, and the ones it could not read.
 *
 * @param imageReferences The pictures each song of this device names, or null where a song could not be read (or was too
 *   large to be), which may name any of them: the planner then deletes no picture on either side.
 */
internal class LocalListing(
    val states: List<LocalFileState>,
    val tooLarge: Set<SyncKey>,
    val unreadable: Set<SyncKey>,
    val imageReferences: Map<SyncKey, Set<String>>?,
)

/**
 * Reading and hashing every file is the slow part of the preparation, so the files are read in parallel. A file over
 * [maxSize] is not read at all: nothing the app writes is that large, so it was put into the folder from outside, and
 * it is no song any other device would download.
 *
 * A picture is not read either: its name is its content and nothing writes another content under it, so its size
 * stands for its hash ([localImageHash]), and the library's photos are not read and hashed on every return to the front.
 * Every transfer of one checks its bytes against its name instead (`ImageTransfers`).
 *
 * A file whose read fails is one file's failure, like any other in a run, and is handed back as unreadable rather than
 * left out: left out, it would be a file gone from this device, which the planner carries out as a deletion on every
 * other one. Only a library of which not one file could be read ends the run, since that is the storage failing rather
 * than a file.
 */
internal suspend fun readLocalListing(
    libraryFileLocalSource: LibraryFileLocalSource,
    logger: Logger,
    maxSize: Long,
    batchSize: Int,
): LocalListing = coroutineScope {
    val (tooLarge, files) = libraryFileLocalSource.loadLibraryFiles().partition { it.size > maxSize }
    tooLarge.forEach { logger.log("Skipped \"${it.name}\": ${it.size} bytes is more than a library file can hold.") }
    val (images, documents) = files.partition { it.kind == LibraryFileKind.FILE }
    // In batches for the same reason as the song scan in SongLocalSourceImpl: unbounded, a large library is thousands of
    // open handles and all of its bytes in memory at once.
    val reads = documents
        .chunked(batchSize)
        .flatMap { batch -> batch.map { file -> async { readLocalState(libraryFileLocalSource, logger, SyncKey(kind = file.kind, name = file.name)) } }.awaitAll() }
    val failures = reads.filterIsInstance<LocalRead.Failed>()
    val read = reads.filterIsInstance<LocalRead.Read>()
    val imageStates = images.map { LocalFileState(key = SyncKey(kind = it.kind, name = it.name), hash = localImageHash(it.size)) }
    if (read.isEmpty() && failures.isNotEmpty() && imageStates.isEmpty()) throw failures.first().exception
    val isEverySongRead = (tooLarge.map { it.kind } + failures.map { it.key.kind }).none { it == LibraryFileKind.SONG }
    LocalListing(
        states = read.map { it.state } + imageStates,
        tooLarge = tooLarge.mapTo(mutableSetOf()) { SyncKey(kind = it.kind, name = it.name) },
        unreadable = failures.mapTo(mutableSetOf()) { it.key },
        imageReferences = if (isEverySongRead) read.filter { it.state.key.kind == LibraryFileKind.SONG }.associate { it.state.key to it.imageReferences } else null,
    )
}

private suspend fun readLocalState(libraryFileLocalSource: LibraryFileLocalSource, logger: Logger, key: SyncKey): LocalRead = logger.recovering(
    describe = { "Could not read \"${key.path}\": ${it.message}" },
    fallback = { LocalRead.Failed(key, it) },
) {
    libraryFileLocalSource.readLibraryFile(key.kind, key.name)?.let { bytes ->
        LocalRead.Read(
            state = LocalFileState(key = key, hash = localHashOf(key, bytes)),
            // The same generous reading the library's pruning uses, so that sync never deletes a picture the library keeps.
            imageReferences = if (key.kind == LibraryFileKind.SONG) ChordProImages.referencedSources(bytes.decodeLibraryText()) else emptySet(),
        )
    } ?: LocalRead.Absent
}

/** What reading one listed file came to. [Absent] is a file deleted since the listing, which really is gone. */
private sealed interface LocalRead {
    class Read(val state: LocalFileState, val imageReferences: Set<String>) : LocalRead
    data object Absent : LocalRead
    class Failed(val key: SyncKey, val exception: Exception) : LocalRead
}
