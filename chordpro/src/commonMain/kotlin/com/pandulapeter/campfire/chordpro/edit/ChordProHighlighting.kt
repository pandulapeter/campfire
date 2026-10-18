/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

/**
 * What [ChordProHighlighter.highlight] found in [text]: its [tokens], and what it keeps of every line to read the next
 * version of the text from, so that an edit costs the lines it changed rather than the whole song. It is immutable, so
 * the caller only has to hold the last one and hand it back with the next text.
 */
public class ChordProHighlighting internal constructor(
    public val text: String,
    public val tokens: List<ChordProHighlighter.Token>,
    internal val lines: List<String>,
    internal val lineStarts: IntArray,
    internal val bodyStart: Int,
    /** How many of [tokens] the lines up to and including each one have. */
    internal val tokenEnds: IntArray,
    /** The state each line leaves for the next one, see [HighlightState]. */
    internal val statesAfter: List<HighlightState>,
) {

    /** The state the line at [index] is read in. */
    internal fun stateBefore(index: Int) = if (index == 0) HighlightState.START else statesAfter[index - 1]

    /** The index in [tokens] of the line at [index]'s first token. */
    internal fun tokenStart(index: Int) = if (index == 0) 0 else tokenEnds[index - 1]
}

/**
 * What reading a line of a ChordPro text carries to the next one: the [environment] it leaves open, the once-only
 * kinds of metadata said so far ([saidOnce]) and whether the header has a key line ([hasHeaderKey]). A line that
 * changes none of them leaves the very same object, so the one set is shared by every line until the next is said.
 */
internal data class HighlightState(
    val environment: String?,
    val saidOnce: Set<String>,
    val hasHeaderKey: Boolean,
) {
    companion object {
        val START = HighlightState(environment = null, saidOnce = emptySet(), hasHeaderKey = false)
    }
}
