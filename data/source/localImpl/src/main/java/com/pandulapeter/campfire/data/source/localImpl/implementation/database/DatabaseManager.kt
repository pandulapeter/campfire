package com.pandulapeter.campfire.data.source.localImpl.implementation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.LanguageDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.SongDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.LanguageEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity

@Database(
    entities = [
        CollectionEntity::class,
        LanguageEntity::class,
        SongEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class DatabaseManager : RoomDatabase() {

    abstract fun getCollectionDao(): CollectionDao

    abstract fun getLanguageDao(): LanguageDao

    abstract fun getSongsDao(): SongDao
}