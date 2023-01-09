package com.pandulapeter.campfire.data.source.localImpl.implementation.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.dao.CollectionDao
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity

@Database(
    entities = [
        CollectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
internal abstract class DatabaseManager : RoomDatabase() {

    abstract fun getCollectionDao(): CollectionDao
}