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

import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.syntax.ChordProImageDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines

/**
 * The pictures a song's text names in its `{image}` directives, read and rewritten in the text itself, every other byte
 * of it left as it was, since what comes out of here is written back to the user's own file.
 *
 * Both read every image directive generously, wherever it stands: one the song draws, an asset definition
 * (`{image: id=a src=x.png}`), one with a selector (`{image-piano: …}`), one inside an environment handed to another
 * program, and one on a `#` line. What they find decides which of the library's pictures are kept, exported, and
 * guarded from a sync deletion, so a picture is never lost because the line naming it was commented out for a moment or
 * is not drawn on this device. [ChordProSummary.imageSources] holds the same set.
 */
public object ChordProImages {

    /** Every file an image directive in [text] names, never an empty one. */
    public fun referencedSources(text: String): Set<String> = ChordProLines.splitLines(text)
        .flatMapTo(mutableSetOf()) { line -> ChordProImageDirectives.sourcesOn(line).map { it.value } }

    /**
     * [text] with the file of every image directive [referencedSources] reads replaced by what [rewrite] makes of it,
     * or left as it is where [rewrite] returns null or a value holding a line break. Only the value changes: it is
     * quoted where the new one needs quotes and nowhere else, and the short form `{image: x.png}` stays short. A text
     * with nothing to rewrite returns unchanged.
     */
    public fun withSourcesRewritten(text: String, rewrite: (String) -> String?): String {
        val lines = ChordProLines.splitLines(text)
        var isChanged = false
        val rewritten = lines.map { line ->
            val sources = ChordProImageDirectives.sourcesOn(line)
            if (sources.isEmpty()) return@map line
            val builder = StringBuilder(line)
            // From the last one back, so the ranges of the ones before it still point where they did.
            sources.asReversed().forEach { source ->
                // A line break would carry the rest of the directive onto a line of its own.
                val replacement = rewrite(source.value)?.takeIf { it != source.value && it.none(::isLineBreak) } ?: return@forEach
                builder.setRange(source.writtenRange.first, source.writtenRange.last + 1, ChordProImageDirectives.written(replacement))
                isChanged = true
            }
            builder.toString()
        }
        return if (isChanged) ChordProLines.joinLines(rewritten, text) else text
    }

    /**
     * The line that draws the picture in [src], quoted where it needs to be: what the editor inserts. A line break in
     * [src] is written as a space, so the directive stays one line.
     */
    public fun directive(src: String): String {
        val value = ChordProImageDirectives.written(src.map { if (isLineBreak(it)) ' ' else it }.joinToString(""))
        return "{${ChordProImageDirectives.IMAGE}: ${ChordProImageDirectives.SRC}=$value}"
    }

    private fun isLineBreak(character: Char) = character == '\n' || character == '\r'
}
