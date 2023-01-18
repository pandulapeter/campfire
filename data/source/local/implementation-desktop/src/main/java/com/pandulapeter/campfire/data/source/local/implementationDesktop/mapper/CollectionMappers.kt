package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.CollectionEntity

internal fun CollectionEntity.toModel() = Collection(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    songIds = songIds.mapToList(),
    isPublic = isPublic
)

internal fun Collection.toEntity(databaseUrl: String) = CollectionEntity().also {
    it.id = id
    it.title = title
    it.description = description
    it.thumbnailUrl = thumbnailUrl
    it.songIds = songIds.mapToString()
    it.isPublic = isPublic
    it.databaseUrl = databaseUrl
}