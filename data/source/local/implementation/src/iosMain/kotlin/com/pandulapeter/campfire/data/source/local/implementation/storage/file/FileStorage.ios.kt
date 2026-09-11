/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

internal actual fun Scope.createFileStorage(): FileStorage = IosFileStorage()

/**
 * The iOS [FileStorage], on top of `NSFileManager`.
 *
 * The library lives in the documents directory so that it can be exposed to the Files app, while the preferences -
 * which are app state rather than user documents - go to the application support directory, out of the user's way.
 */
private class IosFileStorage : FileStorage {

    private val fileManager = NSFileManager.defaultManager

    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryPath = directoryPath(directory)
        // Null means the directory could not be listed. If it is there all the same, that is a failure to report
        // rather than an empty library, see the JVM implementation.
        val entries = fileManager.contentsOfDirectoryAtPath(directoryPath, null)
            ?: if (fileManager.fileExistsAtPath(directoryPath)) throw IllegalStateException("Could not read \"$directoryPath\".") else emptyList<Any?>()
        entries.filterIsInstance<String>()
            .mapNotNull { name -> info(path = "$directoryPath/$name", name = name) }
            .sortedBy { it.name }
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        info(path = filePath(directory, name), name = name)
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        fileManager.fileExistsAtPath(filePath(directory, name))
    }

    /** Null for a directory and for anything that is not there. */
    private fun info(path: String, name: String): StoredFileInfo? {
        val attributes = fileManager.attributesOfItemAtPath(path, null)
        if (attributes == null || attributes[NSFileType] == NSFileTypeDirectory) return null
        return StoredFileInfo(
            name = name,
            size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L,
            lastModified = ((attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(1000).toLong(),
        )
    }

    override suspend fun readText(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        readData(directory, name)?.toByteArray()?.decodeToString()?.withoutByteOrderMark()
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        readData(directory, name)?.toByteArray()
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = writeBytes(directory, name, text.encodeToByteArray())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val path = filePath(directory, name)
        // `atomically` writes to a neighbouring temporary file and swaps it in, so a failure never truncates the file.
        if (!bytes.toNSData().writeToFile(path, atomically = true)) {
            throw IllegalStateException("Could not write \"$name\".")
        }
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val path = filePath(directory, name)
        if (fileManager.fileExistsAtPath(path) && !fileManager.removeItemAtPath(path, null)) {
            throw IllegalStateException("Could not delete \"$name\".")
        }
    }

    private fun readData(directory: StorageDirectory, name: String): NSData? = filePath(directory, name)
        .let { if (fileManager.fileExistsAtPath(it)) NSData.dataWithContentsOfFile(it) else null }

    private fun filePath(directory: StorageDirectory, name: String): String {
        requireValidFileName(name)
        return "${directoryPath(directory)}/$name"
    }

    private fun directoryPath(directory: StorageDirectory) = "${rootPath(directory)}/${directory.pathSegments.joinToString("/")}"
        .also { fileManager.createDirectoryAtPath(it, true, null, null) }

    private fun rootPath(directory: StorageDirectory): String {
        val isPreferences = directory == StorageDirectory.PREFERENCES
        return requireNotNull(
            fileManager.URLForDirectory(
                directory = if (isPreferences) NSApplicationSupportDirectory else NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                // Unlike the documents directory, application support doesn't exist until something creates it.
                create = isPreferences,
                error = null,
            )?.path
        ) { "Could not resolve the data directory." }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) {
        return ByteArray(0)
    }
    val source = this
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), source.bytes, source.length) }
    }
}

private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    memScoped { NSData.create(bytes = allocArrayOf(this@toNSData), length = size.convert()) }
}
