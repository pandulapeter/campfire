package com.pandulapeter.campfire.data.persistence

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.access.HistoryDao
import com.pandulapeter.campfire.data.persistence.access.PlaylistDao
import com.pandulapeter.campfire.data.persistence.access.SongDao
import com.pandulapeter.campfire.data.persistence.access.SongDetailDao
import com.pandulapeter.campfire.data.persistence.converter.StringListConverter

@Database(entities = [Song::class, SongDetail::class, HistoryItem::class, Playlist::class], version = 1, exportSchema = false)
@TypeConverters(value = [StringListConverter::class])
abstract class SongDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun songDetailDao(): SongDetailDao

    abstract fun historyDao(): HistoryDao

    abstract fun playlistDao(): PlaylistDao
}