/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.domain.Setlist

/**
 * The setlist with [songFileName] ticked in or out, as one tick of the song picker asks: a song ticked in goes to the
 * end, unless the setlist already names it; ticked out, its entry goes, with its slot and its overrides. Everything
 * else is the setlist as it is now, whatever another device or a rescan did to it while the picker was open.
 */
internal fun Setlist.withSongTicked(songFileName: String, isTicked: Boolean): Setlist = when {
    isTicked && entries.none { it.songFileName == songFileName } -> copy(entries = entries + Setlist.Entry(songFileName = songFileName))
    isTicked -> this
    else -> copy(entries = entries.filterNot { it.songFileName == songFileName })
}

/**
 * The setlist with every one of [songFileNames] it does not name yet added to its end, in the order given, as one write
 * of the selection's Choose setlists sheet. The songs it names already keep their places and their overrides.
 */
internal fun Setlist.withSongsAdded(songFileNames: List<String>): Setlist {
    val named = entries.mapTo(hashSetOf()) { it.songFileName }
    val added = songFileNames.distinct().filterNot { it in named }
    return if (added.isEmpty()) this else copy(entries = entries + added.map { Setlist.Entry(songFileName = it) })
}

/** The setlist without the entries of [songFileNames], which is how the selection's sheet takes back what it added. */
internal fun Setlist.withSongsTakenOut(songFileNames: Collection<String>): Setlist =
    copy(entries = entries.filterNot { it.songFileName in songFileNames })
