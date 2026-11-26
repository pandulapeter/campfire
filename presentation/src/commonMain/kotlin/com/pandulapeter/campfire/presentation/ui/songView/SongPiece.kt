/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

/**
 * One of the chunks a song is laid out as (`model.SongUnits`: a line of a section that may be cut, else a whole
 * section) where its layout placed it: from [start] to [end] along the way the song scrolls (down the page, or across a
 * strip from the song's start edge), in the same coordinates as the [SongRows] it is reported with, [items] of the
 * section of index [section]. What is on the screen is told by these rather than by whole sections, since a section the
 * text size has made taller than the screen is read a page at a time, and only the page's lines are on it.
 */
internal data class SongPiece(
    val start: Int,
    val end: Int,
    val section: Int,
    val items: IntRange,
)
