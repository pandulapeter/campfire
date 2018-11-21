package com.pandulapeter.campfire.data.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pandulapeter.campfire.data.model.local.HistoryItem
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail
import com.pandulapeter.campfire.data.persistence.access.*
import com.pandulapeter.campfire.data.persistence.converter.StringListConverter

@Database(entities = [Song::class, SongDetail::class, HistoryItem::class, Playlist::class, Collection::class], version = 1, exportSchema = false)
@TypeConverters(value = [StringListConverter::class])
abstract class Database : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun songDetailDao(): SongDetailDao

    abstract fun historyDao(): HistoryDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun collectionDao(): CollectionDao
}