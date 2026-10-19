/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.backup.JsonSongs
import com.pandulapeter.campfire.data.source.local.implementation.backup.SongbookProBackup
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.DosTimestamp
import com.pandulapeter.campfire.data.formats.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Unpacking and packing run on [Dispatchers.Default] rather than IO: nothing here touches the file system, it is all
 * inflating and packing in memory, and a big archive would otherwise block whichever thread asked for it.
 */
@Single
internal class ArchiveLocalSourceImpl(
    private val logger: Logger,
) : ArchiveLocalSource {

    override suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile> = withContext(Dispatchers.Default) {
        unpack(archive = archive, depth = 1, inflated = InflatedBytes(limit = maxSize))
    }

    override suspend fun songsIn(file: ImportedFile): List<ImportedFile>? = withContext(Dispatchers.Default) { foreignSongs(file) }

    override suspend fun pack(files: Map<String, ByteArray>): ByteArray = withContext(Dispatchers.Default) {
        ZipWriter.write(
            entries = files.map { (name, bytes) -> ZipEntry(name = name, bytes = bytes) },
            modifiedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { dateTime ->
                DosTimestamp.of(dateTime.year, dateTime.month.number, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second)
            },
        )
    }

    /**
     * The size limit is shared by the whole import through [inflated] rather than applied per archive: a nested archive
     * is inflated on top of the bytes its parent already holds in memory, and every level is kept until the end.
     */
    private fun unpack(archive: ByteArray, depth: Int, inflated: InflatedBytes): List<ImportedFile> {
        val content = ZipReader.read(
            archive = archive,
            maxTotalSize = inflated.limit - inflated.count,
            // Decided on the name and the declared size, before anything is inflated. What the archiving tool wrote for
            // itself is not part of what anybody chose to import. macOS packs an AppleDouble "._name.cho" next to every
            // entry and a ".DS_Store" into every directory, and the first of those carries the extension of the file it
            // belongs to: an archive of three hundred songs arrives as six hundred entries, half of them binary, and an
            // import that took them at their word would offer to import as many unreadable files as there are songs. A
            // file somebody means to import is never a hidden one, nor inside a hidden folder (a ".Trash" of deleted
            // songs, a ".dropbox.cache"), so these are never read: they cost nothing and cannot fail the archive.
            // SongbookPro keeps a whole library in one document, which is read whatever its size within the import's.
            // A name the import does not know, and a text name larger than any text, is peeked at instead: another app's
            // backup in a zipped cloud folder is a zip under a name of its own as often as not, and its chord sheets are
            // texts under that app's extension. What is never text by its name is not even peeked at.
            limitOf = { name, declaredSize ->
                val ownLimit = ImportLimits.maxSizeOf(name.fileName)
                when {
                    name.isHidden -> EntryPolicy.Skip
                    name.fileName.equals(SongbookProBackup.DATA_FILE_NAME, ignoreCase = true) -> EntryPolicy.Read(ImportLimits.MAX_IMPORT_SIZE)
                    ownLimit == 0L && LibraryFiles.isBinaryFileName(name.fileName) -> EntryPolicy.Skip
                    ownLimit == 0L -> EntryPolicy.Sniff
                    ImportLimits.isReadAsText(name.fileName) && declaredSize > ownLimit -> EntryPolicy.Sniff
                    else -> EntryPolicy.Read(ownLimit)
                }
            },
            // A zip is read within what is left of the import (the reader's own limit), and a text name that is no zip,
            // or an unknown one that reads as text, within the text limit, which a text over it is then reported against.
            sniffedLimitOf = { name, prefix ->
                when {
                    LibraryFiles.isZipArchive(prefix) -> ImportLimits.MAX_IMPORT_SIZE
                    ImportLimits.isReadAsText(name.fileName) -> ImportLimits.MAX_TEXT_FILE_SIZE
                    ImportLimits.maxSizeOf(name.fileName) == 0L && LibraryFiles.isTextLike(prefix) -> ImportLimits.MAX_TEXT_FILE_SIZE
                    else -> null
                }
            },
        )
        inflated.count += content.chargedSize
        // An entry does not say why it was read, so one under a name the import does not know is flagged here: what is
        // not an archive of such a name was read for being text.
        val entries = content.entries.map { entry ->
            val name = entry.name.fileName
            ImportedFile(
                name = name,
                bytes = entry.bytes,
                isPlainTextByContent = ImportLimits.maxSizeOf(name) == 0L && !LibraryFiles.isZipArchive(entry.bytes),
            )
        }
        // Another app's library is translated into the files of one of Campfire's own, and what else its archive holds is
        // that app's bookkeeping rather than anything the user would recognise in a report of files that were left out.
        // The entries that were not read are named to it too, since that bookkeeping is never read, and neither is a
        // document too large for the import: a backup whose library cannot be read is still one file left out, rather
        // than a song of its raw document and a list of names the user never saw.
        when (val backup = SongbookProBackup.read(entries + content.unread.map { ImportedFile.unread(it.name.fileName) })) {
            is SongbookProBackup.Result.Library -> return backup.files
            SongbookProBackup.Result.Unreadable -> return listOf(
                ImportedFile.unread(
                    name = SongbookProBackup.DATA_FILE_NAME,
                    isTooLarge = content.unread.any { it.name.fileName.equals(SongbookProBackup.DATA_FILE_NAME, ignoreCase = true) && it.reason == UnreadZipEntry.Reason.TOO_LARGE },
                ),
            )
            SongbookProBackup.Result.NotABackup -> Unit
        }
        val read = entries.flatMap { file ->
            // An archive by its name, or by its bytes under any name but a document's, as the import decides for a
            // picked file: a backup zipped up with the songs next to it is as often renamed as not.
            val isArchive = LibraryFiles.isArchiveFileName(file.name) || (!LibraryFiles.isDocumentFileName(file.name) && LibraryFiles.isZipArchive(file.bytes))
            if (depth < MAX_DEPTH && isArchive) {
                // A nested archive that cannot be read is reported rather than failing the whole import: the files
                // next to it are still perfectly good.
                try {
                    unpack(archive = file.bytes, depth = depth + 1, inflated = inflated)
                } catch (exception: Exception) {
                    logger.log("Could not unpack \"${file.name}\": ${exception.message}")
                    listOf(ImportedFile.unread(file.name))
                }
            } else {
                foreignSongs(file) ?: listOf(file)
            }
        }
        // Hidden entries stay unreported, see above; everything else that was not read is handed over by name.
        val unread = content.unread
            .filterNot { it.name.isHidden }
            .map { ImportedFile.unread(name = it.name.fileName, isTooLarge = it.reason == UnreadZipEntry.Reason.TOO_LARGE) }
        return read + unread
    }

    /**
     * The songs of another app's document that is not an archive, or null where [file] is not one or holds none, so
     * that it is imported as the file it is: a `.json` that is no setlist, or a text kept for its bytes that starts the
     * way JSON does. Tried after SongbookPro's backup, whose document is read by a reader of its own.
     */
    private fun foreignSongs(file: ImportedFile): List<ImportedFile>? {
        if (file.bytes.isEmpty()) return null
        val isJsonByName = file.name.endsWith(JSON_EXTENSION, ignoreCase = true) && !file.name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true)
        if (!isJsonByName && !file.isPlainTextByContent) return null
        val text = file.bytes.decodeLibraryText()
        val start = text.trimStart().firstOrNull()
        if (!isJsonByName && start != '{' && start != '[') return null
        return JsonSongs.read(text).ifEmpty { null }
    }

    /**
     * The library is flat, so "songs/x.cho" and "x.cho" are the same file as far as an import is concerned. A
     * backslash separates as well, since Windows PowerShell 5.1 writes one; no file name the library holds has one.
     */
    private val String.fileName get() = substringAfterLast('/').substringAfterLast('\\')

    /**
     * Whether the entry is a hidden file or sits inside a hidden folder, as the dropped-folder walks decide it. A "."
     * segment is the current directory, which some archivers write a path through, not a hidden folder.
     */
    private val String.isHidden get() = split('/', '\\').any { it != "." && LibraryFiles.isHiddenFileName(it) }

    /** How many bytes the archives of one import have inflated to so far, carried down the recursion, and the most they may. */
    private class InflatedBytes(val limit: Long) {
        var count = 0L
    }

    private companion object {
        const val MAX_DEPTH = 3
        const val JSON_EXTENSION = ".json"
    }
}
