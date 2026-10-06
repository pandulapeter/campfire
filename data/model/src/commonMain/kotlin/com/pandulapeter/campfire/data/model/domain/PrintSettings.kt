/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * The export screen's choices, local preferences independent of how a song is read on screen: the [format] the file is
 * written in, and the rest for how a PDF is laid out, which an export of the library's own files has no use for. Every
 * box of what is printed starts ticked, so that a new user's first PDF holds everything and what they untick is
 * remembered from then on.
 */
data class PrintSettings(
    val format: Format = Format.PDF,
    val paper: Paper = Paper.A4,
    val isLandscape: Boolean = false,
    val fontSize: Int = 12,
    val marginMm: Int = 15,
    val columns: Int = 1,
    val showChords: Boolean = true,
    /** The diagram of every chord a song plays under its heading, with [showChords] only, since it fingers chords that are printed. */
    val showChordDiagrams: Boolean = true,
    /** The key, the transposition it is printed in and the capo, on a line of their own under a song's heading. */
    val showKey: Boolean = true,
    /** The tempo and the time signature, under a song's heading and where the song changes them. */
    val showTempo: Boolean = true,
    val showComments: Boolean = true,
    val showMetadata: Boolean = true,
    val showPageNumbers: Boolean = true,
    val startSongsOnNewPage: Boolean = true,
    val setlistMode: SetlistMode = SetlistMode.SONG_SHEETS,
    val includeSetlistOverview: Boolean = true,
) {
    fun normalized() = copy(fontSize = fontSize.coerceIn(8, 20), marginMm = marginMm.coerceIn(10, 25), columns = columns.coerceIn(1, MAX_COLUMNS))

    /** [FILES] is what the library holds: a song's ChordPro file, or a setlist's zip of its manifest and its songs. */
    enum class Format(val id: String) {
        PDF("pdf"),
        FILES("files"),
    }

    enum class Paper(val id: String, val width: Float, val height: Float) {
        A4("a4", 595.276f, 841.89f),
        LETTER("letter", 612f, 792f),
    }

    enum class SetlistMode(val id: String) {
        SONG_SHEETS("song_sheets"),
        RUNNING_ORDER("running_order"),
    }

    companion object {
        const val MAX_COLUMNS = 4
    }
}
