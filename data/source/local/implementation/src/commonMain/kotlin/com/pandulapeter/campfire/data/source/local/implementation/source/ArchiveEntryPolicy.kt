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

import com.pandulapeter.campfire.chordpro.abc.AbcText
import com.pandulapeter.campfire.data.formats.zip.EntryPolicy
import com.pandulapeter.campfire.data.formats.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.implementation.backup.AbcFile
import com.pandulapeter.campfire.data.source.local.implementation.backup.JsonSongs
import com.pandulapeter.campfire.data.source.local.implementation.backup.OnSongFile
import com.pandulapeter.campfire.data.source.local.implementation.backup.OpenSongSong
import com.pandulapeter.campfire.data.source.local.implementation.backup.OpenSongSet
import com.pandulapeter.campfire.data.source.local.implementation.backup.SongbookProBackup

/**
 * What an import makes of an archive's entries, decided the same way however the archive is read (in memory, or
 * streamed from the staging folder), so that the two readings cannot drift apart: which entries are read, under which
 * limits, what each one becomes, and what is reported of those that were not read.
 */
internal object ArchiveEntryPolicy {

    /** How deep archives inside archives are unpacked, so that a zip of zips of zips cannot make the app work forever. */
    const val MAX_DEPTH = 3

    private const val JSON_EXTENSION = ".json"
    private const val XML_EXTENSION = ".xml"

    /**
     * Decided on the name and the declared size, before anything is inflated. What the archiving tool wrote for
     * itself is not part of what anybody chose to import. macOS packs an AppleDouble "._name.cho" next to every
     * entry and a ".DS_Store" into every directory, and the first of those carries the extension of the file it
     * belongs to: an archive of three hundred songs arrives as six hundred entries, half of them binary, and an
     * import that took them at their word would offer to import as many unreadable files as there are songs. A
     * file somebody means to import is never a hidden one, nor inside a hidden folder (a ".Trash" of deleted
     * songs, a ".dropbox.cache"), so these are never read: they cost nothing and cannot fail the archive.
     * SongbookPro keeps a whole library in one document, which is read whatever its size within the import's.
     * A name the import does not know, and a text name larger than any text, is peeked at instead: another app's
     * backup in a zipped cloud folder is a zip under a name of its own as often as not, and its chord sheets are
     * texts under that app's extension. What is never text by its name is not even peeked at. A file named by its
     * content as the library names an attachment is one a song of Campfire's export carries, which is read for
     * that song only (ArchiveSongImages), within the attachments' budget: a PDF or a text among them would
     * otherwise be read here as a document of its own, at the cost of the songs' budget.
     */
    fun limitOf(name: String, declaredSize: Long): EntryPolicy {
        val ownLimit = ImportLimits.maxSizeOf(name.fileName)
        return when {
            name.isHidden -> EntryPolicy.Skip
            LibraryFiles.isLibraryAttachmentName(name.fileName) && !LibraryFiles.isLibraryImageName(name.fileName) -> EntryPolicy.Skip
            name.fileName.equals(SongbookProBackup.DATA_FILE_NAME, ignoreCase = true) -> EntryPolicy.Read(ImportLimits.MAX_IMPORT_SIZE)
            ownLimit == 0L && LibraryFiles.isBinaryFileName(name.fileName) -> EntryPolicy.Skip
            ownLimit == 0L -> EntryPolicy.Sniff
            ImportLimits.isReadAsText(name.fileName) && declaredSize > ownLimit -> EntryPolicy.Sniff
            else -> EntryPolicy.Read(ownLimit)
        }
    }

    /**
     * A zip is read within what is left of the import (the reader's own limit), and a text name that is no zip,
     * or an unknown one that reads as text, within the text limit, which a text over it is then reported against.
     */
    fun sniffedLimitOf(name: String, prefix: ByteArray): Long? = when {
        LibraryFiles.isZipArchive(prefix) -> ImportLimits.MAX_IMPORT_SIZE
        ImportLimits.isReadAsText(name.fileName) -> ImportLimits.MAX_TEXT_FILE_SIZE
        ImportLimits.maxSizeOf(name.fileName) == 0L && LibraryFiles.isTextLike(prefix) -> ImportLimits.MAX_TEXT_FILE_SIZE
        else -> null
    }

    /**
     * The entry [entryName] read as [bytes]. An entry does not say why it was read, so one under a name the import does
     * not know is flagged here: what is not an archive of such a name was read for being text.
     */
    fun importedFileOf(entryName: String, bytes: ByteArray): ImportedFile {
        val name = entryName.fileName
        return ImportedFile(
            name = name,
            bytes = bytes,
            isPlainTextByContent = ImportLimits.maxSizeOf(name) == 0L && !LibraryFiles.isZipArchive(bytes),
        )
    }

    /**
     * Another app's library is translated into the files of one of Campfire's own, and what else its archive holds is
     * that app's bookkeeping rather than anything the user would recognise in a report of files that were left out.
     * The entries that were not read are named to it too, since that bookkeeping is never read, and neither is a
     * document too large for the import: a backup whose library cannot be read is still one file left out, rather
     * than a song of its raw document and a list of names the user never saw. Null where [entries] are no backup.
     */
    fun backupOf(entries: List<ImportedFile>, unread: List<UnreadZipEntry>): List<ImportedFile>? =
        when (val backup = SongbookProBackup.read(entries + unread.map { ImportedFile.unread(it.name.fileName) })) {
            is SongbookProBackup.Result.Library -> backup.files
            SongbookProBackup.Result.Unreadable -> listOf(
                ImportedFile.unread(
                    name = SongbookProBackup.DATA_FILE_NAME,
                    isTooLarge = unread.any { it.name.fileName.equals(SongbookProBackup.DATA_FILE_NAME, ignoreCase = true) && it.reason == UnreadZipEntry.Reason.TOO_LARGE },
                ),
            )
            SongbookProBackup.Result.NotABackup -> null
        }

    /**
     * An entry a song names as its picture or attachment is that song's, never a file of its own to import: a song may
     * attach a text or a PDF under any name it likes. [entryNames] are the names [files] were read under, in their order.
     */
    fun withSongImages(files: List<ImportedFile>, entryNames: List<String>, images: ArchiveSongImages.Found) =
        files.mapIndexedNotNull { index, file ->
            if (entryNames[index] in images.takenEntryNames) return@mapIndexedNotNull null
            images.imagesByEntry[index].takeIf { it.isNotEmpty() }?.let { file.copy(images = it) } ?: file
        }

    /**
     * An archive by its name, or by its bytes under any name but a document's, as the import decides for a
     * picked file: a backup zipped up with the songs next to it is as often renamed as not.
     */
    fun isArchive(file: ImportedFile) =
        LibraryFiles.isArchiveFileName(file.name) || (!LibraryFiles.isDocumentFileName(file.name) && LibraryFiles.isZipArchive(file.bytes))

    /**
     * Hidden entries stay unreported, see [limitOf], and so do the pictures and attachments songs took ([takenEntryNames]),
     * which their songs report; everything else that was not read is handed over by name.
     */
    fun unreadFilesOf(unread: List<UnreadZipEntry>, takenEntryNames: Set<String>) = unread
        .filterNot { it.name.isHidden || it.name in takenEntryNames }
        .map { ImportedFile.unread(name = it.name.fileName, isTooLarge = it.reason == UnreadZipEntry.Reason.TOO_LARGE) }

    /**
     * The songs of another app's document that is not an archive, or null where [file] is not one or holds none, so
     * that it is imported as the file it is: a `.json` that is no setlist or a JustChords `.justchords`, or a text kept
     * for its bytes that starts the way JSON does, and an OpenSong song, a text kept for its bytes (OpenSong names its
     * songs without an extension) or an `.xml` that starts the way XML does, an OnSong `.onsong`, and an ABC `.abc` or a
     * text kept for its bytes that reads as ABC, one song per tune. An OpenSong set (an `.osts`, or a text kept for its
     * bytes or an `.xml` whose root is a set) is the setlist it translates into, its songs found by title
     * (`OpenSongSet.filesOf`). Tried after SongbookPro's backup, whose document is read by a reader of its own. A song is
     * handed over as a `.cho` of the name it came under (an ABC tune's or a JSON song's own title), which is what it is
     * titled by where it names no title itself, and which the import counts as a song where it decides whether an
     * archive known only by its bytes held anything to import.
     */
    fun foreignSongs(file: ImportedFile): List<ImportedFile>? {
        if (file.bytes.isEmpty()) return null
        // Read by its name, but a photo renamed .onsong is still a photo, and is left to be reported as skipped.
        if (LibraryFiles.FOREIGN_SONG_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }) {
            if (!LibraryFiles.isTextLike(file.bytes.copyOf(minOf(file.bytes.size, ImportLimits.SNIFF_LENGTH)))) return null
            val text = file.bytes.decodeLibraryText()
            if (file.name.endsWith(LibraryFiles.ABC_EXTENSION, ignoreCase = true)) return AbcFile.read(text, fallbackTitle = file.baseName).ifEmpty { null }
            val song = OnSongFile.read(text, fallbackTitle = file.baseName)
            return listOf(ImportedFile(name = file.baseName + LibraryFiles.SONG_EXTENSION, bytes = song.encodeToByteArray()))
        }
        if (LibraryFiles.FOREIGN_SETLIST_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }) {
            return OpenSongSet.filesOf(file, file.bytes.decodeLibraryText())
        }
        val isJsonByName = (file.name.endsWith(JSON_EXTENSION, ignoreCase = true) && !file.name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true)) ||
            LibraryFiles.FOREIGN_JSON_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) }
        val isXmlByName = file.name.endsWith(XML_EXTENSION, ignoreCase = true)
        if (!isJsonByName && !isXmlByName && !file.isPlainTextByContent) return null
        val text = file.bytes.decodeLibraryText()
        // An ABC tune has no structure the chord sheet reader knows, so a text kept for its bytes would be skipped.
        if (file.isPlainTextByContent && AbcText.isAbc(text)) return AbcFile.read(text, fallbackTitle = file.baseName).ifEmpty { null }
        return when (text.trimStart().firstOrNull()) {
            '{', '[' -> if (isXmlByName) null else JsonSongs.read(text).ifEmpty { null }
            '<' -> when {
                isJsonByName -> null
                OpenSongSet.isSet(text) -> OpenSongSet.filesOf(file, text)
                else -> OpenSongSong.read(text, fallbackTitle = file.baseName)?.let { song ->
                    listOf(ImportedFile(name = file.baseName + LibraryFiles.SONG_EXTENSION, bytes = song.encodeToByteArray()))
                }
            }
            else -> if (isJsonByName) JsonSongs.read(text).ifEmpty { null } else null
        }
    }

    /**
     * The library is flat, so "songs/x.cho" and "x.cho" are the same file as far as an import is concerned. A
     * backslash separates as well, since Windows PowerShell 5.1 writes one; no file name the library holds has one.
     */
    val String.fileName get() = substringAfterLast('/').substringAfterLast('\\')

    /**
     * Whether the entry is a hidden file or sits inside a hidden folder, as the dropped-folder walks decide it. A "."
     * segment is the current directory, which some archivers write a path through, not a hidden folder.
     */
    val String.isHidden get() = split('/', '\\').any { it != "." && LibraryFiles.isHiddenFileName(it) }
}
