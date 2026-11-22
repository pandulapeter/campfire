/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(BetaInteropApi::class, ExperimentalAtomicApi::class, ExperimentalForeignApi::class)

package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDate
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
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.errno
import platform.posix.memcpy
import platform.posix.remove
import platform.posix.rename
import platform.posix.strerror
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * A [FileStorage] on top of `NSFileManager`, under the folder [rootPath] names for each directory: the app's own
 * ([IosFileStorage]) or a test's.
 *
 * iOS backs up whole directories, so what [isInDeviceBackup] leaves out carries `NSURLIsExcludedFromBackupKey`, set here
 * rather than by each writer. [FOLDER_MARKED] directories carry it on the folder, which covers every file added later;
 * the others' files carry it one by one. The mark is an attribute of the file: an atomic write replaces the file and
 * drops it, while a rename, an in place write, and `copyItemAtPath` keep it, so it is set again after each of them.
 */
internal class FoundationFileStorage(
    private val logger: Logger,
    private val rootPath: (StorageDirectory) -> String,
) : FileStorage {

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

    /** Whether each directory has had its first use in this process, see [firstUse]. */
    private val isDirectoryUsed = StorageDirectory.entries.associateWith { AtomicBoolean(false) }

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
        createDirectory(directory)
        val path = filePath(directory, name)
        // `atomically` writes to a neighbouring temporary file and swaps it in, so a failure never truncates the file.
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!bytes.toNSData().writeToFile(path, options = NSDataWritingAtomic, error = error.ptr)) {
                throw LibraryStorageException("Could not write \"$name\": ${error.value?.localizedDescription}", error.value.asStorageCause())
            }
        }
        markForBackup(directory, name, isNewFile = true)
    }

    override suspend fun openSink(directory: StorageDirectory, name: String): FileSink = withContext(Dispatchers.IO) {
        createDirectory(directory)
        PosixFileSink(path = filePath(directory, name), name = name).also { markForBackup(directory, name, isNewFile = false) }
    }

    override suspend fun openSource(directory: StorageDirectory, name: String): FileSource? = withContext(Dispatchers.IO) {
        PosixFileSource.openFile(path = filePath(directory, name), name = name)
    }

    /** POSIX `rename`, which replaces the target in one step: every directory is in the app's one container and volume. */
    override suspend fun moveInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) = withContext(Dispatchers.IO) {
        val source = filePath(from, fromName)
        val target = filePath(to, toName)
        createDirectory(to)
        if (rename(source, target) != 0) throw LibraryStorageException("Could not move \"$fromName\": ${strerror(errno)?.toKString()}")
        markForBackup(to, toName, isNewFile = false)
    }

    /**
     * [file] is the path of a file the app owns (a `Documents/Inbox` copy, a picker's copy, the share extension's copy in
     * the App Group's inbox), moved rather than copied: a rename, the app's container and its group's being on one
     * volume, else the system's move, which copies across volumes and removes the original. The move happens at once, so
     * [onProgress] hears only its end.
     */
    override suspend fun takeInto(directory: StorageDirectory, name: String, file: Any, onProgress: (copiedBytes: Long) -> Unit) =
        withContext(Dispatchers.IO) {
            requireValidFileName(name)
            val source = file as? String ?: throw UnsupportedOperationException("Only a file's path can be taken into iOS's storage.")
            val target = filePath(directory, name)
            createDirectory(directory)
            if (rename(source, target) != 0) {
                remove(target)
                memScoped {
                    val error = alloc<ObjCObjectVar<NSError?>>()
                    if (!fileManager.moveItemAtPath(source, toPath = target, error = error.ptr)) {
                        throw LibraryStorageException("Could not move \"$source\": ${error.value?.localizedDescription}", error.value.asStorageCause())
                    }
                }
            }
            markForBackup(directory, name, isNewFile = false)
            onProgress(fileManager.attributesOfItemAtPath(target, error = null)?.get(NSFileSize).let { it as? NSNumber }?.longLongValue ?: 0L)
        }

    /**
     * Copied by the system under a name of its own beside the target and renamed over it, so a failure never truncates
     * the target, as [writeBytes]'s `atomically` does not either.
     */
    override suspend fun copyInto(from: StorageDirectory, fromName: String, to: StorageDirectory, toName: String) = withContext(Dispatchers.IO) {
        val source = filePath(from, fromName)
        val target = filePath(to, toName)
        val temporary = "${directoryPath(to)}/.campfire-${Uuid.random()}.tmp"
        createDirectory(to)
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!fileManager.copyItemAtPath(source, toPath = temporary, error = error.ptr)) {
                throw LibraryStorageException("Could not copy \"$fromName\": ${error.value?.localizedDescription}", error.value.asStorageCause())
            }
        }
        if (rename(temporary, target) != 0) {
            val failure = LibraryStorageException("Could not copy \"$fromName\": ${strerror(errno)?.toKString()}")
            remove(temporary)
            throw failure
        }
        markForBackup(to, toName, isNewFile = false)
    }

    override suspend fun availableSpace(directory: StorageDirectory) = withContext(Dispatchers.IO) {
        // The nearest folder that is there, since a directory is only created by its first write.
        val existing = generateSequence(directoryPath(directory)) { it.substringBeforeLast('/', "").ifEmpty { null } }.first { fileManager.fileExistsAtPath(it) }
        val values = NSURL.fileURLWithPath(existing).resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), null)
        (values?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber)?.longLongValue ?: 0L
    }

    override fun pathOf(directory: StorageDirectory, name: String) = filePath(directory, name)

    override suspend fun delete(directory: StorageDirectory, name: String) = withContext(Dispatchers.IO) {
        val path = filePath(directory, name)
        if (fileManager.fileExistsAtPath(path) && !fileManager.removeItemAtPath(path, null)) {
            throw LibraryStorageException("Could not delete \"$name\".")
        }
    }

    /**
     * `dataWithContentsOfFile` answers nil for a file that is not there and for one it could not read alike, and only
     * the first of those may come back as null: a file reported as missing is a deletion as far as sync is concerned.
     * So the read comes first, and only a nil is asked about: a file that is not there is missing, and one that is
     * there could not be read. A directory under the name is missing too, as it is to [list], which leaves it out.
     */
    private fun readData(directory: StorageDirectory, name: String): NSData? {
        val path = filePath(directory, name)
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            NSData.dataWithContentsOfFile(path, options = 0u, error = error.ptr) ?: run {
                val isDirectory = alloc<BooleanVar>()
                if (!fileManager.fileExistsAtPath(path, isDirectory = isDirectory.ptr) || isDirectory.value) return null
                throw LibraryStorageException("Could not read \"$name\": ${error.value?.localizedDescription}")
            }
        }
    }

    /** Creates the directory where it is not there yet, a [FOLDER_MARKED] one with its folder kept out of the backup. */
    private fun createDirectory(directory: StorageDirectory) {
        val path = directoryPath(directory)
        if (fileManager.fileExistsAtPath(path)) return
        fileManager.createDirectoryAtPath(path, true, null, null)
        if (directory in FOLDER_MARKED) setExcludedFromBackup(NSURL.fileURLWithPath(path, isDirectory = true), isExcluded = true, name = directory.name)
    }

    private fun filePath(directory: StorageDirectory, name: String): String {
        requireValidFileName(name)
        return "${directoryPath(directory)}/$name"
    }

    private fun directoryPath(directory: StorageDirectory): String {
        val path = directoryPaths.getValue(directory)
        if (isDirectoryUsed.getValue(directory).compareAndSet(expectedValue = false, newValue = true)) firstUse(directory, path)
        return path
    }

    /**
     * Brings what an older version left in [directory] in line with [isInDeviceBackup], once per process and before the
     * directory is used: a [FOLDER_MARKED] folder made before the folder was marked gets its mark, and in the library's
     * files and the preferences every file whose mark is wrong (a picture an older version's copy left out, an attachment
     * or a preferences file written before its writer marked it) is set right. One listing per launch, read with the
     * marks, and a write only where one is wrong. Two threads racing here would set the same marks, which is harmless.
     */
    private fun firstUse(directory: StorageDirectory, path: String) {
        if (!fileManager.fileExistsAtPath(path)) return
        when (directory) {
            in FOLDER_MARKED -> setExcludedFromBackup(NSURL.fileURLWithPath(path, isDirectory = true), isExcluded = true, name = directory.name)
            StorageDirectory.FILES, StorageDirectory.PREFERENCES -> {
                val entries = fileManager.contentsOfDirectoryAtURL(NSURL.fileURLWithPath(path, isDirectory = true), LISTED_KEYS, 0u, null)
                    ?: return logger.log("Could not read the device backup marks of ${directory.name}.")
                entries.filterIsInstance<NSURL>().forEach { url ->
                    val name = url.lastPathComponent ?: return@forEach
                    val values = url.resourceValuesForKeys(LISTED_KEYS, null) ?: return@forEach logger.log("Could not read the device backup mark of \"$name\".")
                    if ((values[NSURLIsDirectoryKey] as? NSNumber)?.boolValue != false) return@forEach
                    val isExcluded = !isInDeviceBackup(directory, name)
                    if ((values[NSURLIsExcludedFromBackupKey] as? NSNumber)?.boolValue != isExcluded) setExcludedFromBackup(url, isExcluded, name)
                }
            }
            else -> Unit
        }
    }

    /**
     * Sets the backup mark of [name] in [directory] as [isInDeviceBackup] says: after an atomic write ([isNewFile]), whose
     * new file carries none, only where it is to be out; after anything else either way, since a move, a copy, a take,
     * and an in place write keep the mark the file had. A file of a [FOLDER_MARKED] directory gets no mark of its own, so
     * one moved or copied out of it brings none along; the exports are in Caches, which no backup takes.
     */
    private fun markForBackup(directory: StorageDirectory, name: String, isNewFile: Boolean) {
        if (directory in FOLDER_MARKED || directory == StorageDirectory.EXPORTS) return
        val isExcluded = !isInDeviceBackup(directory, name)
        if (isExcluded || !isNewFile) setExcludedFromBackup(NSURL.fileURLWithPath(filePath(directory, name)), isExcluded, name)
    }

    /** A mark that cannot be set costs a stale copy in a backup or a file's place in one, which is not worth failing a write over. */
    private fun setExcludedFromBackup(url: NSURL, isExcluded: Boolean, name: String) {
        if (!url.setResourceValue(isExcluded, NSURLIsExcludedFromBackupKey, null)) logger.log("Could not set the device backup mark of \"$name\".")
    }

    private companion object {
        val LISTED_KEYS = listOf(NSURLIsDirectoryKey, NSURLFileSizeKey, NSURLContentModificationDateKey, NSURLIsExcludedFromBackupKey)

        /** The directories kept out of the backup by a mark on the folder, since everything they hold stays out. */
        val FOLDER_MARKED = setOf(StorageDirectory.COVERS, StorageDirectory.DRAFT_IMAGES, StorageDirectory.IMPORTS)
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
