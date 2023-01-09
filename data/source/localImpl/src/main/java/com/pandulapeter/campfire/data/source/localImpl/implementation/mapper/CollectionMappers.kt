package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.CollectionEntity

internal fun CollectionEntity.toModel() = Collection(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    songIds = songIds.split(","),
    isPublic = isPublic
)

internal fun Collection.toEntity() = CollectionEntity(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    songIds = songIds.joinToString(","),
    isPublic = isPublic
)