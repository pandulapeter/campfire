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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.ExportFileWriter
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.source.local.api.ExportFileLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * The folder is emptied once per process, before anything is written into it: [clear] on launch, or the first [create]
 * where an export comes first, so an export can never be one of the files the emptying removes.
 */
@Single
internal class ExportFileLocalSourceImpl(
    private val fileStorage: FileStorage,
    private val logger: Logger,
) : ExportFileLocalSource {

    private val clearing = Mutex()
    private var isCleared = false

    override suspend fun clear() = clearing.withLock {
        if (isCleared) return@withLock
        isCleared = true
        try {
            fileStorage.listNames(StorageDirectory.EXPORTS).forEach { name ->
                try {
                    fileStorage.delete(StorageDirectory.EXPORTS, name)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // One still open elsewhere (another app reading a share) is tried again on the next launch.
                    logger.log("Could not remove the export \"$name\": ${exception.message}")
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not empty the exports: ${exception.message}")
        }
    }

    override suspend fun create(name: String): ExportFileWriter {
        clear()
        val sink = fileStorage.openSink(StorageDirectory.EXPORTS, name)
        return Writer(sink = sink, path = fileStorage.pathOf(StorageDirectory.EXPORTS, name))
    }

    override suspend fun read(file: ExportedFile) = when (val content = file.content) {
        is ExportedContent.Bytes -> content.bytes
        is ExportedContent.TemporaryFile -> fileStorage.readBytes(StorageDirectory.EXPORTS, file.name)
    }

    override suspend fun discard(file: ExportedFile) {
        if (file.content !is ExportedContent.TemporaryFile) return
        try {
            withContext(NonCancellable) { fileStorage.delete(StorageDirectory.EXPORTS, file.name) }
        } catch (exception: Exception) {
            logger.log("Could not remove the export \"${file.name}\": ${exception.message}")
        }
    }

    private class Writer(
        private val sink: FileSink,
        private val path: String,
    ) : ExportFileWriter {

        override val position get() = sink.position

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = sink.write(bytes, offset, length)

        override suspend fun finish(): ExportedContent.TemporaryFile {
            sink.close()
            return ExportedContent.TemporaryFile(path = path, size = sink.position)
        }

        override suspend fun abandon() = sink.abandon()
    }
}
