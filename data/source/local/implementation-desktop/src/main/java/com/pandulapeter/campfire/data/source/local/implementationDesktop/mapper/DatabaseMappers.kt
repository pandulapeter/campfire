package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity

internal fun DatabaseEntity.toModel() = Database(
    url = url,
    name = name,
    isEnabled = isEnabled,
    priority = priority,
    isAddedByUser = isAddedByUser
)

internal fun Database.toEntity() = DatabaseEntity().also {
    it.url = url
    it.name = name
    it.isEnabled = isEnabled
    it.priority = priority
    it.isAddedByUser = isAddedByUser
}