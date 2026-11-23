/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.domain.api.importing.PageSongLines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The last page's answer from [find], kept so a page read from its link runs the page model once: the import asks
 * whether it holds a song, then crops the same lines again. One entry only, keyed by the lines themselves rather than
 * anything carried between the two asks, since the text may change between them (a trailing link dropped); other lines
 * replace it.
 *
 * The lock guards only the entry, never [find]: the model run takes its own lock, which is not reentrant, and two crops
 * must not wait on each other. Only a finished answer is kept, so a run cancelled or failing (no model) is run again on
 * the next ask; two asks of the same lines at once may both run it.
 */
internal class PageSongLinesMemo(
    private val find: suspend (lines: List<String>) -> PageSongLines?,
) {
    private val mutex = Mutex()
    private var last: Entry? = null

    suspend fun songLinesOf(lines: List<String>): PageSongLines? {
        mutex.withLock { last }?.takeIf { it.lines == lines }?.let { return it.answer }
        val key = lines.toList()
        val answer = find(key)
        mutex.withLock { last = Entry(key, answer) }
        return answer
    }

    /** An answer of no song is kept as such, so the entry's presence, not a null [answer], tells a miss. */
    private class Entry(val lines: List<String>, val answer: PageSongLines?)
}
