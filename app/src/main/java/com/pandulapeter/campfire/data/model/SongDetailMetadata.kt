package com.pandulapeter.campfire.data.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class SongDetailMetadata(
    @PrimaryKey() @ColumnInfo(name = Song.ID) val id: String,
    @ColumnInfo(name = SongDetail.VERSION) val version: Int = 0
)