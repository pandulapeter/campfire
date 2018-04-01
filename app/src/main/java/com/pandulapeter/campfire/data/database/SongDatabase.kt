package com.pandulapeter.campfire.data.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.pandulapeter.campfire.data.dao.SongDao
import com.pandulapeter.campfire.data.model.Song

@Database(entities = [(Song::class)], version = 1)
abstract class SongDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
}