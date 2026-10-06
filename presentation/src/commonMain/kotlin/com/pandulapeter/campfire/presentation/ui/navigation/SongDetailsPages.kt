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
 * [songFileNames] with every name once, since the song pager is keyed by file name, and [index] moved to where the
 * song it pointed at is in that list (page 0 for an index that points nowhere).
 */
internal fun reportedSongPages(songFileNames: List<String>, index: Int): Pair<List<String>, Int> {
    val pages = songFileNames.distinct()
    return pages to pages.indexOf(songFileNames.getOrNull(index)).coerceAtLeast(0)
}
