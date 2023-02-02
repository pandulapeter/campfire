package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.RawSongDetailsEntity

internal fun RawSongDetailsEntity.toModel() = RawSongDetails(
    url = url,
    rawData = rawData
)

internal fun RawSongDetails.toEntity() = RawSongDetailsEntity().also {
    it.url = url
    it.rawData = rawData
}