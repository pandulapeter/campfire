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

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.DosTimestamp
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

    override suspend fun unpack(archive: ByteArray): List<ImportedFile> = withContext(Dispatchers.Default) {
        unpack(archive = archive, depth = 1, inflated = InflatedBytes())
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
    private fun unpack(archive: ByteArray, depth: Int, inflated: InflatedBytes): List<ImportedFile> = ZipReader
        .read(archive = archive, maxTotalSize = ZipReader.MAX_ARCHIVE_SIZE - inflated.count)
        .also { entries -> inflated.count += entries.sumOf { it.bytes.size.toLong() } }
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
                    unpack(archive = file.bytes, depth = depth + 1, inflated = inflated)
                } catch (exception: Exception) {
                    println("Could not unpack \"${file.name}\": ${exception.message}")
                    emptyList()
                }
            } else {
                listOf(file)
            }
        }

    /** How many bytes the archives of one import have inflated to so far, carried down the recursion. */
    private class InflatedBytes {
        var count = 0L
    }

    private companion object {
        const val MAX_DEPTH = 3
        const val ZIP_EXTENSION = ".zip"

        /** What every hidden file starts with, whichever system wrote it: "._name.cho", ".DS_Store", ".gitignore". */
        const val HIDDEN_NAME_PREFIX = "."
    }
}
