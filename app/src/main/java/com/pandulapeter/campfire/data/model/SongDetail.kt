package com.pandulapeter.campfire.data.model

import com.google.gson.annotations.SerializedName

data class SongDetail(
    @SerializedName("id") val id: String,
    @SerializedName("song") val song: String)