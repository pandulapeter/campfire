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

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.model.library.PackedArchive
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveEntryPolicy.isHidden
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.DosTimestamp
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.formats.zip.ZipSink
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single

/**
 * Unpacking and packing run on [Dispatchers.Default] rather than IO: they are inflating and deflating, and a big archive
 * would otherwise block whichever thread asked for it. What touches the file system (a streamed pack's entries and its
 * sink) moves to its own dispatcher itself.
 */
@Single
internal class ArchiveLocalSourceImpl(
    private val logger: Logger,
) : ArchiveLocalSource {

    override suspend fun unpack(archive: ByteArray, maxSize: Long, maxImageSize: Long, maxAttachmentSize: Long): UnpackedArchive =
        withContext(Dispatchers.Default) {
            val inflated = InflatedBytes(limit = maxSize, imageLimit = maxImageSize, attachmentLimit = maxAttachmentSize)
            UnpackedArchive(
                files = unpack(archive = archive, depth = 1, inflated = inflated),
                imageSize = inflated.imageCount,
                attachmentSize = inflated.attachmentCount,
            )
        }

    override suspend fun songsIn(file: ImportedFile): List<ImportedFile>? = withContext(Dispatchers.Default) { ArchiveEntryPolicy.foreignSongs(file) }

    override suspend fun pack(files: Map<String, ByteArray>, isDated: Boolean): ByteArray = withContext(Dispatchers.Default) {
        ZipWriter.write(entries = files.map { (name, bytes) -> ZipEntry(name = name, bytes = bytes) }, modifiedAt = timestamp(isDated))
    }

    override suspend fun pack(entries: List<suspend () -> ArchiveEntry?>, isDated: Boolean, sink: ExportSink) = withContext(Dispatchers.Default) {
        ZipWriter.write(
            entries = entries.map { read -> { read()?.let { ZipEntry(name = it.name, bytes = it.bytes) } } },
            sink = object : ZipSink {
                override val position get() = sink.position

                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = sink.write(bytes, offset, length)
            },
            modifiedAt = timestamp(isDated),
        ).let { PackedArchive(entryCount = it.entryCount, unpackedSize = it.unpackedSize) }
    }

    private fun timestamp(isDated: Boolean) = if (isDated) {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { dateTime ->
            DosTimestamp.of(dateTime.year, dateTime.month.number, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second)
        }
    } else {
        DosTimestamp.EARLIEST
    }

    /**
     * The size limit is shared by the whole import through [inflated] rather than applied per archive: a nested archive
     * is inflated on top of the bytes its parent already holds in memory, and every level is kept until the end.
     */
    private fun unpack(archive: ByteArray, depth: Int, inflated: InflatedBytes): List<ImportedFile> {
        val content = ZipReader.read(
            archive = archive,
            maxTotalSize = inflated.limit - inflated.count,
            limitOf = ArchiveEntryPolicy::limitOf,
            sniffedLimitOf = ArchiveEntryPolicy::sniffedLimitOf,
        )
        inflated.count += content.chargedSize
        val entries = content.entries.map { entry -> ArchiveEntryPolicy.importedFileOf(entry.name, entry.bytes) }
        ArchiveEntryPolicy.backupOf(entries, content.unread)?.let { return it }
        val images = ArchiveSongImages.read(
            archive = archive,
            content = content,
            maxSize = inflated.imageLimit - inflated.imageCount,
            maxAttachmentSize = inflated.attachmentLimit - inflated.attachmentCount,
            isHidden = { it.isHidden },
        )
        inflated.imageCount += images.chargedSize
        inflated.attachmentCount += images.chargedAttachmentSize
        val read = ArchiveEntryPolicy.withSongImages(entries, content.entries.map { it.name }, images).flatMap { file ->
            if (depth < ArchiveEntryPolicy.MAX_DEPTH && ArchiveEntryPolicy.isArchive(file)) {
                // A nested archive that cannot be read is reported rather than failing the whole import: the files
                // next to it are still perfectly good.
                try {
                    unpack(archive = file.bytes, depth = depth + 1, inflated = inflated)
                } catch (exception: Exception) {
                    logger.log("Could not unpack \"${file.name}\": ${exception.message}")
                    listOf(ImportedFile.unread(file.name))
                }
            } else {
                ArchiveEntryPolicy.foreignSongs(file) ?: listOf(file)
            }
        }
        return read + ArchiveEntryPolicy.unreadFilesOf(content.unread, images.takenEntryNames)
    }

    /**
     * How many bytes the archives of one import have inflated to so far, carried down the recursion, and the most they
     * may: the songs, the pictures, and the attachments each within a budget of their own.
     */
    private class InflatedBytes(val limit: Long, val imageLimit: Long, val attachmentLimit: Long) {
        var count = 0L
        var imageCount = 0L
        var attachmentCount = 0L
    }
}
