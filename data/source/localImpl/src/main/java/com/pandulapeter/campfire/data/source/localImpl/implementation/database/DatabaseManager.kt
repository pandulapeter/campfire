package com.pandulapeter.campfire.data.source.localImpl.implementation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.SheetDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.SongDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SheetEntity
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity

@Database(
    entities = [
        CollectionEntity::class,
        SheetEntity::class,
        SongEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class DatabaseManager : RoomDatabase() {

    abstract fun getCollectionDao(): CollectionDao

    abstract fun getSheetDao(): SheetDao

    abstract fun getSongsDao(): SongDao
}