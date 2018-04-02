package com.pandulapeter.campfire.data.persistence

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.access.SongDao
import com.pandulapeter.campfire.data.persistence.access.SongDetailDao

@Database(entities = [Song::class, SongDetail::class], version = 1, exportSchema = false)
abstract class SongDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun songDetailDao(): SongDetailDao
}