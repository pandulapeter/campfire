package com.pandulapeter.campfire.data.model.local

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.model.remote.SongDetail

@Entity
data class SongDetailMetadata(
    @PrimaryKey() @ColumnInfo(name = Song.ID) val id: String,
    @ColumnInfo(name = SongDetail.VERSION) val version: Int = 0
)