/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * Sets the cover image of a song directly in its text, leaving every other byte of it exactly as it was, for the same
 * reason [ChordProTags] does: what comes out of here is written back to the user's own file.
 *
 * ChordPro has no directive for a cover, so this is `{meta: cover https://…}`, a custom metadata item like the
 * language of a song (see [ChordProLanguages]), which any other ChordPro program keeps and ignores.
 */
object ChordProCoverArt {

    /**
     * Makes [url] the song's cover: the first cover line the file has is rewritten in place and every other one
     * dropped, or a new one is written into the header where the file has none. A null [url], or one that is not an
     * `http` or `https` address, removes every cover line instead. A text that already says exactly that returns
     * unchanged.
     */
    fun set(text: String, url: String?): String {
        val cover = url?.let(ChordProSyntax::webUrl)
        val lines = ChordProSyntax.splitLines(text)
        val newLine = cover?.let { "{meta: ${ChordProSyntax.COVER_NAME} $it}" }
        val kept = mutableListOf<String>()
        var hasWritten = false
        lines.forEach { line ->
            if (!line.isCover()) {
                kept += line
            } else if (newLine != null && !hasWritten) {
                // A line that already names this cover stays exactly as it is written, spacing and spelling included.
                kept += if (ChordProSyntax.matchDirective(line.trim())?.let(ChordProSyntax::cover) == cover) line else newLine
                hasWritten = true
            }
        }
        if (newLine != null && !hasWritten) {
            kept.add(ChordProSyntax.metadataInsertionIndex(kept, ChordProSyntax.COVER_NAME), newLine)
        }
        return if (kept == lines) text else ChordProSyntax.joinLines(kept, text)
    }

    /**
     * [value] as the address [set] would write, or null where it would take the cover off instead: trimmed, and only an
     * `http` or `https` address with no whitespace in it. What a field the user types an address into checks against,
     * so that it never offers to save something the file would not keep.
     */
    fun usableUrl(value: String): String? = ChordProSyntax.webUrl(value)

    /** Whether the line is a `{meta: cover …}` directive, usable or not. */
    private fun String.isCover() = ChordProSyntax.matchDirective(trim())?.let(ChordProSyntax::isCoverMeta) == true
}
