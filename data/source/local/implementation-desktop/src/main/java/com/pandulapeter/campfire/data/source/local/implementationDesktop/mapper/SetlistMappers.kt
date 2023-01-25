package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SetlistEntity

internal fun SetlistEntity.toModel() = Setlist(
    id = id,
    title = title,
    songIds = songIds.mapToList(),
    priority = priority
)

internal fun Setlist.toEntity() = SetlistEntity().also {
    it.id = id
    it.title = title
    it.songIds = songIds.mapToString()
    it.priority = priority
}