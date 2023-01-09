package com.pandulapeter.campfire.data.source.localImpl.implementation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = LanguageEntity.TABLE_NAME)
internal data class LanguageEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "nameEn") val nameEn: String,
    @ColumnInfo(name = "nameHu") val nameHu: String,
    @ColumnInfo(name = "nameRo") val nameRo: String
) {

    companion object {
        const val TABLE_NAME = "languages"
    }
}