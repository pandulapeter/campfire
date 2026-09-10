package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.LibraryFile
import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class LibraryFileLocalSourceImpl(
    private val fileStorage: FileStorage
) : LibraryFileLocalSource {

    override suspend fun loadLibraryFiles(): List<LibraryFile> = coroutineScope {
        LibraryFileKind.entries
            .map { kind -> async { fileStorage.list(kind.directory).filter { kind.matches(it.name) }.map { it.toLibraryFile(kind) } } }
            .awaitAll()
            .flatten()
    }

    override suspend fun readLibraryFile(kind: LibraryFileKind, name: String) = fileStorage.readBytes(kind.directory, name)

    override suspend fun writeLibraryFile(kind: LibraryFileKind, name: String, bytes: ByteArray) =
        fileStorage.writeBytes(kind.directory, name, bytes)

    override suspend fun writeLibraryFileToFreeName(kind: LibraryFileKind, desiredName: String, bytes: ByteArray): String {
        val name = fileStorage.uniqueName(kind.directory, desiredName)
        fileStorage.writeBytes(kind.directory, name, bytes)
        return name
    }

    override suspend fun deleteLibraryFile(kind: LibraryFileKind, name: String) = fileStorage.delete(kind.directory, name)

    private fun StoredFileInfo.toLibraryFile(kind: LibraryFileKind) =
        LibraryFile(kind = kind, name = name, size = size, lastModified = lastModified)

    private val LibraryFileKind.directory
        get() = when (this) {
            LibraryFileKind.SONG -> StorageDirectory.SONGS
            LibraryFileKind.SETLIST -> StorageDirectory.SETLISTS
        }

    /**
     * The same rule the parsing sources use: the library folder is a folder the user can open, so whatever else
     * they put in it is left where it is rather than being uploaded to their cloud storage.
     */
    private fun LibraryFileKind.matches(name: String) = when (this) {
        LibraryFileKind.SONG -> LibraryFiles.SONG_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
        LibraryFileKind.SETLIST -> name.endsWith(LibraryFiles.SETLIST_EXTENSION, ignoreCase = true)
    }
}
