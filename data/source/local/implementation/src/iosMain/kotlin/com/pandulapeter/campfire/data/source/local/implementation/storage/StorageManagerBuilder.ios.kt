package com.pandulapeter.campfire.data.source.local.implementation.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.scope.Scope
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual fun Scope.createStorageManagerBuilder(): RoomDatabase.Builder<StorageManager> = Room.databaseBuilder<StorageManager>(
    name = "${documentDirectoryPath()}/$DATABASE_FILE_NAME"
)

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectoryPath() = requireNotNull(
    NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )?.path
)
