package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.ImportedFile

/**
 * Zip handling, which has no state to cache: this exists so that the use cases can reach it without the domain layer
 * having to see the local sources.
 */
interface ArchiveRepository {

    /** See `ArchiveLocalSource.unpack`. */
    suspend fun unpack(archive: ByteArray): List<ImportedFile>

    /** See `ArchiveLocalSource.pack`. */
    suspend fun pack(files: Map<String, ByteArray>): ByteArray
}
