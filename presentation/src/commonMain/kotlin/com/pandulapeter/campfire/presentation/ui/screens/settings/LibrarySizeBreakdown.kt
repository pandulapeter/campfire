/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import com.pandulapeter.campfire.presentation.ui.state.LibrarySummary

/** The parts the library's size on disk is made of, in the order the library row names them. */
internal enum class LibraryPart {
    SONGS,
    SETLISTS,
    PICTURES,
    ATTACHMENTS,
}

/**
 * The parts of [summary] that take up any space, with their sizes, or none where fewer than two do: a library that is
 * only songs is told everything by its total, and a breakdown repeating it would be noise.
 */
internal fun sizeBreakdownOf(summary: LibrarySummary): List<Pair<LibraryPart, Long>> = listOf(
    LibraryPart.SONGS to summary.songsSize,
    LibraryPart.SETLISTS to summary.setlistsSize,
    LibraryPart.PICTURES to summary.picturesSize,
    LibraryPart.ATTACHMENTS to summary.attachmentsSize,
).filter { it.second > 0 }.takeIf { it.size > 1 }.orEmpty()
