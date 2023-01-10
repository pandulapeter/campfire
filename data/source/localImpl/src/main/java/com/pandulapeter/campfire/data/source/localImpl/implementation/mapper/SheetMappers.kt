package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Sheet
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SheetEntity

internal fun SheetEntity.toModel() = Sheet(
    url = url,
    name = name,
    isActive = isActive,
    priority = priority
)

internal fun Sheet.toEntity() = SheetEntity(
    url = url,
    name = name,
    isActive = isActive,
    priority = priority
)