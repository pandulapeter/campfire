package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import com.pandulapeter.campfire.data.source.local.implementation.withoutExtension

/**
 * A song file becomes a list entry: the directives win, and whatever they leave out falls back on the file name,
 * which is the one thing every file in the library is guaranteed to have.
 */
internal fun StoredFileInfo.toSong(metadata: ChordProMetadata, hasChords: Boolean) = Song(
    fileName = name,
    title = metadata.title?.takeIf { it.isNotBlank() } ?: name.withoutExtension(),
    artist = metadata.artist?.takeIf { it.isNotBlank() } ?: metadata.subtitle?.takeIf { it.isNotBlank() }.orEmpty(),
    key = metadata.key?.takeIf { it.isNotBlank() },
    hasChords = hasChords,
    lastModified = lastModified
)
