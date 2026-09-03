package com.pandulapeter.campfire.data.source.local.implementation.storage

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.core.scope.Scope

internal actual fun Scope.createStorageManagerBuilder(): RoomDatabase.Builder<StorageManager> = get<Context>().applicationContext.let { context ->
    Room.databaseBuilder<StorageManager>(
        context = context,
        name = context.getDatabasePath(DATABASE_FILE_NAME).absolutePath
    )
}
