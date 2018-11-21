package com.pandulapeter.campfire.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pandulapeter.campfire.data.model.remote.Song

@Entity(tableName = HistoryItem.TABLE_NAME)
data class HistoryItem(
    @PrimaryKey() @ColumnInfo(name = Song.ID) val id: String,
    @ColumnInfo(name = HistoryItem.LAST_OPENED_AT) val lastOpenedAt: Long = 0
) {
    companion object {
        const val TABLE_NAME = "history"
        const val LAST_OPENED_AT = "lastOpenedAt"
    }
}