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

import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The iOS [FileStorage], on top of `NSFileManager`.
 *
 * The library lives in the documents directory so that it can be exposed to the Files app, while the preferences -
 * which are app state rather than user documents - go to the application support directory, out of the user's way.
 */
@Single
internal class IosFileStorage : FileStorage {

    private val fileManager = NSFileManager.defaultManager

    /**
     * Each directory's path, resolved once rather than asked of `URLForDirectory` on every call. A directory is only
     * created by a write ([writeBytes]): a read or a listing of one that is not there answers what it answers for a
     * file that is not there, which keeps a library folder deleted in the Files app looking deleted to sync's guard
     * until the next save creates it again.
     */
    private val directoryPaths by lazy {
        StorageDirectory.entries.associateWith { "${rootPath(it)}/${it.pathSegments.joinToString("/")}" }
    }

    /**
     * The attributes every entry is listed with are asked for with the listing itself, rather than read one entry after
     * another with `attributesOfItemAtPath`, which built a dictionary of every attribute for each of them.
     */
    override suspend fun list(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryPath = directoryPath(directory)
        // Null means the directory could not be listed. If it is there all the same, that is a failure to report
        // rather than an empty library, see the JVM implementation.
        val entries = fileManager.contentsOfDirectoryAtURL(
            url = NSURL.fileURLWithPath(directoryPath, isDirectory = true),
            includingPropertiesForKeys = LISTED_KEYS,
            options = 0u,
            error = null,
        ) ?: if (fileManager.fileExistsAtPath(directoryPath)) throw LibraryStorageException("Could not read \"$directoryPath\".") else emptyList<Any?>()
        entries.filterIsInstance<NSURL>()
            .mapNotNull { url -> url.listedInfo() }
            .sortedBy { it.name }
    }

    override suspend fun listNames(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        val directoryPath = directoryPath(directory)
        fileManager.contentsOfDirectoryAtPath(directoryPath, null)?.filterIsInstance<String>()
            ?: if (fileManager.fileExistsAtPath(directoryPath)) throw LibraryStorageException("Could not read \"$directoryPath\".") else emptyList()
    }

    override suspend fun info(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        info(path = filePath(directory, name), name = name)
    }

    override suspend fun exists(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        fileManager.fileExistsAtPath(filePath(directory, name))
    }

    /** What [list] fetched about one entry, null for a directory and for one gone since the listing. */
    private fun NSURL.listedInfo(): StoredFileInfo? {
        val name = lastPathComponent ?: return null
        val values = resourceValuesForKeys(LISTED_KEYS, null) ?: return null
        if ((values[NSURLIsDirectoryKey] as? NSNumber)?.boolValue != false) return null
        return StoredFileInfo(
            name = name,
            size = (values[NSURLFileSizeKey] as? NSNumber)?.longLongValue ?: 0L,
            lastModified = ((values[NSURLContentModificationDateKey] as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(1000).toLong(),
        )
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
        readData(directory, name)?.toByteArray()?.decodeLibraryText()
    }

    override suspend fun readBytes(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        readData(directory, name)?.toByteArray()
    }

    override suspend fun writeText(directory: StorageDirectory, name: String, text: String) = writeBytes(directory, name, text.encodeToByteArray())

    override suspend fun writeBytes(directory: StorageDirectory, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        fileManager.createDirectoryAtPath(directoryPath(directory), true, null, null)
        val path = filePath(directory, name)
        // `atomically` writes to a neighbouring temporary file and swaps it in, so a failure never truncates the file.
        if (!bytes.toNSData().writeToFile(path, atomically = true)) {
            throw LibraryStorageException("Could not write \"$name\".")
        }
    }

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val path = filePath(directory, name)
        if (fileManager.fileExistsAtPath(path) && !fileManager.removeItemAtPath(path, null)) {
            throw LibraryStorageException("Could not delete \"$name\".")
        }
    }

    /**
     * The mark is an attribute of the file, which an atomic write replaces, so it is set again after every write. One
     * that cannot be set costs a stale copy in a backup and nothing else, which is not worth failing the write over.
     */
    override suspend fun keepOutOfDeviceBackup(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val path = filePath(directory, name)
        if (fileManager.fileExistsAtPath(path) && !NSURL.fileURLWithPath(path).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)) {
            println("Could not keep \"$name\" out of the device backup.")
        }
    }

    /**
     * `dataWithContentsOfFile` answers nil for a file that is not there and for one it could not read alike, and only
     * the first of those may come back as null: a file reported as missing is a deletion as far as sync is concerned.
     * So the read comes first, and only a nil is asked about: a file that is not there is missing, and one that is
     * there could not be read.
     */
    private fun readData(directory: StorageDirectory, name: String): NSData? {
        val path = filePath(directory, name)
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSData.dataWithContentsOfFile(path, options = 0u, error = error.ptr) ?: run {
                if (!fileManager.fileExistsAtPath(path)) return null
                throw LibraryStorageException("Could not read \"$name\": ${error.value?.localizedDescription}")
            }
        }
    }

    private fun filePath(directory: StorageDirectory, name: String): String {
        requireValidFileName(name)
        return "${directoryPath(directory)}/$name"
    }

    private fun directoryPath(directory: StorageDirectory) = directoryPaths.getValue(directory)

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

    private companion object {
        val LISTED_KEYS = listOf(NSURLIsDirectoryKey, NSURLFileSizeKey, NSURLContentModificationDateKey)
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
