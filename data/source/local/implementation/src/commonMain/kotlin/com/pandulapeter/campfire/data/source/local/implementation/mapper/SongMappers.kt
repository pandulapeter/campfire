package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.implementation.model.SongEntity

internal fun SongEntity.toModel() = Song(
    id = id,
    url = url,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords
)

internal fun Song.toEntity(databaseUrl: String) = SongEntity(
    id = id,
    url = url,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords,
    databaseUrl = databaseUrl
)