package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.PlaylistEntity

internal fun PlaylistEntity.toModel() = Playlist(
    id = id,
    title = title,
    songIds = songIds.mapToList(),
    priority = priority
)

internal fun PlaylistEntity.update(
    id: String,
    title: String,
    songIds: String,
    priority: Int
) = apply {
    this.id = id
    this.title = title
    this.songIds = songIds
    this.priority = priority
}

internal fun Playlist.toEntity() = PlaylistEntity().also {
    it.id = id
    it.title = title
    it.songIds = songIds.mapToString()
    it.priority = priority
}