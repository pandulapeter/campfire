package com.pandulapeter.campfire.data.source.local.implementationDesktop.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.TranspositionEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.RawSongDetailsDao
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.SetlistDao
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.TranspositionDao
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.dao.UserPreferencesDao

@Database(
    entities = [
        DatabaseEntity::class,
        SetlistEntity::class,
        SongEntity::class,
        RawSongDetailsEntity::class,
        UserPreferencesEntity::class,
        TranspositionEntity::class
    ],
    version = 2,
    exportSchema = false
)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getSetlistDao(): SetlistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getRawSongDetailsDao(): RawSongDetailsDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao

    abstract fun getTranspositionDao(): TranspositionDao
}