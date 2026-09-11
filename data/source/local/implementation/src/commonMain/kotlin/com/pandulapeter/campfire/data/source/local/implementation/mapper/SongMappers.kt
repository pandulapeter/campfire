/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import com.pandulapeter.campfire.data.source.local.implementation.withoutExtension

/**
 * A song file becomes a list entry: the directives win, and whatever they leave out falls back on the file name,
 * which is the one thing every file in the library is guaranteed to have.
 */
internal fun StoredFileInfo.toSong(summary: ChordProSummary) = Song(
    fileName = name,
    title = summary.metadata.title?.takeIf { it.isNotBlank() } ?: name.withoutExtension(),
    artist = summary.metadata.artist?.takeIf { it.isNotBlank() } ?: summary.metadata.subtitle?.takeIf { it.isNotBlank() }.orEmpty(),
    key = summary.metadata.key?.takeIf { it.isNotBlank() },
    tags = summary.metadata.tags,
    hasChords = summary.hasChords,
    lastModified = lastModified,
)
