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

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipReader
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unpacking and packing run on [Dispatchers.Default] rather than IO: nothing here touches the file system, it is all
 * inflating and deflating in memory, and a big archive would otherwise block whichever thread asked for it.
 */
internal class ArchiveLocalSourceImpl : ArchiveLocalSource {

    override suspend fun unpack(archive: ByteArray): List<ImportedFile> = withContext(Dispatchers.Default) {
        unpack(archive = archive, depth = 1)
    }

    override suspend fun pack(files: Map<String, ByteArray>): ByteArray = withContext(Dispatchers.Default) {
        ZipWriter.write(files.map { (name, bytes) -> ZipEntry(name = name, bytes = bytes) })
    }

    private fun unpack(archive: ByteArray, depth: Int): List<ImportedFile> = ZipReader.read(archive)
        // What the archiving tool wrote for itself is not part of what anybody chose to import. macOS packs an
        // AppleDouble "._name.cho" next to every entry and a ".DS_Store" into every directory, and the first of
        // those carries the extension of the file it belongs to: an archive of three hundred songs arrives as six
        // hundred entries, half of them binary, and an import that took them at their word would offer to import
        // as many unreadable files as there are songs. A file somebody means to import is never a hidden one, so
        // these are dropped here instead of being carried through the plan and reported as unsupported.
        .filterNot { entry -> entry.name.substringAfterLast('/').startsWith(HIDDEN_NAME_PREFIX) }
        .flatMap { entry ->
            // The path is dropped here rather than by the caller: the library is flat, so "songs/x.cho" and "x.cho"
            // are the same file as far as an import is concerned.
            val file = ImportedFile(name = entry.name.substringAfterLast('/'), bytes = entry.bytes)
            if (depth < MAX_DEPTH && file.name.endsWith(ZIP_EXTENSION, ignoreCase = true)) {
                // A nested archive that cannot be read is skipped rather than failing the whole import: the files
                // next to it are still perfectly good.
                try {
                    unpack(archive = file.bytes, depth = depth + 1)
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    emptyList()
                }
            } else {
                listOf(file)
            }
        }

    private companion object {
        const val MAX_DEPTH = 3
        const val ZIP_EXTENSION = ".zip"

        /** What every hidden file starts with, whichever system wrote it: "._name.cho", ".DS_Store", ".gitignore". */
        const val HIDDEN_NAME_PREFIX = "."
    }
}
