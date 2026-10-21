/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRunEndingException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteSharedFile
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.sync.implementation.SyncIndexEntry
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import kotlinx.coroutines.CancellationException

/**
 * A run's shared-copy step (documentation/plans/song-links.md, §5): brings every shared copy in the cloud folder up to
 * date with the library a completed run left, applies this device's journal of renames and stops, and writes what
 * changed into the registry as one edit at the end, also when it ends early, keeping what was done up to then.
 *
 * It costs nothing when nothing is shared and the journal is empty, and only the listing when nothing changed: the
 * registry comes from the mirror where the listing names the revision it was read at, and a copy whose `built` matches
 * its inputs fingerprint ([sharedCopyFingerprint]) is neither read, built, nor uploaded. [buildNumber] stands for the
 * code that packs: a copy an older build made is built again once, one a newer build made is left alone.
 *
 * A share is never stopped by inference: an item missing from the library, or a copy missing from the folder, only
 * makes the entry `orphaned`, which keeps serving the last version, and an item that comes back makes it `ok` again.
 */
internal class SharedCopySync(
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val contents: SharedCopyContents,
    private val registry: SharedLinksRegistry,
    private val mirror: SharedLinksMirror,
    private val journal: SharedLinksJournal,
    private val logger: Logger,
    private val buildNumber: Int,
) {

    /**
     * Settles the shared copies against [index], the index the run completed with, and [sharedFiles] and
     * [registryRevision], what the run's last listing named. Answers false where a copy could not be settled or the
     * registry not written, which the next run tries again. Throws cancellation, every [SyncRunEndingException], a
     * [SyncScopeMissingException], and whatever stops the step as a whole, for the run to judge.
     */
    suspend fun synchronize(
        folder: SyncFolder,
        index: Map<SyncKey, SyncIndexEntry>,
        sharedFiles: List<RemoteSharedFile>,
        registryRevision: String?,
    ): Boolean {
        val pending = journal.load()
        if (sharedFiles.isEmpty() && pending.isEmpty()) return true
        val (revision, document) = read(folder, registryRevision, pending.isEmpty()) ?: return true
        val changes = SharedCopyChanges()
        val applied = mutableListOf<SharedLinksJournal.Entry>()
        var isSettled = true
        val failure = try {
            pending.forEach { entry ->
                when (entry) {
                    is SharedLinksJournal.Entry.Rename -> changes.rename(from = entry.from, to = entry.to)
                    is SharedLinksJournal.Entry.Stop -> {
                        // The copy goes before the entry, as Stop sharing does it: an entry removed while its copy stays
                        // would leave a working link that no screen shows any more.
                        folder.deleteSharedFile(entry.shareId)
                        changes.remove(entry.shareId)
                    }
                }
                applied += entry
            }
            val pass = Pass(
                folder = folder,
                index = IndexedNames(index),
                sharedFiles = sharedFiles.associateBy { it.shareId },
                // A registry read after the listing may name a share created since, whose copy the listing could not
                // have seen: missing from it, such a copy is not missing from the folder.
                isListingCurrent = revision == registryRevision,
                changes = changes,
            )
            changes.applyTo(document).links.forEach { entry ->
                if (!pass.settle(entry)) isSettled = false
            }
            null
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception
        }
        val isWritten = try {
            write(folder, document, changes, applied)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (failure == null) throw exception
            logger.log("Could not write the shared links after a failed step: ${exception::class.simpleName}")
            false
        }
        failure?.let { throw it }
        return isSettled && isWritten
    }

    private suspend fun read(folder: SyncFolder, registryRevision: String?, isJournalEmpty: Boolean): Pair<String?, SharedLinksDocument>? {
        val mirrored = mirror.load()
        // A journal to apply is a reason to read: the edit that writes it reads the registry anyway, and a mirror that is
        // current cannot be told from one that only looks it.
        if (isJournalEmpty && mirrored.revision == registryRevision) return mirrored.revision to mirrored.document
        return when (val read = registry.read(folder)) {
            is SharedLinksRegistry.Read.Readable -> read.revision to read.document
            is SharedLinksRegistry.Read.FromNewerVersion -> {
                logger.log("The shared links are of version ${read.version}, which this version leaves alone.")
                null
            }
        }
    }

    /** Writes [changes] where they change anything, then forgets the journal entries [applied]; false where it gave up. */
    private suspend fun write(
        folder: SyncFolder,
        document: SharedLinksDocument,
        changes: SharedCopyChanges,
        applied: List<SharedLinksJournal.Entry>,
    ): Boolean {
        if (changes.applyTo(document) != document) {
            val edit = registry.edit(folder) { current ->
                SharedLinksRegistry.Change(document = changes.applyTo(current).takeIf { it != current }, result = Unit)
            }
            when (edit) {
                is SharedLinksRegistry.Edit.Done -> Unit
                is SharedLinksRegistry.Edit.FromNewerVersion -> return false
                SharedLinksRegistry.Edit.Contended -> return false
            }
        }
        if (applied.isNotEmpty()) journal.remove(applied)
        return true
    }

    /** One step's look at every entry: what it found in the folder and the library, and what it changes. */
    private inner class Pass(
        private val folder: SyncFolder,
        private val index: IndexedNames,
        private val sharedFiles: Map<String, RemoteSharedFile>,
        private val isListingCurrent: Boolean,
        private val changes: SharedCopyChanges,
    ) {

        /** False where the entry could not be settled this time, which the next run tries again. */
        suspend fun settle(entry: SharedLinksEntry) = try {
            settleOrThrow(entry)
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SyncRunEndingException) {
            throw exception
        } catch (exception: SyncScopeMissingException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not update the shared copy ${entry.shareId}: ${exception::class.simpleName}")
            false
        }

        private suspend fun settleOrThrow(entry: SharedLinksEntry) {
            val kind = SharedLinkKind.fromId(entry.kind) ?: return
            val libraryKind = kind.libraryKind
            val indexed = index.find(libraryKind, entry.fileName)
            val localName = indexed?.key?.name ?: entry.fileName
            // A song shared alone is judged by its index entry and not read at all; a setlist is the one file read, for
            // the songs it names.
            val item = if (kind == SharedLinkKind.SETLIST || indexed == null) libraryFileLocalSource.readLibraryFile(libraryKind, localName) else null
            if ((kind == SharedLinkKind.SETLIST || indexed == null) && item == null) return orphan(entry)
            val remote = sharedFiles[entry.shareId] ?: return if (isListingCurrent) orphan(entry) else Unit
            val songNames = item?.takeIf { kind == SharedLinkKind.SETLIST }?.let {
                contents.setlistSongFileNames(it) ?: throw IllegalStateException("The setlist $localName cannot be read.")
            }
            val expected = sharedCopyFingerprint(
                kind = kind,
                fileName = entry.fileName,
                hash = item?.let(::localContentHash) ?: indexed!!.value.localHash,
                songs = songNames?.map { name -> name to index.find(LibraryFileKind.SONG, name)?.value?.localHash },
            )
            if (entry.isBuiltFrom(expected, remote)) {
                if (entry.state == SharedLinksEntry.STATE_ORPHANED) changes.update(entry.shareId) { it.copy(state = SharedLinksEntry.STATE_OK) }
                return
            }
            val itemBytes = item ?: libraryFileLocalSource.readLibraryFile(libraryKind, localName) ?: return orphan(entry)
            build(entry, kind, localName, itemBytes, songNames, remote)
        }

        private suspend fun build(
            entry: SharedLinksEntry,
            kind: SharedLinkKind,
            localName: String,
            itemBytes: ByteArray,
            songNames: List<String>?,
            remote: RemoteSharedFile,
        ) {
            // The same files, in the same order and under the same names, as the export that made the first copy
            // (ExportSetlistUseCase or ExportSongsUseCase, undated), so that a copy of unchanged files is the same bytes.
            val files = linkedMapOf(entry.fileName to itemBytes)
            val songs = songNames?.map { name ->
                val bytes = libraryFileLocalSource.readLibraryFile(LibraryFileKind.SONG, index.find(LibraryFileKind.SONG, name)?.key?.name ?: name)
                if (bytes != null && name !in files) files[name] = bytes
                name to bytes?.let(::localContentHash)
            }
            val inputs = sharedCopyFingerprint(kind = kind, fileName = entry.fileName, hash = localContentHash(itemBytes), songs = songs)
            val copy = contents.pack(files)
            if (copy.size > ImportLimits.MAX_IMPORT_SIZE) {
                // The file is left as it is, so the last version that fit keeps being served.
                changes.update(entry.shareId) {
                    it.copy(state = SharedLinksEntry.STATE_TOO_LARGE, built = SharedCopyBuild(inputs = inputs, build = buildNumber))
                }
                return
            }
            val hash = folder.contentHashOf(copy)
            val title = contents.titleOf(kind, localName, itemBytes)
            val isCurrent = hash != null && hash == remote.contentHash
            if (!isCurrent) {
                when (folder.uploadSharedFile(shareId = entry.shareId, bytes = copy, expectedRevision = remote.revision)) {
                    is RemoteWriteResult.Written -> Unit
                    // Another device uploaded in between, most likely the same bytes; whichever is newer, the next run
                    // compares against it.
                    RemoteWriteResult.Conflict -> return
                }
            }
            changes.update(entry.shareId) { current ->
                current.copy(
                    state = SharedLinksEntry.STATE_OK,
                    built = SharedCopyBuild(inputs = inputs, build = buildNumber, hash = hash),
                    title = title?.takeIf { it.isNotBlank() } ?: current.title,
                )
            }
        }

        /**
         * Whether the copy in the folder is the one [expected] builds: the same inputs, built by a build no older than
         * this one, and the very bytes then built, or, for a copy too large to share, none at all.
         */
        private fun SharedLinksEntry.isBuiltFrom(expected: String, remote: RemoteSharedFile): Boolean {
            val built = built ?: return false
            if (built.inputs != expected || built.build < buildNumber) return false
            return built.hash?.let { it == remote.contentHash } ?: (state == SharedLinksEntry.STATE_TOO_LARGE)
        }

        private fun orphan(entry: SharedLinksEntry) {
            if (entry.state != SharedLinksEntry.STATE_ORPHANED) changes.update(entry.shareId) { it.copy(state = SharedLinksEntry.STATE_ORPHANED) }
        }
    }

    /** The run's index by the identity of each name, since the registry and setlists may spell a file differently. */
    private class IndexedNames(index: Map<SyncKey, SyncIndexEntry>) {

        private val entries by lazy { index.entries.associateBy { (key, _) -> key.kind to LibraryFiles.identityKey(key.name) } }

        fun find(kind: LibraryFileKind, name: String) = entries[kind to LibraryFiles.identityKey(name)]
    }

    private val SharedLinkKind.libraryKind
        get() = when (this) {
            SharedLinkKind.SETLIST -> LibraryFileKind.SETLIST
            SharedLinkKind.SONG -> LibraryFileKind.SONG
        }
}
