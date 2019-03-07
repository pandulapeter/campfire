package com.pandulapeter.campfire.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail

@Entity
data class SongDetailMetadata(
    @PrimaryKey @ColumnInfo(name = Song.ID) val id: String,
    @ColumnInfo(name = SongDetail.VERSION) val version: Int = 0
)