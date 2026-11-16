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
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.sync.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLock
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.SyncOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * How a picture moves. Its name is its content (`LibraryFiles.isImageNamedFor`), so two copies of one name are the same
 * picture unless one is damaged, and a picture is never a conflict: a copy whose bytes are not what its name says is
 * never uploaded, never written into the library, and is replaced by an intact one from the other side wherever there is
 * one. That check is also what stands in for the hash [readLocalListing] does not take of a picture.
 *
 * [imageWritesGate] is `ImageRepository.withImagesKept`, which the app holds from storing a picture to writing the song
 * that names it: [deleteLocally] decides inside it, so that a song saved during the run naming a picture this run was
 * about to delete either is on disk when the deletion looks, or is written after the picture is gone and stores it again.
 */
internal class ImageTransfers(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val logger: Logger,
    private val downloadWithinLimit: suspend (SyncPass, SyncKey) -> RemoteDocument,
    private val imageWritesGate: suspend (suspend () -> Unit) -> Unit,
) {

    /**
     * The pass's deletions of pictures here, as one batch: the plan knew what the songs named when the run listed the
     * library, which says nothing about a song saved since. So what every song here names is read again, under the
     * library's lock and inside [imageWritesGate], and a picture a song names now goes back up instead; where a song
     * cannot be read, nothing is deleted. A picture changed since the listing goes up too, as [sendUp] decides.
     */
    suspend fun deleteLocally(pass: SyncPass, operations: List<SyncOperation>): List<Pair<SyncOperation, SyncEngine.OperationOutcome>> {
        if (operations.isEmpty()) return emptyList()
        var decided = emptyList<Pair<SyncOperation, SyncEngine.OperationOutcome?>>()
        imageWritesGate {
            decided = libraryFileLock.withLock {
                val named = namedNow() ?: return@withLock operations.map { it to SyncEngine.OperationOutcome() }
                operations.map { operation -> operation to deleteUnlessNamed(pass, operation.key, named) }
            }
        }
        return decided.map { (operation, outcome) ->
            operation to (outcome ?: sendUp(pass, SyncOperation.Upload(operation.key, expectedRevision = null)))
        }
    }

    /** Null where the picture goes back up instead. Must be called under [libraryFileLock]. */
    private suspend fun deleteUnlessNamed(pass: SyncPass, key: SyncKey, named: Set<String>): SyncEngine.OperationOutcome? {
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return SyncEngine.OperationOutcome(removals = setOf(key))
        if (key.name in named || localHashOf(key, local) != pass.index[key]?.localHash) return null
        // Finished together once started, as SyncEngine's deletions are.
        withContext(NonCancellable) {
            libraryFileLocalSource.deleteLibraryFile(key.kind, key.name)
            pass.run.onLocalFileChanged(key)
        }
        return SyncEngine.OperationOutcome(removals = setOf(key), summary = SyncSummary(deletedLocally = 1))
    }

    /** Every picture a song here names as the files are now, or null where one of them cannot be read. */
    private suspend fun namedNow(): Set<String>? = try {
        libraryFileLocalSource.loadLibraryFiles()
            .filter { it.kind == LibraryFileKind.SONG }
            .flatMapTo(mutableSetOf()) { file ->
                if (file.size > ImportLimits.MAX_TEXT_FILE_SIZE) return null
                libraryFileLocalSource.readLibraryFile(file.kind, file.name)?.let { ChordProImages.referencedSources(it.decodeLibraryText()) }.orEmpty()
            }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.log("Kept the pictures this run would have deleted: the songs could not be read (${exception.message}).")
        null
    }

    /**
     * A download, or a picture on both sides that the index does not know in step. An intact copy here is the picture,
     * which only needs the index entry, unless the service says the folder's copy holds other bytes, which this one then
     * replaces.
     */
    suspend fun bringDown(pass: SyncPass, key: SyncKey, revision: String): SyncEngine.OperationOutcome {
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isIntact(key, local)) {
            val remoteHash = pass.remoteFiles[key]?.contentHash
            val localHash = pass.run.provider.contentHashOf(local)
            // Where either side cannot say, nothing says the folder's copy is damaged, and a copy that is would be refused
            // by every device that tried to download it rather than spread.
            return if (remoteHash == null || localHash == null || localHash == remoteHash) {
                SyncEngine.OperationOutcome(entries = mapOf(key to SyncIndexEntry(localHashOf(key, local), revision)))
            } else {
                upload(pass, key, local, expectedRevision = revision)
            }
        }
        val downloaded = downloadWithinLimit(pass, key)
        if (!isIntact(key, downloaded.bytes)) return failed(key, "the cloud folder's copy is not the picture its name says")
        val isWritten = libraryFileLock.withLock {
            val current = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            // Anything intact under this name is this very picture, written meanwhile by an import or a Save.
            if (current != null && isIntact(key, current)) {
                false
            } else {
                // Finished together once started, as SyncEngine's download is.
                withContext(NonCancellable) {
                    libraryFileLocalSource.writeLibraryFile(key.kind, key.name, downloaded.bytes)
                    pass.run.onLocalFileChanged(key)
                }
                true
            }
        }
        return SyncEngine.OperationOutcome(
            entries = mapOf(key to SyncIndexEntry(localHashOf(key, downloaded.bytes), downloaded.revision)),
            summary = if (isWritten) SyncSummary(downloaded = 1) else SyncSummary(),
        )
    }

    /** An upload: of an intact picture only, a damaged one being replaced from the folder where the folder has it. */
    suspend fun sendUp(pass: SyncPass, operation: SyncOperation.Upload): SyncEngine.OperationOutcome {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return SyncEngine.OperationOutcome()
        if (isIntact(key, bytes)) return upload(pass, key, bytes, operation.expectedRevision)
        val remote = pass.remoteFiles[key] ?: return failed(key, "this device's copy is not the picture its name says")
        return bringDown(pass, key, remote.revision)
    }

    private suspend fun upload(pass: SyncPass, key: SyncKey, bytes: ByteArray, expectedRevision: String?) =
        when (val result = pass.run.provider.upload(key.kind, key.name, bytes, expectedRevision)) {
            is RemoteWriteResult.Written -> SyncEngine.OperationOutcome(
                entries = mapOf(key to SyncIndexEntry(localHashOf(key, bytes), result.revision)),
                summary = SyncSummary(uploaded = 1),
            )

            // Another device put the picture there meanwhile: the next pass finds it on both sides and settles it here.
            RemoteWriteResult.Conflict -> SyncEngine.OperationOutcome(isUnresolved = true)
        }

    private fun isIntact(key: SyncKey, bytes: ByteArray) = LibraryFiles.isImageNamedFor(key.name, localContentHash(bytes))

    /** The index is left alone, so the next run sees this file as it was and tries again. */
    private fun failed(key: SyncKey, reason: String): SyncEngine.OperationOutcome {
        logger.log("Could not sync \"${key.path}\": $reason.")
        return SyncEngine.OperationOutcome(summary = SyncSummary(failed = listOf(key.name)))
    }
}
