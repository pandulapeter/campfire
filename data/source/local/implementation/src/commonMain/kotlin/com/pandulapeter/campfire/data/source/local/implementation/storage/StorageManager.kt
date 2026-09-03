package com.pandulapeter.campfire.data.source.local.implementation.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.pandulapeter.campfire.data.source.local.implementation.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.TranspositionEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.RawSongDetailsDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.SetlistDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.TranspositionDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.UserPreferencesDao

@Database(
    entities = [
        DatabaseEntity::class,
        SetlistEntity::class,
        SongEntity::class,
        RawSongDetailsEntity::class,
        UserPreferencesEntity::class,
        TranspositionEntity::class
    ],
    version = 3,
    exportSchema = false
)
@ConstructedBy(StorageManagerConstructor::class)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getSetlistDao(): SetlistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getRawSongDetailsDao(): RawSongDetailsDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao

    abstract fun getTranspositionDao(): TranspositionDao
}

// The Room compiler generates the actual implementation for every target.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
internal expect object StorageManagerConstructor : RoomDatabaseConstructor<StorageManager> {
    override fun initialize(): StorageManager
}
