/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

/**
 * This destination as it reads once the song file [from] has been renamed to [to], or this very instance where it does
 * not name that file. The details screen is named after the songs it pages through, so the entry showing this one is
 * rewritten rather than popped: the action can be taken from that screen, and a song that has just been renamed is
 * still the song being read.
 */
internal fun CampfireDestination.followingSongRename(from: String, to: String): CampfireDestination = when {
    this is CampfireDestination.SongDetails && from in songFileNames -> {
        // A destination opened on a setlist that named both files - the old name and the one the song is
        // moving to, whose file this device did not have - would otherwise name the same song twice, and
        // the pager keys its pages by that name.
        val songFileNames = songFileNames.map { if (it == from) to else it }.distinct()
        copy(
            songFileNames = songFileNames,
            // The page the reader is on, so that a rename leaves them looking at the song they renamed.
            initialIndex = songFileNames.indexOf(to),
        )
    }

    this is CampfireDestination.SongEditor && fileName == from -> copy(fileName = to)

    else -> this
}

/** Whether this destination shows the song file [fileName]: the editor open on it, or a details screen paging through it. */
internal fun CampfireDestination?.isShowingSong(fileName: String) =
    this is CampfireDestination.SongEditor && this.fileName == fileName || this is CampfireDestination.SongDetails && fileName in songFileNames
