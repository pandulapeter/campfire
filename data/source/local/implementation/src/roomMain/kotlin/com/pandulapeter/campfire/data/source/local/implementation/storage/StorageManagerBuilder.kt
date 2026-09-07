package com.pandulapeter.campfire.data.source.local.implementation.storage

import androidx.room.RoomDatabase
import org.koin.core.scope.Scope

internal const val DATABASE_FILE_NAME = "campfire.db"

/**
 * Every platform stores the database in a different location, and Android additionally needs a [android.content.Context] from Koin.
 */
internal expect fun Scope.createStorageManagerBuilder(): RoomDatabase.Builder<StorageManager>
