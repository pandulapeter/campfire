package com.pandulapeter.campfire.data.source.remote.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.remote.implementation.model.SongResponse
import com.pandulapeter.campfire.data.source.remote.implementation.model.exception.DataValidationException

internal fun SongResponse.toModel() = try {
    Song(
        id = id.toSongId(),
        url = url.toSongUrl(),
        title = title.toSongTitle(),
        artist = artist.toSongArtist(),
        key = key.toSongKey(),
        hasChords = hasChords.toSongHasChords()
    )
} catch (exception: DataValidationException) {
    println(exception.message)
    null
}

private fun String?.toSongId() = toId("Missing song ID.")

private fun String?.toSongUrl() = toUrl("Missing song URL.")

private fun String?.toSongTitle() = toText("Missing song title.")

private fun String?.toSongArtist() = toText("Missing song artist.")

private fun String?.toSongKey() = toText("Missing song key.")

private fun Boolean?.toSongHasChords() = toBoolean()