/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProPrettifier
import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.chordpro.ChordSheet
import com.pandulapeter.campfire.chordpro.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportProgress
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.DocumentRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.domain.api.useCases.PrepareImportUseCase
import com.pandulapeter.campfire.domain.implementation.ImportPlanner
import com.pandulapeter.campfire.domain.implementation.mapper.toChordSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.koin.core.annotation.Factory

@Factory
class PrepareImportUseCaseImpl internal constructor(
    private val archiveRepository: ArchiveRepository,
    private val songRepository: SongRepository,
    private val songContentRepository: SongContentRepository,
    private val setlistRepository: SetlistRepository,
    private val documentRepository: DocumentRepository,
    private val logger: Logger,
) : PrepareImportUseCase {

    /**
     * Planned on [Dispatchers.Default] rather than wherever it was asked for, which for the view model is the main
     * thread. Decoding, splitting collections, reading every song header and folding comparable texts is computation.
     */
    override suspend operator fun invoke(
        files: List<ImportedFile>,
        onProgress: (ImportProgress) -> Unit,
    ): ImportPlan = withContext(Dispatchers.Default) { plan(files, onProgress) }

    private suspend fun plan(files: List<ImportedFile>, onProgress: (ImportProgress) -> Unit): ImportPlan {
        val songFiles = mutableListOf<IncomingFile>()
        val setlistFiles = mutableListOf<IncomingFile>()
        val documentFiles = mutableListOf<IncomingFile>()
        val triage = ImportTriage()

        // What the import may still hold once everything is unpacked, shared by every archive and plain file of it.
        // The checks here are not redundant with the platform readers: they are what holds for a caller that did not
        // read its files through an ImportBudget.
        var remaining = ImportLimits.MAX_IMPORT_SIZE

        // Loose files share the null origin, and everything unpacked from the picked file at index i has origin i.
        fun sort(file: ImportedFile, origin: Int?) {
            val extension = file.name.substringAfterLast('.', "").lowercase()
            when {
                // Picked along with the songs it sits next to by a "select all" on a volume macOS has written to. It
                // carries the extension of the file it belongs to, and decoded as a song it would be a screen of binary.
                LibraryFiles.isHiddenFileName(file.name) -> triage.skippedFileNames += file.name
                extension !in SONG_EXTENSIONS && extension !in DOCUMENT_EXTENSIONS && extension != SETLIST_EXTENSION -> triage.skippedFileNames += file.name
                file.isTooLarge || file.bytes.size > minOf(ImportLimits.maxSizeOf(file.name), remaining) -> triage.oversizedFileNames += file.name
                else -> {
                    remaining -= file.bytes.size
                    val incoming = IncomingFile(file = file, origin = origin)
                    when (extension) {
                        SETLIST_EXTENSION -> setlistFiles += incoming
                        in DOCUMENT_EXTENSIONS -> documentFiles += incoming
                        else -> songFiles += incoming
                    }
                }
            }
        }

        files.forEachIndexed { index, file ->
            yield()
            onProgress(ImportProgress(ImportProgress.Phase.UNPACKING, index, files.size, file.name))
            // An archive by its name, or a file under a name nothing here knows whose bytes are an archive's: the library
            // backups of other apps are zip archives of their own extension, and the songs in them are worth looking for.
            val isArchiveByName = LibraryFiles.isArchiveFileName(file.name)
            val isArchive = isArchiveByName || (!LibraryFiles.isImportableFileName(file.name) && LibraryFiles.isZipArchive(file.bytes))
            when {
                !isArchive -> sort(file = file, origin = null)
                file.isTooLarge || file.bytes.size > ImportLimits.MAX_IMPORT_SIZE -> triage.oversizedFileNames += file.name
                else -> try {
                    val entries = archiveRepository.unpack(archive = file.bytes, maxSize = remaining)
                    // Everyday document formats are zip archives too (an OpenDocument, a spreadsheet, an e-book), and
                    // their parts are not what anybody picked: one recognised by its bytes alone that holds nothing an
                    // import reads is reported as the one file it was, rather than as the names of its insides.
                    if (isArchiveByName || entries.any { it.isImportable() }) {
                        entries.forEach { sort(file = it, origin = index) }
                    } else {
                        triage.skippedFileNames += file.name
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logger.log("Could not unpack \"${file.name}\": ${exception.message}")
                    triage.skippedFileNames += file.name
                }
            }
        }

        onProgress(ImportProgress(ImportProgress.Phase.UNPACKING, files.size, files.size))
        val songs = planSongs(songFiles, documentFiles, triage, onProgress)
        return ImportPlan(
            songs = songs,
            setlists = planSetlists(setlistFiles, triage, ImportPlanner.plannedSongFileNames(songs)),
            skippedFileNames = triage.skippedFileNames,
            oversizedFileNames = triage.oversizedFileNames,
            unreadableDocumentFileNames = triage.unreadableDocumentFileNames,
        )
    }

    /** Every song of the batch under the name its own header gives it, held against the library by [ImportPlanner]. */
    private suspend fun planSongs(
        files: List<IncomingFile>,
        documents: List<IncomingFile>,
        triage: ImportTriage,
        onProgress: (ImportProgress) -> Unit,
    ): List<ImportPlan.SongEntry> {
        val incoming = (files + documents).flatMapIndexed { index, (file, origin) ->
            onProgress(ImportProgress(ImportProgress.Phase.READING, index, files.size + documents.size, file.name))
            // The web's default dispatcher is a queue on its only thread, so yielding between files and songs keeps
            // the page painting and lets a preparation nobody awaits any more notice cancellation.
            yield()
            val isDocument = index >= files.size
            val original = if (isDocument) null else file.bytes.decodeLibraryText()
            val converted = when {
                isDocument -> {
                    val extracted = documentRepository.extract(file)
                    if (extracted == null) {
                        triage.unreadableDocumentFileNames += file.name
                        return@flatMapIndexed emptyList()
                    }
                    ChordSheetConverter.convert(extracted.toChordSheet(), String::normalizedToNfc)
                }
                file.name.endsWith(LibraryFiles.TEXT_EXTENSION, true) -> ChordSheetConverter.convert(ChordSheet.ofPlainText(original!!), String::normalizedToNfc)
                else -> listOf(original!!)
            }
            if (converted.sumOf { it.encodeToByteArray().size.toLong() } > ImportLimits.MAX_TEXT_FILE_SIZE) {
                triage.oversizedFileNames += file.name
                return@flatMapIndexed emptyList()
            }
            val isConverted = isDocument || converted != listOf(original)
            val parts = converted.flatMap(ChordProSplitter::split)
            if (parts.isEmpty()) {
                if (isDocument) triage.unreadableDocumentFileNames += file.name else triage.skippedFileNames += file.name
                return@flatMapIndexed emptyList()
            }
            val songs = parts.map { part ->
                yield()
                // Every song is named by its own header, whichever file it arrived in. The name a file came under is
                // only worth anything where the song inside it declares no title: then it is what titles the song,
                // and a collection's parts do not even have that, since the file they came from named none of them.
                val fallbackTitle = if (parts.size == 1) file.name.substringBeforeLast('.') else ""
                // All sources share the same formatter after conversion and splitting, and before naming or
                // comparing. The editor's manual action uses it too. Notation conversion still precedes formatting.
                val text = ChordProPrettifier.prettify(ChordProNotation.convertText(part, ChordNotation.STANDARD, ChordNotation.STANDARD))
                ImportPlanner.IncomingSong(
                    fileName = songRepository.importFileName(fallbackTitle = fallbackTitle, text = text),
                    text = text,
                    sourceFileName = file.name.takeIf { parts.size == 1 },
                    isConverted = isConverted,
                    origin = origin,
                )
            }
            // Formatting adds section separators and a final newline; keep the resulting text within the same
            // limit as converted input, so a file at the boundary is not written too large to read back.
            if (songs.sumOf { it.text.encodeToByteArray().size.toLong() } > ImportLimits.MAX_TEXT_FILE_SIZE) {
                triage.oversizedFileNames += file.name
                emptyList()
            } else songs
        }
        onProgress(ImportProgress(ImportProgress.Phase.READING, files.size + documents.size, files.size + documents.size))
        onProgress(ImportProgress(ImportProgress.Phase.COMPARING))
        return ImportPlanner.planSongs(
            incoming = incoming,
            // The names come from the scan the app already made, so finding numbered siblings needs no directory
            // listing — on the web a listing opens every file — and only those siblings are read.
            libraryFileNames = songRepository.loadSongsIfNeeded().orEmpty().map { it.fileName },
            // Past the cache: what matters is the file as it is now, and an import walks files the library has no
            // other reason to hold on to.
            readLibraryText = { fileName -> songContentRepository.loadSongContent(fileName, useCache = false)?.text },
        )
    }

    /** Every setlist of the batch held against the library by [ImportPlanner]. */
    private suspend fun planSetlists(
        files: List<IncomingFile>,
        triage: ImportTriage,
        songFileNames: ImportPlanner.SongFileNames,
    ): List<ImportPlan.SetlistEntry> {
        val incoming = files.mapNotNull { (file, origin) ->
            yield()
            val parsed = setlistRepository.parseSetlist(file.bytes.decodeLibraryText())
            if (parsed == null) {
                triage.skippedFileNames += file.name
                return@mapNotNull null
            }
            ImportPlanner.IncomingSetlist(setlist = parsed.setlist, sourceFileName = file.name, origin = origin, isDated = parsed.isDated)
        }
        return ImportPlanner.planSetlists(
            incoming = incoming,
            librarySetlists = setlistRepository.loadSetlistsIfNeeded().orEmpty(),
            songFileNames = songFileNames,
        )
    }

    /**
     * Whether this is a file an import would read anything from: not hidden, a song, a document or a setlist by its
     * name, and actually read. A bare `.json` does not count, since inside another app's container it is that app's
     * configuration, and neither does an entry handed over unread, or an empty one, which holds nothing to import.
     */
    private fun ImportedFile.isImportable(): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return !LibraryFiles.isHiddenFileName(name) &&
                (bytes.isNotEmpty() || isTooLarge) &&
                (extension in SONG_EXTENSIONS || extension in DOCUMENT_EXTENSIONS || name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true))
    }

    /** The files of the batch that are left out, by why, as the preparation comes across them. */
    private class ImportTriage {
        val skippedFileNames = mutableListOf<String>()
        val unreadableDocumentFileNames = mutableListOf<String>()
        val oversizedFileNames = mutableListOf<String>()
    }

    /** A file of the batch and which picked file it came out of, see [ImportPlan.SongEntry.origin]. */
    private data class IncomingFile(val file: ImportedFile, val origin: Int?)

    private companion object {
        /** The ChordPro family plus plain text, without the dots, which is how a file name is asked for its type. */
        val SONG_EXTENSIONS = (LibraryFiles.SONG_EXTENSIONS + LibraryFiles.TEXT_EXTENSION).mapTo(mutableSetOf()) { it.removePrefix(".") }
        val DOCUMENT_EXTENSIONS = (LibraryFiles.DOCUMENT_EXTENSIONS + LibraryFiles.LEGACY_DOCUMENT_EXTENSION).mapTo(mutableSetOf()) { it.removePrefix(".") }

        /** ".setlist.json" ends in this, and a plain ".json" is worth trying to parse as a setlist too. */
        const val SETLIST_EXTENSION = "json"
    }
}
