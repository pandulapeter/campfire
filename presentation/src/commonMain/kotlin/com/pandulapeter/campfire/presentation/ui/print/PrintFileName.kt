/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.PrintSettings

/**
 * The name a PDF is offered under. A song's is the one its own header gives it, artist and title, by the rule the
 * library and every other export name a song by - whatever its library file happens to be called, a numbered sibling
 * or a name from before its title changed. That rule lives in the local source, which this module does not see, so it
 * is spelled out here from [LibraryFiles]. A setlist's running order is told apart from its song sheets, so that the
 * two exports of one setlist do not take each other's name either.
 */
internal fun pdfFileName(source: PrintSource, settings: PrintSettings): String {
    val song = source.songs.singleOrNull()
    val base = when {
        source.isSetlist -> LibraryFiles.normalizedName(source.title) +
                if (settings.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER) RUNNING_ORDER_SUFFIX else ""
        song == null -> LibraryFiles.normalizedName(source.title)
        song.artist.isNullOrBlank() -> LibraryFiles.normalizedName(song.title)
        else -> LibraryFiles.normalizedName(song.artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR + LibraryFiles.normalizedName(song.title)
    }
    return base + PDF_EXTENSION
}

private const val RUNNING_ORDER_SUFFIX = "-running_order"
private const val PDF_EXTENSION = ".pdf"
