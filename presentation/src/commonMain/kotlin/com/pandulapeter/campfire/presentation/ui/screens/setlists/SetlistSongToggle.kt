/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

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
