package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.RawSongDetailsDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.SetlistDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.UserPreferencesDao

@Database(
    entities = [
        DatabaseEntity::class,
        SetlistEntity::class,
        SongEntity::class,
        RawSongDetailsEntity::class,
        UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getSetlistDao(): SetlistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getRawSongDetailsDao(): RawSongDetailsDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao
}