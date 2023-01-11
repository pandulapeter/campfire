package com.pandulapeter.campfire.data.source.localImpl.implementation.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.PlaylistEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.PlaylistDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.dao.UserPreferencesDao

@Database(
    entities = [
        CollectionEntity::class,
        DatabaseEntity::class,
        PlaylistEntity::class,
        SongEntity::class,
        UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getCollectionDao(): CollectionDao

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getPlaylistDao(): PlaylistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao
}