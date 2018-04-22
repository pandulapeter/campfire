package com.pandulapeter.campfire.data.model.remote

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.util.normalize
import kotlinx.android.parcel.IgnoredOnParcel

@Entity(tableName = Collection.TABLE_NAME)
data class Collection(
    @PrimaryKey() @ColumnInfo(name = ID) @SerializedName(ID) val id: String,
    @ColumnInfo(name = TITLE) @SerializedName(TITLE) val title: String = "",
    @ColumnInfo(name = DESCRIPTION) @SerializedName(DESCRIPTION) val description: String = "",
    @ColumnInfo(name = IMAGE_URL) @SerializedName(IMAGE_URL) val imageUrl: String = "",
    @ColumnInfo(name = SONGS) @SerializedName(SONGS) val songs: List<String> = listOf(),
    @ColumnInfo(name = LANGUAGE) @SerializedName(LANGUAGE) val language: List<String>? = null,
    @ColumnInfo(name = POPULARITY) @SerializedName(POPULARITY) val popularity: Int? = 0,
    @ColumnInfo(name = DATE) @SerializedName(DATE) val date: Long? = 0,
    @ColumnInfo(name = IS_EXPLICIT) @SerializedName(IS_EXPLICIT) val isExplicit: Boolean? = false,
    @ColumnInfo(name = IS_SAVED) @SerializedName(IS_SAVED) var isSaved: Boolean? = false
) {


    @IgnoredOnParcel
    @Ignore
    @Transient
    private var normalizedTitle: String? = null

    @IgnoredOnParcel
    @Ignore
    @Transient
    var isNew = false

    fun getNormalizedTitle(): String {
        if (normalizedTitle == null) {
            normalizedTitle = title.normalize()
        }
        return normalizedTitle ?: ""
    }

    companion object {
        const val TABLE_NAME = "collections"
        const val ID = "id"
        private const val TITLE = "title"
        private const val DESCRIPTION = "description"
        private const val IMAGE_URL = "imageUrl"
        private const val SONGS = "songs"
        private const val LANGUAGE = "language"
        private const val POPULARITY = "popularity"
        private const val DATE = "date"
        private const val IS_EXPLICIT = "isExplicit"
        private const val IS_SAVED = "isSaved"
    }
}