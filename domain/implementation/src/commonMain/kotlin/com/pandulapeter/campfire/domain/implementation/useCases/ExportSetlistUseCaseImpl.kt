package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportSetlistUseCase

class ExportSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository,
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository
) : ExportSetlistUseCase {

    /**
     * The setlist document goes in exactly as it is stored, so that the transpositions in it survive the trip, and
     * the songs it names go in next to it. A song whose file is missing is left out rather than failing the export:
     * the setlist still names it, and importing it somewhere the song does exist will find it again.
     */
    override suspend operator fun invoke(setlistFileName: String): ExportedFile? {
        val setlist = setlistRepository.loadSetlistsIfNeeded().orEmpty().firstOrNull { it.fileName == setlistFileName } ?: return null
        val document = setlistRepository.loadSetlistDocument(setlistFileName) ?: return null
        val files = buildMap {
            put(setlistFileName, document.encodeToByteArray())
            setlist.entries.forEach { entry ->
                songContentRepository.loadSongContent(entry.songFileName, shouldCache = false)
                    ?.let { put(it.fileName, it.text.encodeToByteArray()) }
            }
        }
        return ExportedFile(
            // The stored file name is already a sanitized version of the title, so it is safe to suggest as one.
            name = setlistFileName.removeSuffix(LibraryFiles.SETLIST_EXTENSION) + ARCHIVE_EXTENSION,
            mimeType = ExportedFile.ZIP_MIME_TYPE,
            bytes = archiveRepository.pack(files)
        )
    }

    private companion object {
        const val ARCHIVE_EXTENSION = ".zip"
    }
}
