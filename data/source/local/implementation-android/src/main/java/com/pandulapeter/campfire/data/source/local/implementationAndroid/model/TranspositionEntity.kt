package com.pandulapeter.campfire.data.source.local.implementationAndroid.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = TranspositionEntity.TABLE_NAME)
internal data class TranspositionEntity(
    @PrimaryKey @ColumnInfo(name = "songId") val songId: String,
    @ColumnInfo(name = "transposition") val transposition: Int
) {

    companion object {
        const val TABLE_NAME = "transpositions"
    }
}
