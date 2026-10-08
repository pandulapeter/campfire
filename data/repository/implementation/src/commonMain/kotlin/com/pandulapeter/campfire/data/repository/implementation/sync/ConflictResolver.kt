/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.SyncSummary
import com.pandulapeter.campfire.data.repository.implementation.LibraryFileLock
import com.pandulapeter.campfire.data.repository.implementation.base.recovering
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlinx.coroutines.CancellationException

/**
 * Settles a file that changed on both sides: both versions kept under two names, except where nothing anybody wrote is
 * lost by taking the cloud folder's. Built by [SyncEngine], never by Koin, with the engine's own collaborators.
 *
 * @param plantedContentHash What the app planted under a name, as a content hash, or null for a file it did not plant.
 */
internal class ConflictResolver(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val libraryFileLock: LibraryFileLock,
    private val setlistComparison: SetlistComparison,
    private val plantedContentHash: suspend (SyncKey) -> String?,
) {

    /**
     * `SyncEngine.resolve` from the point where both versions are in hand, also for a download that found a save under its
     * write.
     *
     * Every device gives an undated setlist the day it first reads it on, so after an update every setlist two devices
     * shared changed on both sides, in nothing but that day. Kept as a conflict, that would be a copy of every setlist
     * the library had on every device. So a setlist whose two versions differ only in the day, or whose only change
     * here is the day this device's read gave the undated version the last run saw ([indexEntry]), takes the remote
     * version: it needs no upload and is what every other device has or will download. What it costs is a day set on
     * purpose on two devices offline, or here while another device edited the setlist, losing to the cloud folder's.
     *
     * A demo file this device planted that still holds exactly what was planted ([plantedContentHash]) takes the remote
     * version too, but only where the index has no entry for it, the first time this device meets the name in this
     * folder.
     */
    suspend fun resolveWith(
        pass: SyncPass,
        key: SyncKey,
        revision: String,
        localBytes: ByteArray,
        remote: ByteArray,
        indexEntry: SyncIndexEntry?,
    ): SyncEngine.OperationOutcome {
        val provider = pass.run.provider
        val onLocalFileChanged = pass.run.onLocalFileChanged
        if (remote.contentEquals(localBytes)) {
            return SyncEngine.OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(localBytes), revision)))
        }
        // A demo file this device planted and nobody has touched since, met in this folder for the first time: the
        // folder's version is an older or newer demo that another device planted under the same name (or the user's
        // edit of one), and keeping both would be two copies of every demo song on every device. Nothing anybody wrote
        // is lost by taking it, which a content check rather than a name check is what guarantees. Not with an index
        // entry: a file the last run saw with other bytes and that is back at its planted ones was changed back on
        // purpose, and that change is kept like any other.
        val plantedHash = if (indexEntry == null) plantedContentHash(key) else null
        if (plantedHash != null && plantedHash == localContentHash(localBytes)) {
            return takeRemote(key = key, revision = revision, localBytes = localBytes, remote = remote, onLocalFileChanged = onLocalFileChanged)
        }
        if (key.kind == LibraryFileKind.SETLIST) {
            // Byte for byte as this version encodes the document, which holds for every file the app wrote; one written
            // by hand never matches and keeps its conflict copy, which is the safe direction.
            val isOnlyTheDayHere = indexEntry != null &&
                setlistComparison.withoutDate(localBytes)?.let(::localContentHash) == indexEntry.localHash
            if (isOnlyTheDayHere || setlistComparison.isSameApartFromDate(localBytes, remote)) {
                return takeRemote(key = key, revision = revision, localBytes = localBytes, remote = remote, onLocalFileChanged = onLocalFileChanged)
            }
        }
        // Free on both sides, not only here: the listing may hold a file under the copy's name that has not come down
        // yet - another device's copy, or a song that simply has that name - and is planned as a download later in
        // this pass. A copy written under it here would be taken for that file changed on this device, go up over it,
        // and push the file itself on to the next number. Folded, as the service may take two spellings for one name.
        val remoteNames = pass.remoteFiles.keys.filter { it.kind == key.kind }.mapTo(hashSetOf()) { it.folded().name }
        // Under the lock like every other write: the repositories pick a free name and write under it in two steps too,
        // and would otherwise be given the one this is.
        val copyName = libraryFileLock.withLock {
            libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote) { name ->
                SyncKey(kind = key.kind, name = name).folded().name in remoteNames
            }
        }
        val copyKey = SyncKey(kind = key.kind, name = copyName)
        onLocalFileChanged(copyKey)
        val uploaded = try {
            provider.upload(key.kind, key.name, localBytes, revision)
        } catch (exception: CancellationException) {
            // Nothing says whether the write landed, so the copy stays: see the KDoc.
            throw exception
        } catch (exception: SyncNetworkException) {
            throw exception
        } catch (exception: Exception) {
            // The service answered, and the answer was no. The remote version is where it was, and a copy kept now would
            // be joined by another one every time this file is resolved again.
            discardCopy(copyKey, remote, onLocalFileChanged)
            throw exception
        }
        if (uploaded !is RemoteWriteResult.Written) {
            // Contested - unless what is there now is what was just sent, which is how a write looks that landed and was
            // then retried. In that case the remote version is gone from the service, and the copy is all there is of it.
            val isOwnWrite = provider.download(key.kind, key.name).bytes.contentEquals(localBytes)
            if (!isOwnWrite) discardCopy(copyKey, remote, onLocalFileChanged)
            return SyncEngine.OperationOutcome(
                summary = if (isOwnWrite) SyncSummary(conflicts = listOf(copyName)) else SyncSummary(),
                isUnresolved = true,
            )
        }
        val entries = mutableMapOf(key to SyncIndexEntry(localContentHash(localBytes), uploaded.revision))
        val copyUploaded = provider.upload(copyKey.kind, copyKey.name, remote, expectedRevision = null)
        if (copyUploaded is RemoteWriteResult.Written) {
            entries[copyKey] = SyncIndexEntry(localContentHash(remote), copyUploaded.revision)
        }
        // Counted as one upload even when the copy also went up: what the user needs to know is that one file was
        // in two states, and that both of them survived under the name in the summary.
        return SyncEngine.OperationOutcome(
            entries = entries,
            summary = SyncSummary(uploaded = 1, downloaded = 1, conflicts = listOf(copyName)),
        )
    }

    /**
     * Writes [remote] over the local file, but only over the [localBytes] it was compared with: a save that landed since
     * leaves the key to the next pass, which decides on a fresh listing.
     */
    private suspend fun takeRemote(
        key: SyncKey,
        revision: String,
        localBytes: ByteArray,
        remote: ByteArray,
        onLocalFileChanged: suspend (SyncKey) -> Unit,
    ): SyncEngine.OperationOutcome {
        val isWritten = libraryFileLock.withLock {
            val current = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
            (current != null && current.contentEquals(localBytes)).also { isSame ->
                if (isSame) {
                    libraryFileLocalSource.writeLibraryFile(key.kind, key.name, remote)
                    onLocalFileChanged(key)
                }
            }
        }
        return if (isWritten) {
            SyncEngine.OperationOutcome(entries = mapOf(key to SyncIndexEntry(localContentHash(remote), revision)), summary = SyncSummary(downloaded = 1))
        } else {
            SyncEngine.OperationOutcome(isUnresolved = true)
        }
    }

    /**
     * Takes back a copy whose file turned out not to have been overwritten remotely. Read before it is deleted, like
     * everything else this class removes: only the bytes that were written a moment ago are taken back.
     */
    private suspend fun discardCopy(copyKey: SyncKey, written: ByteArray, onLocalFileChanged: suspend (SyncKey) -> Unit) {
        recovering(
            // A copy too many is the harmless way for this to go wrong.
            describe = { "Could not remove the unused copy \"${copyKey.path}\": ${it.message}" },
            fallback = {},
        ) {
            libraryFileLock.withLock {
                if (libraryFileLocalSource.readLibraryFile(copyKey.kind, copyKey.name)?.contentEquals(written) == true) {
                    libraryFileLocalSource.deleteLibraryFile(copyKey.kind, copyKey.name)
                    onLocalFileChanged(copyKey)
                }
            }
        }
    }
}
