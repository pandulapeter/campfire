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
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.model.library.PackedArchive
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.DosTimestamp
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
    private val fileStorage: FileStorage,
    private val importStaging: ImportStaging,
) : ArchiveLocalSource {

    override suspend fun unpack(
        archive: StagedFile,
        maxSize: Long,
        onProgress: (readBytes: Long, totalBytes: Long) -> Unit,
    ): List<ImportedFile> = withContext(Dispatchers.Default) {
        StreamingArchiveUnpack(
            fileStorage = fileStorage,
            staging = importStaging,
            logger = logger,
            request = importStaging.requestOf(archive),
            maxSize = maxSize,
            onProgress = onProgress,
        ).unpack(archive)
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
}
