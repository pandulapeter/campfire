package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource

internal class ArchiveRepositoryImpl(
    private val archiveLocalSource: ArchiveLocalSource
) : ArchiveRepository {

    override suspend fun unpack(archive: ByteArray) = archiveLocalSource.unpack(archive)

    override suspend fun pack(files: Map<String, ByteArray>) = archiveLocalSource.pack(files)
}
