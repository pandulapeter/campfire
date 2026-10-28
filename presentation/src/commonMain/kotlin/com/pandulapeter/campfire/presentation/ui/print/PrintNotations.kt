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

import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.abcTunesOf
import com.pandulapeter.campfire.presentation.ui.notation.renderNotationDrawing

/**
 * The ABC tunes of [source] engraved for the song sheets' columns as [settings] lays them out, by song file and by
 * tune, for [layoutPrintDocument]; each song's in its own transposition and chord notation, each copy of a tune under
 * the key change it stands after, as on its page. None where
 * the chords are not printed, which leaves verbatim runs out altogether, and none for a tune that cannot be rendered,
 * which prints as its text.
 */
internal suspend fun printNotationsOf(source: PrintSource, settings: PrintSettings, renderer: AbcRenderer): Map<String, Map<AbcTune, NotationDrawing>> {
    if (!settings.normalized().showSheetMusic) return emptyMap()
    val width = (printColumnWidth(settings) / printNotationUnit(settings)).toInt()
    return source.songs.mapNotNull { song ->
        val tunes = abcTunesOf(song.song?.blocks.orEmpty())
        val drawings = tunes.mapNotNull { tune ->
            renderNotationDrawing(renderer, tune.text, width, song.notationChords.movedBy(tune.offset), song.title)?.let { tune to it }
        }.toMap()
        drawings.takeIf { it.isNotEmpty() }?.let { song.fileName to it }
    }.toMap()
}
