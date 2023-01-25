package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SetlistEntity

internal fun SetlistEntity.toModel() = Setlist(
    id = id,
    title = title,
    songIds = songIds.mapToList(),
    priority = priority
)

internal fun Setlist.toEntity() = SetlistEntity(
    id = id,
    title = title,
    songIds = songIds.mapToString(),
    priority = priority
)