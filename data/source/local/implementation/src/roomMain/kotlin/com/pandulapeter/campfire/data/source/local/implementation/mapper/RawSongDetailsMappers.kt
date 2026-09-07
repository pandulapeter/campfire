package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.implementation.model.RawSongDetailsEntity

internal fun RawSongDetailsEntity.toModel() = RawSongDetails(
    url = url,
    rawData = rawData
)

internal fun RawSongDetails.toEntity() = RawSongDetailsEntity(
    url = url,
    rawData = rawData
)