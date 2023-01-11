package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.DatabaseEntity

internal fun DatabaseEntity.toModel() = Database(
    url = url,
    name = name,
    isEnabled = isEnabled,
    priority = priority,
    isAddedByUser = isAddedByUser
)

internal fun Database.toEntity() = DatabaseEntity(
    url = url,
    name = name,
    isEnabled = isEnabled,
    priority = priority,
    isAddedByUser = isAddedByUser
)