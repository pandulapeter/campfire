/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

import com.pandulapeter.campfire.chordpro.ChordProParser

/**
 * The `{transpose}` directives of a song, the way the spec reads them: each one is the transposition of the rest of
 * the song (not an addition to the one before it), and one with no value goes back to the one before it. What a
 * song opens with — every `{transpose}` before its first line — is the whole song's; a later one is a modulation.
 *
 * The body begins at the first line that is not blank or a `#` comment, the first `{start_of_…}`, or the first
 * directive that makes a block; a stray `{end_of_…}` or a directive that only sets metadata puts nothing in the song.
 */
internal class Transposition {
    private val restorable = mutableListOf<Int>()
    private var current = 0
    private var lastReported = 0

    /** The transposition of the whole song: what was in effect when the body began, or at the end of a song with none. */
    var wholeSong = 0
        private set

    /** Whether the body has begun, before which every directive is part of the header. */
    var isInBody = false
        private set

    fun startBody() {
        if (isInBody) return
        isInBody = true
        wholeSong = current
        lastReported = current
    }

    /** Reads one directive; the offset from [wholeSong] the chords after it are at, where that changed in the body. */
    fun consume(value: String?): Int? {
        val written = value?.trim().orEmpty()
        if (written.isEmpty()) {
            current = restorable.removeLastOrNull() ?: 0
        } else {
            val semitones = ChordProParser.transposeSemitones(written) ?: return null
            restorable += current
            current = semitones
        }
        if (!isInBody || current == lastReported) return null
        lastReported = current
        return current - wholeSong
    }

    fun finish() = startBody()
}
