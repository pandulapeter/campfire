/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.staging

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * The staging folder (`imports/`), where an import writes what it receives and unpacks before anything reaches the
 * library: one per process, which is its session.
 *
 * Every name it writes carries the session and the request that wrote it, and nothing that arrived with the file:
 * `<session>-<request>-<n>.zip` for a received or nested archive, `<session>-<request>-<sha256>.<ext>` for an unpacked
 * picture or attachment (written as `<session>-<request>-<n>.part` until its hash is known, then renamed), the session
 * 16 and the hash 64 lowercase hex digits, the extension one of [LibraryFiles.ATTACHMENT_EXTENSIONS] or
 * [UNKNOWN_TYPE_EXTENSION]. So a hostile
 * entry name never becomes a path, [deleteOtherSessions] can empty the folder on launch of whatever a crash, a killed
 * process, or a closed tab left without touching a file this process has already received (a cold start's "Open
 * with"), and [deleteRequest] removes exactly one request's files.
 *
 * What is staged counts against the app-wide [StagingLedger]. A file is only ever handed out as a [StagedFile] once it
 * was closed cleanly; a sink that fails, is cancelled, or runs out of budget deletes its partial file first.
 */
@Single
internal class ImportStaging(
    private val fileStorage: FileStorage,
    private val logger: Logger,
) {

    /** This process's session, random so that no earlier process's can be mistaken for it. */
    val session: String = Uuid.random().toHexString().take(SESSION_DIGITS)

    internal val ledger = StagingLedger(fileStorage)

    /** Held while a request's files are named, renamed, recorded, or deleted, so that a deletion never misses one. */
    private val files = Mutex()
    private var lastNumber = 0L
    private val liveRequests = mutableSetOf<Long>()

    /** The unpacked files each live request holds, by name, so that a second entry of the same content is not staged twice. */
    private val unpackedFiles = mutableMapOf<Long, MutableMap<String, StagedFile>>()

    /** A new request, whose files stay until [deleteRequest] or the next launch. */
    suspend fun newRequest(): StagingRequest = files.withLock {
        StagingRequest(++lastNumber).also { liveRequests += it.id }
    }

    /**
     * Reserves [bytes] for [request] from the ledger before its files are written (an archive's declared sizes added up),
     * see [StagingLedger.reserve].
     */
    suspend fun reserve(request: StagingRequest, bytes: Long) {
        requireLive(request)
        ledger.reserve(request.id, bytes)
    }

    /**
     * Gives back what [request] reserved with [reserve] and no file took, once the walks it was reserved for have ended,
     * however they ended; see [StagingLedger.releasePool]. Finishes even when cancelled.
     */
    suspend fun releaseUnused(request: StagingRequest) = withContext(NonCancellable) { ledger.releasePool(request.id) }

    /**
     * A sink for an archive [request] receives or finds nested in another, [announcedSize] bytes long where that is known
     * (reserved before the file is made), else reserved as it grows. Its [StagedFile] has no hash.
     */
    suspend fun openArchive(request: StagingRequest, announcedSize: Long?): StagingSink {
        if (announcedSize != null && announcedSize > ImportLimits.MAX_STAGED_ARCHIVE_SIZE) {
            throw StagingRefusedException.TooLarge(ImportLimits.MAX_STAGED_ARCHIVE_SIZE)
        }
        return open(request = request, extension = null, announcedSize = announcedSize)
    }

    /**
     * A sink for a picture or attachment [request] unpacks, named by its content's SHA-256 and [extension] (with its dot,
     * one of [LibraryFiles.ATTACHMENT_EXTENSIONS], or [UNKNOWN_TYPE_EXTENSION] for a file of any other name) once it is
     * whole.
     */
    suspend fun openUnpacked(request: StagingRequest, extension: String, announcedSize: Long?): StagingSink {
        require(extension in LibraryFiles.ATTACHMENT_EXTENSIONS || extension == UNKNOWN_TYPE_EXTENSION) { "Not an extension a staged file can have: \"$extension\"." }
        return open(request = request, extension = extension, announcedSize = announcedSize)
    }

    private suspend fun open(request: StagingRequest, extension: String?, announcedSize: Long?): StagingSink {
        val isUnpacked = extension != null
        val name = files.withLock {
            check(request.id in liveRequests) { "The request was deleted." }
            prefixOf(request) + (++lastNumber) + if (isUnpacked) PARTIAL_EXTENSION else ARCHIVE_EXTENSION
        }
        val reserved = announcedSize?.also { ledger.take(request.id, it, isUnpacked) } ?: 0L
        val fileSink = try {
            fileStorage.openSink(StorageDirectory.IMPORTS, name)
        } catch (exception: Throwable) {
            withContext(NonCancellable) {
                ledger.giveBack(request.id, reserved = reserved, written = 0, isUnpacked = isUnpacked)
                deleteQuietly(name)
            }
            throw exception
        }
        return StagingSink(
            staging = this,
            request = request,
            name = name,
            extension = extension,
            announcedSize = announcedSize,
            reserved = reserved,
            fileSink = fileSink,
            digest = if (isUnpacked) Sha256.Digest() else null,
        )
    }

    /**
     * Names a sink's closed file: an archive keeps its name, an unpacked file is renamed by its [sha256Hex], or deleted
     * where the request already holds that content, whose [StagedFile] is then the answer.
     */
    internal suspend fun complete(sink: StagingSink, size: Long, extension: String?, sha256Hex: String?): StagedFile = files.withLock {
        requireLiveLocked(sink.request)
        if (extension == null || sha256Hex == null) return@withLock StagedFile(name = sink.name, size = size, sha256Hex = null)
        val name = prefixOf(sink.request) + sha256Hex + extension
        val held = unpackedFiles.getOrPut(sink.request.id, ::mutableMapOf)
        held[name]?.let { existing ->
            fileStorage.delete(StorageDirectory.IMPORTS, sink.name)
            ledger.giveBack(sink.request.id, reserved = size, written = size, isUnpacked = true)
            return@withLock existing
        }
        fileStorage.moveInto(StorageDirectory.IMPORTS, sink.name, StorageDirectory.IMPORTS, name)
        StagedFile(name = name, size = size, sha256Hex = sha256Hex).also { held[name] = it }
    }

    /**
     * Deletes one staged file of [request] that is no longer needed (a received archive once it is unpacked) and gives
     * its room back. Never throws, and finishes even when cancelled.
     */
    suspend fun deleteFile(request: StagingRequest, file: StagedFile) = withContext(NonCancellable) {
        files.withLock {
            if (!file.name.startsWith(prefixOf(request))) return@withLock
            val isUnpacked = file.sha256Hex != null
            if (isUnpacked) unpackedFiles[request.id]?.remove(file.name)
            if (deleteQuietly(file.name)) {
                ledger.giveBack(request.id, reserved = file.size, written = file.size, isUnpacked = isUnpacked)
            }
        }
    }

    /**
     * Deletes every file of [request], finished or partial, and gives back all it reserved: an import settled (imported,
     * cancelled, failed and dismissed, or replaced by a retry). A sink of it still open fails at its next write or at its
     * finish and deletes its own file. Never throws, and finishes even when cancelled; a file that cannot be deleted now
     * (one still open on Windows) goes at the next launch.
     */
    suspend fun deleteRequest(request: StagingRequest) = withContext(NonCancellable) {
        files.withLock {
            liveRequests -= request.id
            unpackedFiles -= request.id
            val prefix = prefixOf(request)
            listQuietly().filter { it.startsWith(prefix) }.forEach { deleteQuietly(it) }
            ledger.close(request.id)
        }
    }

    /**
     * Deletes every file of the folder that this session did not write: what an earlier process left behind. Run on
     * launch; safe at any moment, since nothing of this session's matches. Never throws but cancellation.
     */
    suspend fun deleteOtherSessions() {
        val prefix = "$session-"
        listQuietly().filterNot { it.startsWith(prefix) }.forEach { deleteQuietly(it) }
    }

    /**
     * The live request [file] was staged for, which its name carries: what an archive unpacks belongs to the request that
     * received it, so that settling the request removes both. Throws for a file of another session or a deleted request.
     */
    suspend fun requestOf(file: StagedFile): StagingRequest = files.withLock {
        val id = file.name.takeIf { it.startsWith("$session-") }?.removePrefix("$session-")?.substringBefore('-', "")?.toLongOrNull()
        check(id != null && id in liveRequests) { "\"${file.name}\" is not a staged file of a live request." }
        StagingRequest(id)
    }

    /** The unpacked files [request] holds now, which a later sink of the same content is handed instead of a new one. */
    suspend fun unpackedNamesOf(request: StagingRequest): Set<String> = files.withLock { unpackedFiles[request.id]?.keys?.toSet().orEmpty() }

    internal fun isLive(request: StagingRequest) = request.id in liveRequests

    private suspend fun requireLive(request: StagingRequest) = files.withLock { requireLiveLocked(request) }

    private fun requireLiveLocked(request: StagingRequest) = check(request.id in liveRequests) { "The request was deleted." }

    internal suspend fun deleteQuietly(name: String) = try {
        fileStorage.delete(StorageDirectory.IMPORTS, name)
        true
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.log("Could not remove the staged file \"$name\": ${exception.message}")
        false
    }

    private suspend fun listQuietly() = try {
        fileStorage.listNames(StorageDirectory.IMPORTS)
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        logger.log("Could not list the staged files: ${exception.message}")
        emptyList()
    }

    private fun prefixOf(request: StagingRequest) = "$session-${request.id}-"
}

/** One import's share of the staging folder, see [ImportStaging.newRequest]. */
internal data class StagingRequest(val id: Long)

private const val SESSION_DIGITS = 16
private const val ARCHIVE_EXTENSION = ".zip"
private const val PARTIAL_EXTENSION = ".part"

/**
 * The extension of a staged file whose name has none the library keeps (a picture a song names as `chart`, a file
 * attached as `run.exe`): staged all the same, so that the import judges it by its content as it judges one in memory.
 */
internal const val UNKNOWN_TYPE_EXTENSION = ".bin"
