package com.pandulapeter.campfire.data.database

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.pandulapeter.campfire.data.dao.SongDetailDao
import com.pandulapeter.campfire.data.model.SongDetail

@Database(entities = [(SongDetail::class)], version = 1, exportSchema = false)
abstract class SongDetailDatabase : RoomDatabase() {

    abstract fun songDetailDao(): SongDetailDao
}