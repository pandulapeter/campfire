package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ExportedFile
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.domain.api.useCases.ExportSongsUseCase

class ExportSongsUseCaseImpl internal constructor(
    private val songContentRepository: SongContentRepository,
    private val archiveRepository: ArchiveRepository
) : ExportSongsUseCase {

    override suspend operator fun invoke(fileNames: List<String>): ExportedFile? {
        val contents = fileNames.mapNotNull { songContentRepository.loadSongContent(it) }
        return when (contents.size) {
            0 -> null
            // One song leaves as itself: a zip around a single text file would only be something to unpack again.
            1 -> contents.first().let {
                ExportedFile(name = it.fileName, mimeType = ExportedFile.TEXT_MIME_TYPE, bytes = it.text.encodeToByteArray())
            }

            else -> ExportedFile(
                name = ARCHIVE_NAME,
                mimeType = ExportedFile.ZIP_MIME_TYPE,
                bytes = archiveRepository.pack(contents.associate { it.fileName to it.text.encodeToByteArray() })
            )
        }
    }

    private companion object {
        const val ARCHIVE_NAME = "campfire-songs.zip"
    }
}
