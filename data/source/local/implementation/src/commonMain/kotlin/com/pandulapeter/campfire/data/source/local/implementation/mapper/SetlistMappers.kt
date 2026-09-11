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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument

internal fun SetlistDocument.toModel(fileName: String) = Setlist(
    fileName = fileName,
    title = title,
    priority = priority,
    // A document that was edited by hand can name the same song twice or leave a blank entry behind.
    entries = songs.filter { it.file.isNotBlank() }.map { Setlist.Entry(songFileName = it.file, transposition = it.transposition) },
)

internal fun Setlist.toDocument() = SetlistDocument(
    title = title,
    priority = priority,
    songs = entries.map { SetlistSongDocument(file = it.songFileName, transposition = it.transposition) },
)
