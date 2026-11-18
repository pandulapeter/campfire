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
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.library.sharedCopyAttachments
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
 * A run's shared-copy step: brings every shared copy in the cloud folder up to
 * date with the library a completed run left, applies this device's journal of renames and stops, and writes what
 * changed into the registry as one edit at the end, also when it ends early, keeping what was done up to then.
 *
 * It costs nothing for an account that never shared (no registry) while the journal is empty, and only the listing
 * when nothing changed: the registry comes from the mirror where the listing names the revision it was read at, and a
 * copy whose `built` matches its inputs fingerprint ([sharedCopyFingerprint]) is neither read, built, nor uploaded.
 * [buildNumber] stands for the code that packs: a copy an older build made is built again once, one a newer build made
 * is left alone.
 *
 * A share is never stopped by inference: an item missing from the library only makes the entry `orphaned`, which keeps
 * serving the last version, and an item that comes back makes it `ok` again. A copy missing from the folder makes it
 * `orphaned` and `copyMissing` for good, since a deleted file's link never comes back; sharing the item again replaces
 * it.
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
    ): Boolean = journal.whileApplying {
        // Held from loading the journal until what was applied is removed: the applier must not take the same entries.
        val pending = journal.load()
        // A registry is looked at even with no copy listed: copies deleted by hand leave entries whose links open nothing.
        if (sharedFiles.isEmpty() && pending.isEmpty() && registryRevision == null) return@whileApplying true
        val (revision, document) = read(folder, registryRevision, pending.isEmpty()) ?: return@whileApplying true
        val changes = SharedCopyChanges()
        val applied = mutableListOf<SharedLinksJournal.Entry>()
        var isSettled = true
        val uploaded = mutableSetOf<String>()
        val failure = try {
            pending.forEach { entry ->
                changes.takeFromJournal(entry, folder, document)
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
                uploaded = uploaded,
            )
            val links = changes.applyTo(document).links
            links.forEach { entry ->
                if (!pass.settle(entry)) isSettled = false
            }
            logger.log("Sync: shared copies: ${links.size} links, ${applied.size} noted changes applied, ${uploaded.size} copies uploaded")
            null
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception
        }
        val isWritten = try {
            write(folder, document, changes, applied, uploaded)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (failure == null) throw exception
            logger.log("Could not write the shared links after a failed step: ${exception::class.simpleName}")
            false
        }
        failure?.let { throw it }
        isSettled && isWritten
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

    /**
     * Writes [changes] where they change anything, then forgets the journal entries [applied]; false where it gave up.
     * The copies of shares it stops, and those of [uploaded] that another device stopped or found gone since the step read
     * the list, are deleted again ([deleteStoppedCopies]).
     */
    private suspend fun write(
        folder: SyncFolder,
        document: SharedLinksDocument,
        changes: SharedCopyChanges,
        applied: List<SharedLinksJournal.Entry>,
        uploaded: Set<String>,
    ): Boolean {
        // An upload is followed by a read of the list even where it changes no entry (a copy replaced by hand, built again).
        if (changes.applyTo(document) != document || uploaded.isNotEmpty()) {
            // Any read naming a share no more, or naming its copy gone, is final, whatever the edit then answers: an entry
            // once gone never comes back, and a link whose copy was deleted never serves one uploaded again (measured).
            val stoppedElsewhere = mutableSetOf<String>()
            val edit = registry.edit(folder) { current ->
                stoppedElsewhere += uploaded.filter { shareId -> current.links.none { it.shareId == shareId && !it.copyMissing } }
                SharedLinksRegistry.Change(document = changes.applyTo(current).takeIf { it != current }, result = Unit)
            }
            folder.deleteStoppedCopies(stoppedElsewhere, logger)
            when (edit) {
                is SharedLinksRegistry.Edit.Done -> folder.deleteStoppedCopies(changes.removed, logger)
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
        private val uploaded: MutableSet<String>,
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
            val remote = sharedFiles[entry.shareId]
            // A deleted copy's link never comes back, whatever the library holds. Since that is for good, a copy only
            // the listing misses is asked about first: a listing may leave out what the folder holds.
            if (remote == null && isListingCurrent && (entry.copyMissing || !folder.holdsSharedCopy(entry.shareId))) {
                return markCopyMissing(entry)
            }
            // Nor does a copy put back by hand: the entry stays `orphaned`, as a version that knows no `copyMissing`
            // shows it, until sharing the item again replaces it.
            if (entry.copyMissing) return
            val libraryKind = kind.libraryKind
            val indexed = index.find(libraryKind, entry.fileName)
            val localName = indexed?.key?.name ?: entry.fileName
            // A song shared alone is judged by its index entry and not read for that; a setlist is read for the songs it
            // names. Their songs are read only for the pictures they name, below.
            val item = if (kind == SharedLinkKind.SETLIST || indexed == null) libraryFileLocalSource.readLibraryFile(libraryKind, localName) else null
            if ((kind == SharedLinkKind.SETLIST || indexed == null) && item == null) return orphan(entry)
            // A share newer than the listing, which could not have seen its copy.
            if (remote == null) return
            val songNames = item?.takeIf { kind == SharedLinkKind.SETLIST }?.let {
                contents.setlistSongFileNames(it) ?: throw IllegalStateException("The setlist $localName cannot be read.")
            }
            // The pictures are the one input the index cannot tell from a song's hash, since one a song names may have
            // arrived since, so the songs are read for the names they give, but only where the library holds any picture.
            val images = if (!index.hasImages) {
                emptyList()
            } else if (kind == SharedLinkKind.SETLIST) {
                filesNamedBy(songNames.orEmpty().mapNotNull { name -> libraryFileLocalSource.readLibraryFile(LibraryFileKind.SONG, index.localNameOf(name)) })
            } else {
                filesNamedBy(listOfNotNull(item ?: libraryFileLocalSource.readLibraryFile(libraryKind, localName)))
            }
            val expected = sharedCopyFingerprint(
                kind = kind,
                fileName = entry.fileName,
                hash = item?.let(::localContentHash) ?: indexed!!.value.localHash,
                songs = songNames?.map { name -> name to index.find(LibraryFileKind.SONG, name)?.value?.localHash },
                images = images,
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
            // The export reads the item and its songs as text and writes them as UTF-8, a byte order mark dropped, so they
            // go in here the same way rather than as the bytes on disk.
            val files = linkedMapOf(entry.fileName to itemBytes.asExportedText())
            val songTexts = mutableListOf<ByteArray>()
            val songs = songNames?.map { name ->
                val bytes = libraryFileLocalSource.readLibraryFile(LibraryFileKind.SONG, index.localNameOf(name))
                if (bytes != null && name !in files) {
                    files[name] = bytes.asExportedText()
                    songTexts += bytes
                }
                name to bytes?.let(::localContentHash)
            }
            // Then the pictures and attachments under images/, in the order of their names, as the export puts them. The
            // pictures are read one at a time and no further than the limit: a setlist of photos may name more than a
            // phone has room for. The attachments (files no song of the copy shows) are read in the order the songs name
            // them, as far as their own budget goes, each one past it left out and let go of at once, as the export picks
            // them (sharedCopyAttachments).
            val texts = if (kind == SharedLinkKind.SONG) listOf(itemBytes) else songTexts
            val intended = filesNamedBy(texts)
            val shown = texts.flatMapTo(hashSetOf()) { contents.pictureNamesOf(it) }
            val attachments = texts.flatMap { contents.attachmentNamesOf(it) }.filter { it in intended && it !in shown }
            val read = mutableMapOf<String, ByteArray>()
            var size = files.values.sumOf { it.size.toLong() }
            for (name in intended) {
                if (name in attachments) continue
                if (size > ImportLimits.MAX_SHARED_COPY_SIZE) break
                val bytes = readFile(name) ?: continue
                read[name] = bytes
                size += bytes.size
            }
            val isTooLarge = size > ImportLimits.MAX_SHARED_COPY_SIZE
            val leftOut = if (isTooLarge) {
                emptySet()
            } else {
                sharedCopyAttachments(names = attachments, onLeftOut = { read.remove(it) }) { name ->
                    readFile(name)?.also { read[name] = it }?.size?.toLong()
                }.leftOut
            }
            // Its songs keep no line for an attachment the copy leaves out, as the share's export writes them (ExportedImages).
            if (leftOut.isNotEmpty()) {
                files.keys.filter { kind == SharedLinkKind.SONG || it != entry.fileName }.forEach { name ->
                    files[name] = contents.withoutAttachments(files.getValue(name), leftOut)
                }
            }
            intended.forEach { name -> read[name]?.let { files[IMAGES_FOLDER + name] = it } }
            // A copy too large is judged by what it was to hold, so that the next run finds nothing changed and reads no
            // picture again; one that fits by what it holds and what it left out for its size, so that a file it could
            // not read is tried again.
            val inputs = sharedCopyFingerprint(
                kind = kind,
                fileName = entry.fileName,
                hash = localContentHash(itemBytes),
                songs = songs,
                images = if (isTooLarge) intended else intended.filter { it in read || it in leftOut },
            )
            val copy = if (isTooLarge) null else contents.pack(files)
            // What every version opening a link downloads, and what the copy and its files may hold in memory here at once.
            if (copy == null || copy.size > ImportLimits.MAX_SHARED_COPY_SIZE + ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE) {
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
                    is RemoteWriteResult.Written -> uploaded += entry.shareId
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
                    leftOutAttachments = leftOut.size,
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

        /**
         * The library's files [songs] name, pictures and attachments, by the run's index, in the order of their names: what
         * a copy carries besides its songs, and what its fingerprint names.
         */
        private suspend fun filesNamedBy(songs: List<ByteArray>) = songs.flatMap { contents.libraryFileNamesOf(it) }.toSet()
            .filter { LibraryFiles.isLibraryFileName(it) && index.find(LibraryFileKind.FILE, it) != null }
            .sorted()

        private suspend fun readFile(name: String) =
            libraryFileLocalSource.readLibraryFile(LibraryFileKind.FILE, index.localNameOf(name, LibraryFileKind.FILE))

        private fun orphan(entry: SharedLinksEntry) {
            if (entry.state != SharedLinksEntry.STATE_ORPHANED) changes.update(entry.shareId) { it.copy(state = SharedLinksEntry.STATE_ORPHANED) }
        }

        private fun markCopyMissing(entry: SharedLinksEntry) {
            if (entry.copyMissing && entry.state == SharedLinksEntry.STATE_ORPHANED) return
            changes.update(entry.shareId) { it.copy(state = SharedLinksEntry.STATE_ORPHANED, copyMissing = true) }
        }
    }

    /** The run's index by the identity of each name, since the registry and setlists may spell a file differently. */
    private class IndexedNames(index: Map<SyncKey, SyncIndexEntry>) {

        private val entries by lazy { index.entries.associateBy { (key, _) -> key.kind to LibraryFiles.identityKey(key.name) } }

        fun find(kind: LibraryFileKind, name: String) = entries[kind to LibraryFiles.identityKey(name)]

        /** This device's spelling of [name], where the index knows the file under another one. */
        fun localNameOf(name: String, kind: LibraryFileKind = LibraryFileKind.SONG) = find(kind, name)?.key?.name ?: name

        /** Whether the library held any picture at the end of the run, without which no song shows one. */
        val hasImages by lazy { entries.keys.any { (kind, _) -> kind == LibraryFileKind.FILE } }
    }

    /** A library text file as the export writes it: read as the app reads text, then written as UTF-8. */
    private fun ByteArray.asExportedText() = decodeLibraryText().encodeToByteArray()

    private val SharedLinkKind.libraryKind
        get() = when (this) {
            SharedLinkKind.SETLIST -> LibraryFileKind.SETLIST
            SharedLinkKind.SONG -> LibraryFileKind.SONG
        }

    private companion object {
        /** Where an archive keeps its pictures, as `ExportedImages` writes them. */
        const val IMAGES_FOLDER = "images/"
    }
}
