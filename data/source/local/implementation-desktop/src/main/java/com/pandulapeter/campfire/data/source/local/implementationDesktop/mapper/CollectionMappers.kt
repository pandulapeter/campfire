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

internal fun CollectionEntity.update(
    id: String,
    title: String,
    description: String,
    thumbnailUrl: String,
    songIds: String,
    isPublic: Boolean,
    databaseUrl: String
) = apply {
    this.id = id
    this.title = title
    this.description = description
    this.thumbnailUrl = thumbnailUrl
    this.songIds = songIds
    this.isPublic = isPublic
    this.databaseUrl = databaseUrl
}

internal fun Collection.toEntity(databaseUrl: String) = CollectionEntity().also {
    it.id = id
    it.title = title
    it.description = description
    it.thumbnailUrl = thumbnailUrl
    it.songIds = songIds.mapToString()
    it.isPublic = isPublic
    it.databaseUrl = databaseUrl
}