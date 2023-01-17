package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.CollectionEntity

internal fun CollectionEntity.toModel() = Collection(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    songIds = songIds.mapToList(),
    isPublic = isPublic
)

internal fun Collection.toEntity(databaseUrl: String) = CollectionEntity(
    id = id,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    songIds = songIds.mapToString(),
    isPublic = isPublic,
    databaseUrl = databaseUrl
)