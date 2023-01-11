package com.pandulapeter.campfire.data.source.local.implementationAndroid.mapper

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.SongEntity

internal fun SongEntity.toModel() = Song(
    id = id,
    url = url,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords,
    isPublic = isPublic
)

internal fun Song.toEntity(databaseUrl: String) = SongEntity(
    id = id,
    url = url,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords,
    isPublic = isPublic,
    databaseUrl = databaseUrl
)