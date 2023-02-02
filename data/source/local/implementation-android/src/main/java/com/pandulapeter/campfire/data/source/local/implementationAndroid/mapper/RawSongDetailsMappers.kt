package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.RawSongDetailsEntity

internal fun RawSongDetailsEntity.toModel() = RawSongDetails(
    url = url,
    rawData = rawData
)

internal fun RawSongDetails.toEntity() = RawSongDetailsEntity(
    url = url,
    rawData = rawData
)