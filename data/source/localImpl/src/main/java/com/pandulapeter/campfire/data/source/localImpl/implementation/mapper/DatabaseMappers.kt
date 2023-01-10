package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.DatabaseEntity

internal fun DatabaseEntity.toModel() = Database(
    url = url,
    name = name,
    isActive = isActive,
    priority = priority,
    isAddedByUser = isAddedByUser
)

internal fun Database.toEntity() = DatabaseEntity(
    url = url,
    name = name,
    isActive = isActive,
    priority = priority,
    isAddedByUser = isAddedByUser
)