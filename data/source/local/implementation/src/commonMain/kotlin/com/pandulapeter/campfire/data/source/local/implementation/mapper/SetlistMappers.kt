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
    description = description,
    priority = priority,
    isArchived = isArchived,
    // A document that was edited by hand can leave a blank entry behind or name the same song twice. The second
    // mention is dropped and the first one wins, its transposition with it: the screens key their rows and the
    // pager its pages by the song's file name, and a setlist naming a song twice would put the same key up twice.
    entries = songs.filter { it.file.isNotBlank() }.distinctBy { it.file }.map { Setlist.Entry(songFileName = it.file, transposition = it.transposition) },
)

internal fun Setlist.toDocument() = SetlistDocument(
    title = title,
    description = description,
    priority = priority,
    isArchived = isArchived,
    // Written the way it is read, so that a file never carries a duplicate whatever built the setlist in memory.
    songs = entries.distinctBy { it.songFileName }.map { SetlistSongDocument(file = it.songFileName, transposition = it.transposition) },
)
