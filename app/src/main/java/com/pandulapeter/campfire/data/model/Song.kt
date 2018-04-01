package com.pandulapeter.campfire.data.model

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.util.replaceSpecialCharacters

@Entity(tableName = Song.TABLE_NAME)
data class Song(
    @PrimaryKey() @SerializedName("id") val id: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("language") val language: String? = null,
    @SerializedName("version") val version: Int? = 0,
    @SerializedName("popularity") val popularity: Int? = 0,
    @SerializedName("isExplicit") val isExplicit: Boolean? = false,
    val downloadedVersion: Int? = null
) {
    @delegate:Transient
    val titleWithSpecialCharactersRemoved by lazy { title.replaceSpecialCharacters() }
    @delegate:Transient
    val artistWithSpecialCharactersRemoved by lazy { artist.replaceSpecialCharacters() }

    companion object {
        const val TABLE_NAME = "songs"
    }
}