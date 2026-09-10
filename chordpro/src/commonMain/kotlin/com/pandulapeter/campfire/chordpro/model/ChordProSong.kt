/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * A parsed ChordPro song: the directives that describe it plus the blocks that make up its body.
 */
data class ChordProSong(
    val metadata: ChordProMetadata,
    val blocks: List<ChordProBlock>
) {

    /** True if any lyrics or grid line contains at least one real chord (annotations don't count). */
    val hasChords: Boolean
        get() = blocks.any { block ->
            block is ChordProBlock.Section && block.lines.any { line ->
                when (line) {
                    is ChordProLine.Lyrics -> line.chords.any { !it.isAnnotation }
                    is ChordProLine.Grid -> line.tokens.any { it is GridToken.Chord }
                    is ChordProLine.Tab -> false
                    ChordProLine.Blank -> false
                }
            }
        }
}
