/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ByteArrayEntrySink
import com.pandulapeter.campfire.data.formats.zip.ByteArrayZipSource
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.EntrySink
import com.pandulapeter.campfire.data.formats.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipDirectory
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.formats.zip.ZipSource
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.backup.OpenSongArchive
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveEntryPolicy.fileName
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveEntryPolicy.isHidden
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.staging.StagingEntrySink
import com.pandulapeter.campfire.data.source.local.implementation.staging.StagingRequest
import com.pandulapeter.campfire.data.source.local.implementation.staging.UNKNOWN_TYPE_EXTENSION
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One unpack of an archive in the staging folder, the in-memory unpack's decisions ([ArchiveEntryPolicy],
 * [ArchiveSongImages]) over the streaming reader: the song texts are read into memory within [maxSize] as there, while
 * the pictures and attachments songs name are inflated into staging files of [request], and so is every archive nested
 * above [ArchiveEntryPolicy.MAX_DEPTH], walked from there and deleted once walked. An archive at the last level is read
 * into memory within the songs' budget, as the in-memory unpack reads it, and so is one under a text's name that turned
 * out to be one, which that budget already holds.
 *
 * What differs is only what bounds the files on disk: a nested archive counts against the disk budget instead of the
 * songs', and pictures and attachments against the disk budget instead of their in-memory budgets (each file still
 * within its own limit), reserved from the sizes the archive declares before a picture or attachment walk starts. Across
 * every archive of the unpack, nested ones included, at most [ImportLimits.MAX_ARCHIVE_ENTRIES] central directory records
 * are read, and the directories held at once (an archive's, and those of the archives nested in it it is unpacking) add
 * up to at most [ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE]; a nested archive past either is reported unread, as a damaged
 * one is.
 *
 * Every byte read from a staged archive is told to [onProgress], against the sizes of the staged archives reached so far:
 * almost all of an unpack's time goes into reading and inflating them, in 64 KiB pieces.
 *
 * Not safe for concurrent use, and used once.
 */
internal class StreamingArchiveUnpack(
    private val fileStorage: FileStorage,
    private val staging: ImportStaging,
    private val logger: Logger,
    private val request: StagingRequest,
    private val maxSize: Long,
    private val onProgress: (readBytes: Long, totalBytes: Long) -> Unit,
) {
    private var readBytes = 0L
    private var totalBytes = 0L
    private var textCharged = 0L
    private var imageCharged = 0L
    private var attachmentCharged = 0L
    private var recordsRead = 0L
    private var directoriesHeld = 0L
    private val stagedArchives = mutableSetOf<StagedFile>()
    private val stagedFiles = mutableSetOf<StagedFile>()

    /** The unpacked files the request held before this unpack, which a file it stages may turn out to be. */
    private var heldBefore = emptySet<String>()

    /**
     * Unpacks [archive], which stays where it is. Whatever ends the unpack other than its result (a damaged outer
     * archive, the storage, the disk budget, a cancellation) first deletes every file it staged, so that nothing of it
     * is left behind but what the request held before.
     */
    suspend fun unpack(archive: StagedFile): UnpackedArchive {
        heldBefore = staging.unpackedNamesOf(request)
        val files = try {
            unpackStaged(archive, depth = 1)
        } catch (throwable: Throwable) {
            deleteStagedSince(files = emptySet(), archives = emptySet())
            throw throwable
        }
        return UnpackedArchive(files = files, imageSize = imageCharged, attachmentSize = attachmentCharged)
    }

    /** One archive's entries as read, before the archives among them are unpacked; see [expand]. */
    private class Level(val files: List<ImportedFile>, val unread: List<ImportedFile>, val directorySize: Long)

    /** An entry the first walk read: into memory ([bytes]), or into the staging folder as an archive ([staged]). */
    private class Entry(val name: String, val bytes: ByteArray?, val staged: StagedFile?) {

        fun toImportedFile() = staged?.let { ImportedFile(name = name.fileName, bytes = ByteArray(0), staged = it) }
            ?: ArchiveEntryPolicy.importedFileOf(name, bytes ?: ByteArray(0))
    }

    private suspend fun unpackStaged(archive: StagedFile, depth: Int): List<ImportedFile> {
        val source = fileStorage.openSource(StorageDirectory.IMPORTS, archive.name)
            ?: throw LibraryStorageException("The staged archive \"${archive.name}\" is gone.")
        totalBytes += source.size
        onProgress(readBytes, totalBytes)
        // Closed before the archives nested in it are unpacked: nothing more is read from it.
        val level = try {
            read(CountedZipSource(FileZipSource(source)), depth)
        } finally {
            withContext(NonCancellable) { source.close() }
        }
        return expand(level, depth)
    }

    /**
     * Opens the archive within what is left of both caps. A directory stays charged until the archives nested in it are
     * unpacked ([expand]), whether or not its arrays are still reachable then, so that nesting never multiplies the cap.
     */
    private suspend fun read(source: ZipSource, depth: Int): Level {
        val directory = ZipReader.open(
            source = source,
            maxCentralDirectorySize = ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE - directoriesHeld,
            maxEntries = (ImportLimits.MAX_ARCHIVE_ENTRIES - recordsRead).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        )
        recordsRead += directory.recordCount
        directoriesHeld += directory.centralDirectorySize
        return try {
            readEntries(directory, depth)
        } catch (throwable: Throwable) {
            directoriesHeld -= directory.centralDirectorySize
            throw throwable
        }
    }

    private suspend fun readEntries(directory: ZipDirectory, depth: Int): Level {
        val walk = FirstWalk(directory, stagesArchives = depth < ArchiveEntryPolicy.MAX_DEPTH)
        val unread = walk.walk()
        val entries = walk.entries()
        val read = entries.map { it.toImportedFile() }
        ArchiveEntryPolicy.backupOf(read, unread)?.let { backup ->
            entries.mapNotNull { it.staged }.forEach { deleteArchive(it) }
            return Level(files = backup, unread = emptyList(), directorySize = directory.centralDirectorySize)
        }
        val openSong = OpenSongArchive.read(entries.map { it.name }, read)
        val files = openSong?.files ?: read
        val images = songImages(directory, entries, unread, walk.declaredSizes)
        // A song naming an archive as its picture or attachment took it, read again as one of those: it is not unpacked.
        entries.filter { it.staged != null && it.name in images.takenEntryNames }.forEach { deleteArchive(it.staged!!) }
        return Level(
            files = ArchiveEntryPolicy.withSongImages(files, entries.map { it.name }, images),
            unread = openSong?.leftOut.orEmpty() + ArchiveEntryPolicy.unreadFilesOf(unread, images.takenEntryNames),
            directorySize = directory.centralDirectorySize,
        )
    }

    private suspend fun expand(level: Level, depth: Int): List<ImportedFile> = try {
        level.files.flatMap { file ->
            val staged = file.staged
            when {
                staged != null -> nested(file.name) {
                    try {
                        unpackStaged(staged, depth + 1)
                    } finally {
                        deleteArchive(staged)
                    }
                }

                depth < ArchiveEntryPolicy.MAX_DEPTH && ArchiveEntryPolicy.isArchive(file) -> nested(file.name) {
                    expand(read(ByteArrayZipSource(file.bytes), depth + 1), depth + 1)
                }

                else -> ArchiveEntryPolicy.foreignSongs(file) ?: listOf(file)
            }
        } + level.unread
    } finally {
        directoriesHeld -= level.directorySize
    }

    /**
     * A nested archive that cannot be read is reported rather than failing the whole import, as the in-memory unpack
     * reports it, and what it staged before it failed is deleted. Only what is not the archive's fault ends the unpack:
     * the storage, the disk, the request, a cancellation.
     */
    private suspend fun nested(name: String, unpack: suspend () -> List<ImportedFile>): List<ImportedFile> {
        val filesBefore = stagedFiles.toSet()
        val archivesBefore = stagedArchives.toSet()
        return try {
            unpack()
        } catch (exception: Exception) {
            if (exception is CancellationException || exception.isNotTheArchives()) throw exception
            deleteStagedSince(files = filesBefore, archives = archivesBefore)
            logger.log("Could not unpack \"$name\": ${exception.message}")
            listOf(ImportedFile.unread(name))
        }
    }

    private fun Exception.isNotTheArchives() =
        this is StagingRefusedException || this is LibraryStorageException || isOutOfSpace() || !staging.isLive(request)

    /**
     * Deletes every archive staged since [archives] and not deleted yet, and every file staged since [files] but those
     * the request held before this unpack.
     */
    private suspend fun deleteStagedSince(files: Set<StagedFile>, archives: Set<StagedFile>) = withContext(NonCancellable) {
        (stagedArchives - archives).forEach { deleteArchive(it) }
        val since = stagedFiles - files
        stagedFiles -= since
        since.filterNot { it.name in heldBefore }.forEach { staging.deleteFile(request, it) }
    }

    private suspend fun deleteArchive(archive: StagedFile) {
        stagedArchives -= archive
        staging.deleteFile(request, archive)
    }

    /**
     * The pictures and attachments the songs among [entries] name, staged from [directory] after the disk budget was
     * reserved for every entry the walks may read, as declared.
     */
    private suspend fun songImages(
        directory: ZipDirectory,
        entries: List<Entry>,
        unread: List<UnreadZipEntry>,
        declaredSizes: LongArray,
    ): ArchiveSongImages.Found {
        val wanted = ArchiveSongImages.wanted(entries.map { it.name to it.bytes }, unread.map { it.name }) { it.isHidden }
            ?: return ArchiveSongImages.Found(entries.map { emptyList() }, emptySet(), 0, 0)
        var reserved = 0L
        for (index in 0 until directory.entryCount) {
            val limit = when (directory.nameOf(index)) {
                in wanted.pictures -> ImportLimits.MAX_IMAGE_FILE_SIZE
                in wanted.attachments -> ImportLimits.MAX_ATTACHMENT_FILE_SIZE
                else -> continue
            }
            // One whose data cannot be what it declares is left out unread by the walk, and is no reason to refuse the import.
            if (declaredSizes[index] <= limit && directory.canProduceDeclaredSize(index)) reserved += declaredSizes[index]
        }
        val staged = mutableMapOf<String, StagedFile>()
        val unreadByName = mutableMapOf<String, UnreadZipEntry.Reason>()
        var pictureSize = 0L
        var attachmentSize = 0L
        try {
            if (reserved > 0) staging.reserve(request, reserved)
            pictureSize = stageWalk(directory, wanted.pictures, isAttachment = false, staged, unreadByName)
            attachmentSize = stageWalk(directory, wanted.attachments, isAttachment = true, staged, unreadByName)
        } finally {
            // What an entry left unread or never reached was reserved for would otherwise stay reserved until the
            // request settles, keeping a share arriving meanwhile from the room.
            staging.releaseUnused(request)
        }
        imageCharged += pictureSize
        attachmentCharged += attachmentSize
        return ArchiveSongImages.found(
            wanted = wanted,
            contentByName = wanted.readFirst.mapValues { IncomingContent.Bytes(it.value) } + staged.mapValues { IncomingContent.Staged(it.value) },
            unreadByName = unreadByName,
            chargedSize = pictureSize,
            chargedAttachmentSize = attachmentSize,
        )
    }

    /** One walk of [directory] staging the entries among [wanted]; answers what they cost as declared. */
    private suspend fun stageWalk(
        directory: ZipDirectory,
        wanted: Set<String>,
        isAttachment: Boolean,
        staged: MutableMap<String, StagedFile>,
        unreadByName: MutableMap<String, UnreadZipEntry.Reason>,
    ): Long {
        if (wanted.isEmpty()) return 0
        val walk = directory.walk(maxTotalSize = Long.MAX_VALUE, limitOf = ArchiveSongImages.policyOf(wanted, isAttachment)) { name, declaredSize ->
            StagingEntrySink(staging, staging.openUnpacked(request, stagedExtensionOf(name), declaredSize)) { file ->
                stagedFiles += file
                staged[name] = file
            }
        }
        walk.unread.filter { it.name in wanted }.forEach { unreadByName[it.name] = it.reason }
        return walk.chargedSize
    }

    /** The extension a staged file keeps of its entry's name: the one it has where the library keeps it, else none. */
    private fun stagedExtensionOf(name: String) = name.fileName.substringAfterLast('.', "").lowercase().let { ".$it" }
        .takeIf { it in LibraryFiles.ATTACHMENT_EXTENSIONS } ?: UNKNOWN_TYPE_EXTENSION

    /**
     * The first walk of a directory: [ArchiveEntryPolicy]'s decisions, with an archive staged where [stagesArchives]
     * rather than read into memory, and everything else held to what is left of the songs' budget, which the archive
     * staged no longer counts against. The walk asks an entry's limit, then peeks at it, then asks for its sink, one
     * entry after the other, so what the first two decide is the third's to act on.
     */
    private inner class FirstWalk(private val directory: ZipDirectory, private val stagesArchives: Boolean) {

        /** Each file entry's declared size, by its index in [directory]. */
        val declaredSizes = LongArray(directory.entryCount)

        private var index = 0
        private var isArchive = false
        private val pending = mutableListOf<Pending>()

        /** Walks the directory and answers the entries it did not read. */
        suspend fun walk() = directory.walk(
            maxTotalSize = Long.MAX_VALUE,
            limitOf = ::limitOf,
            sniffedLimitOf = ::sniffedLimitOf,
            // Charged as the reader charges it, before it is read: a damaged entry has cost what it declared all the
            // same, one whose data cannot even be that size too, which the walk leaves out without asking for a sink.
            onCharged = { _, declaredSize -> if (!isArchive) textCharged += declaredSize },
            sinkOf = ::sinkOf,
        ).unread

        /** The entries read whole, in the directory's order. */
        fun entries() = pending.mapNotNull { entry ->
            when {
                entry.memory?.isClosed == true -> Entry(entry.name, entry.memory.bytes(), null)
                entry.staged != null -> Entry(entry.name, null, entry.staged)
                else -> null
            }
        }

        private fun limitOf(name: String, declaredSize: Long): EntryPolicy {
            declaredSizes[index++] = declaredSize
            isArchive = false
            val policy = ArchiveEntryPolicy.limitOf(name, declaredSize)
            return when {
                policy !is EntryPolicy.Read -> policy
                isStaged(LibraryFiles.isArchiveFileName(name.fileName)) -> EntryPolicy.Read(ImportLimits.MAX_STAGED_ARCHIVE_SIZE).also { isArchive = true }
                else -> EntryPolicy.Read(minOf(policy.limit, maxSize - textCharged))
            }
        }

        private fun sniffedLimitOf(name: String, prefix: ByteArray): Long? {
            val limit = ArchiveEntryPolicy.sniffedLimitOf(name, prefix) ?: return null
            return when {
                isStaged(LibraryFiles.isZipArchive(prefix)) -> {
                    isArchive = true
                    ImportLimits.MAX_STAGED_ARCHIVE_SIZE
                }
                // Held in memory, as the in-memory unpack holds every entry, whose reader cannot even peek at one declaring
                // 2 GiB or more and leaves it out as not wanted: reported as too large here, the same entry would be
                // listed under another heading of the import's report on each path.
                declaredSizes[index - 1] > Int.MAX_VALUE -> null
                else -> minOf(limit, maxSize - textCharged)
            }
        }

        /**
         * Whether the current entry, an archive by [isArchiveEntry], is staged rather than read into memory. Only where
         * its data could be the size it declares: room is set aside for an archive as it declares itself, and one that
         * cannot be that size is held to the in-memory unpack's rule instead, which leaves it out as that one does.
         */
        private fun isStaged(isArchiveEntry: Boolean) = stagesArchives && isArchiveEntry && directory.canProduceDeclaredSize(index - 1)

        private suspend fun sinkOf(name: String, declaredSize: Long): EntrySink = if (isArchive) {
            val entry = Pending(name, memory = null).also { pending += it }
            StagingEntrySink(staging, staging.openArchive(request, announcedSize = declaredSize)) { file ->
                stagedArchives += file
                entry.staged = file
            }
        } else {
            ByteArrayEntrySink(declaredSize).also { pending += Pending(name, memory = it) }
        }
    }

    /** [source] telling [onProgress] what it reads, as many bytes as it hands out, held to the total. */
    private inner class CountedZipSource(private val source: ZipSource) : ZipSource {

        override val size get() = source.size

        override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
            source.read(position, buffer, offset, length)
            readBytes = minOf(readBytes + length, totalBytes)
            onProgress(readBytes, totalBytes)
        }
    }

    /** An entry the first walk is reading, into [memory] or, once closed, into [staged]. */
    private class Pending(val name: String, val memory: ByteArrayEntrySink?) {
        var staged: StagedFile? = null
    }
}
