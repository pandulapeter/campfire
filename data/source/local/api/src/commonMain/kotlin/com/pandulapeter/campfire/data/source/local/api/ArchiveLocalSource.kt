package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.ImportedFile

interface ArchiveLocalSource {

    /**
     * Every file in a zip archive, with the paths of the entries stripped and archives inside it unpacked too (up to
     * a few levels deep, so that a zip of zips of zips cannot be used to make the app work forever). Throws when the
     * bytes are not a readable archive.
     */
    suspend fun unpack(archive: ByteArray): List<ImportedFile>

    /** Packs the given entries (name to content, the names may contain directories) into a zip archive. */
    suspend fun pack(files: Map<String, ByteArray>): ByteArray
}
