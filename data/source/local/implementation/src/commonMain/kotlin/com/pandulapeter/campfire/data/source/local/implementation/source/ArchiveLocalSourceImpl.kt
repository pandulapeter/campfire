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

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.DosTimestamp
import com.pandulapeter.campfire.data.source.local.implementation.zip.UnreadZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipReader
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipWriter
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
 * inflating and deflating in memory, and a big archive would otherwise block whichever thread asked for it.
 */
@Single
internal class ArchiveLocalSourceImpl : ArchiveLocalSource {

    override suspend fun unpack(archive: ByteArray, maxSize: Long): List<ImportedFile> = withContext(Dispatchers.Default) {
        unpack(archive = archive, depth = 1, inflated = InflatedBytes(limit = maxSize))
    }

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
            // Decided on the name alone, before anything is inflated. What the archiving tool wrote for itself is not
            // part of what anybody chose to import. macOS packs an AppleDouble "._name.cho" next to every entry and a
            // ".DS_Store" into every directory, and the first of those carries the extension of the file it belongs
            // to: an archive of three hundred songs arrives as six hundred entries, half of them binary, and an import
            // that took them at their word would offer to import as many unreadable files as there are songs. A file
            // somebody means to import is never a hidden one, so these are never read, and neither is anything an
            // import would not look inside: both cost nothing and cannot fail the archive.
            limitOf = { name -> name.fileName.takeUnless { it.startsWith(HIDDEN_NAME_PREFIX) }?.let(ImportLimits::maxSizeOf)?.takeIf { it > 0 } },
        )
        inflated.count += content.entries.sumOf { it.bytes.size.toLong() }
        val read = content.entries.flatMap { entry ->
            val file = ImportedFile(name = entry.name.fileName, bytes = entry.bytes)
            if (depth < MAX_DEPTH && file.name.endsWith(ZIP_EXTENSION, ignoreCase = true)) {
                // A nested archive that cannot be read is reported rather than failing the whole import: the files
                // next to it are still perfectly good.
                try {
                    unpack(archive = file.bytes, depth = depth + 1, inflated = inflated)
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    listOf(ImportedFile.unread(file.name))
                }
            } else {
                listOf(file)
            }
        }
        // Hidden entries stay unreported, see above; everything else that was not read is handed over by name.
        val unread = content.unread
            .filterNot { it.name.fileName.startsWith(HIDDEN_NAME_PREFIX) }
            .map { ImportedFile.unread(name = it.name.fileName, isTooLarge = it.reason == UnreadZipEntry.Reason.TOO_LARGE) }
        return read + unread
    }

    /** The library is flat, so "songs/x.cho" and "x.cho" are the same file as far as an import is concerned. */
    private val String.fileName get() = substringAfterLast('/')

    /** How many bytes the archives of one import have inflated to so far, carried down the recursion, and the most they may. */
    private class InflatedBytes(val limit: Long) {
        var count = 0L
    }

    private companion object {
        const val MAX_DEPTH = 3
        const val ZIP_EXTENSION = ".zip"

        /** What every hidden file starts with, whichever system wrote it: "._name.cho", ".DS_Store", ".gitignore". */
        const val HIDDEN_NAME_PREFIX = "."
    }
}
