/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.playing.SongAsPlayed
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/**
 * Whether the library's own files of [source] can be written as played ([PrintSettings.exportsAsPlayed]): where a song
 * of it is played differently from its file, and never for a setlist, whose zip's manifest already says how each song is
 * played; written into the songs as well, a Campfire importing the zip would apply it twice.
 */
internal fun isAsPlayedOffered(source: PrintSource?) = source != null && !source.hasRunningOrder && source.songs.any { it.asPlayed != null }

/** The song files of [source] rewritten as they are played, by file name: none where the export writes them as stored. */
internal fun asPlayedFilesOf(source: PrintSource?, settings: PrintSettings): Map<String, SongAsPlayed> =
    if (settings.exportsAsPlayed && isAsPlayedOffered(source)) {
        source?.songs.orEmpty().mapNotNull { song -> song.asPlayed?.let { song.fileName to it } }.toMap()
    } else {
        emptyMap()
    }
