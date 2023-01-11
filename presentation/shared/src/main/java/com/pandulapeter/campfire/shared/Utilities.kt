package com.pandulapeter.campfire.shared

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData

fun DataState<ScreenData>.describe() = when (this) {
    is DataState.Failure -> "Error\n${data.describe()}"
    is DataState.Idle -> "Idle\n${data.describe()}"
    is DataState.Loading -> "Loading\n${data.describe()}"
}

private fun ScreenData?.describe() = this?.let {
    "${collections.size} collections, ${databases.size} databases, ${playlists.size} playlists, ${songs.size} songs"
} ?: "no data"

