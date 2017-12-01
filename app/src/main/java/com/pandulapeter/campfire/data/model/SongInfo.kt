package com.pandulapeter.campfire.data.model

import android.annotation.SuppressLint
import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

/**
 * Contains basic metadata about a song.
 */
@SuppressLint("ParcelCreator")
@Parcelize data class SongInfo(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("language") val language: String?,
    @SerializedName("version") val version: Int?, //TODO: Notify users about updated songs.
    @SerializedName("isExplicit") val isExplicit: Boolean?) : Parcelable