package com.pandulapeter.campfire.data.source.localImpl.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.localImpl.implementation.model.SongEntity

internal fun SongEntity.toModel() = Song(
    id = id,
    url = url,
    languageId = languageId,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords,
    isPublic = isPublic
)

internal fun Song.toEntity() = SongEntity(
    id = id,
    url = url,
    languageId = languageId,
    title = title,
    artist = artist,
    key = key,
    isExplicit = isExplicit,
    hasChords = hasChords,
    isPublic = isPublic
)