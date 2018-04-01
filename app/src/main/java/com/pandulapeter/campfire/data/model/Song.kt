package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName
import com.pandulapeter.campfire.util.replaceSpecialCharacters

data class Song(
    @SerializedName("id") val id: String = "",
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
}