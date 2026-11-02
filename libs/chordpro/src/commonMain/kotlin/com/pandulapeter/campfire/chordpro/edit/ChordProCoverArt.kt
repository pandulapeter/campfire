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

import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProHeaderLayout
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems

/**
 * Sets the cover image of a song directly in its text, leaving every other byte of it exactly as it was, for the same
 * reason [ChordProTags] does: what comes out of here is written back to the user's own file.
 *
 * ChordPro has no directive for a cover, so this is `{meta: cover https://…}`, a custom metadata item like the
 * language of a song (see [ChordProLanguages]), which any other ChordPro program keeps and ignores.
 */
public object ChordProCoverArt {

    /**
     * Makes [url] the song's cover: the first cover line the file has is rewritten in place and every other one
     * dropped, or a new one is written into the header where the file has none. A null [url], or one that is not an
     * `http` or `https` address, removes every cover line instead. A text that already says exactly that returns
     * unchanged.
     */
    public fun set(text: String, url: String?): String {
        val cover = url?.let(ChordProMetaItems::webUrl)
        val lines = ChordProLines.splitLines(text)
        val newLine = cover?.let { "{meta: ${ChordProMetaItems.COVER_NAME} $it}" }
        val kept = mutableListOf<String>()
        var hasWritten = false
        lines.forEach { line ->
            if (!line.isCover()) {
                kept += line
            } else if (newLine != null && !hasWritten) {
                // A line that already names this cover stays exactly as it is written, spacing and spelling included.
                kept += if (ChordProDirectives.matchDirective(line.trim())?.let(ChordProMetaItems::cover) == cover) line else newLine
                hasWritten = true
            }
        }
        if (newLine != null && !hasWritten) {
            kept.add(ChordProHeaderLayout.metadataInsertionIndex(kept, ChordProMetaItems.COVER_NAME), newLine)
        }
        return if (kept == lines) text else ChordProLines.joinLines(kept, text)
    }

    /**
     * [value] as the address [set] would write, or null where it would take the cover off instead: trimmed, and only an
     * `http` or `https` address with no whitespace in it. An address typed without its scheme is taken as `https`, the
     * way a link's is ([ChordProMetaItems.typedWebUrl]), and [set] is handed the completed address. What a field the user
     * types an address into checks against, so that it never offers to save something the file would not keep.
     */
    public fun usableUrl(value: String): String? = ChordProMetaItems.typedWebUrl(value)

    /** Whether the line is a `{meta: cover …}` directive, usable or not. */
    private fun String.isCover() = ChordProDirectives.matchDirective(trim())?.let(ChordProMetaItems::isCoverMeta) == true
}
