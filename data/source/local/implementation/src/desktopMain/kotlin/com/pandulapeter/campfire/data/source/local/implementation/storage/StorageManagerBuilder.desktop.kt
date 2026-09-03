package com.pandulapeter.campfire.data.source.local.implementation.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.core.scope.Scope

// Relative to the working directory, matching where earlier desktop versions kept the database.
internal actual fun Scope.createStorageManagerBuilder(): RoomDatabase.Builder<StorageManager> = Room.databaseBuilder<StorageManager>(name = DATABASE_FILE_NAME)
