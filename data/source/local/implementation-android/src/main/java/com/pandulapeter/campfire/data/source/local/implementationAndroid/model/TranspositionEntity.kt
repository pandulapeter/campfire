package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = TranspositionEntity.TABLE_NAME, primaryKeys = ["songId", "setlistId"])
internal data class TranspositionEntity(
    @ColumnInfo(name = "songId") val songId: String,
    @ColumnInfo(name = "setlistId") val setlistId: String,
    @ColumnInfo(name = "transposition") val transposition: Int
) {

    companion object {
        const val TABLE_NAME = "transpositions"

        // Room does not support nullable primary keys, so songs opened from the main song list use an empty setlistId.
        const val NO_SETLIST_ID = ""
    }
}
