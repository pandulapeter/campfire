package com.pandulapeter.campfire.data.source.local.implementationDesktop.mapper

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SongEntity

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

internal fun SongEntity.update(
    id: String,
    url: String,
    title: String,
    artist: String,
    key: String,
    isExplicit: Boolean,
    hasChords: Boolean,
    isPublic: Boolean,
    databaseUrl: String,
) = apply {
    this.id = id
    this.url = url
    this.title = title
    this.artist = artist
    this.key = key
    this.isExplicit = isExplicit
    this.hasChords = hasChords
    this.isPublic = isPublic
    this.databaseUrl = databaseUrl
}

internal fun Song.toEntity(databaseUrl: String) = SongEntity().also {
    it.id = id
    it.url = url
    it.title = title
    it.artist = artist
    it.key = key
    it.isExplicit = isExplicit
    it.hasChords = hasChords
    it.isPublic = isPublic
    it.databaseUrl = databaseUrl
}