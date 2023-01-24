package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.PlaylistEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.PlaylistDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.dao.UserPreferencesDao

@Database(
    entities = [
        DatabaseEntity::class,
        PlaylistEntity::class,
        SongEntity::class,
        UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getPlaylistDao(): PlaylistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao
}