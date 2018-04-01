package com.pandulapeter.campfire.data.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Ignore
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.util.normalize

@Entity(tableName = Song.TABLE_NAME)
data class Song(
    @PrimaryKey() @ColumnInfo(name = ID) @SerializedName("id") val id: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("language") val language: String? = null,
    @SerializedName("version") val version: Int? = 0,
    @SerializedName("popularity") val popularity: Int? = 0,
    @SerializedName("isExplicit") val isExplicit: Boolean? = false
) {
    @Ignore
    @Transient
    private var normalizedTitle: String? = null
    @Ignore
    @Transient
    private var normalizedArtist: String? = null

    fun getNormalizedTitle(): String {
        if (normalizedTitle == null) {
            normalizedTitle = title.normalize()
        }
        return normalizedTitle ?: ""
    }

    fun getNormalizedArtist(): String {
        if (normalizedArtist == null) {
            normalizedArtist = artist.normalize()
        }
        return normalizedArtist ?: ""
    }

    companion object {
        const val TABLE_NAME = "songs"
        const val ID = "id"
    }
}