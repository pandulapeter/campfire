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

import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.syntax.ChordProAttachmentDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProHeaderLayout
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems

/**
 * The files that go with a song, read and written in its text, every other byte of it left as it was, as [ChordProLinks]
 * does for links: each is a `{meta: attachment 3fa9c2e81b7d4f06.pdf Rehearsal take.pdf}` line, the library file first
 * and the name it is shown under after it, a custom metadata item any other ChordPro program keeps and ignores.
 *
 * Reading for what a song keeps is generous, as [ChordProImages]' is: a line on a `#` comment or inside an environment
 * handed to another program still names its file, which [ChordProSummary.attachmentSources] holds too.
 */
public object ChordProAttachments {

    /** Every file an attachment directive in [text] names, read generously. */
    public fun referencedSources(text: String): Set<String> = ChordProLines.splitLines(text)
        .mapNotNullTo(mutableSetOf(), ChordProAttachmentDirectives::sourceOn)

    /**
     * Makes [attachments] the attachments the file carries, in their order, keeping every unrelated byte and the line of an
     * attachment that is unchanged and still in its place, the way [ChordProLinks.setLinks] places links. A source that is
     * no bare file name is left out, each file is kept once, and a blank name is omitted; braces and line breaks are taken
     * out of names, so a name cannot write another directive into the file. Commented-out lines are not the song's
     * attachments, and are left as they are.
     */
    public fun setAttachments(text: String, attachments: List<ChordProAttachment>): String {
        val wanted = attachments
            .filter { ChordProMetaItems.isAttachmentSource(it.source) }
            .map { ChordProAttachment(source = it.source, name = ChordProDirectiveValues.name(it.name)) }
            .distinctBy { it.source }
        val wantedSources = wanted.mapTo(mutableSetOf()) { it.source }
        // Every place a wanted file already has is filled with the next attachment of the list, which there always is,
        // since there are no more such places than wanted files.
        val inOrder = wanted.iterator()
        val declared = mutableSetOf<String>()
        val lines = ChordProLines.splitLines(text)
        val kept = mutableListOf<String>()
        lines.forEach { line ->
            val attachment = ChordProDirectives.matchDirective(line.trim())?.let(ChordProMetaItems::attachment)
            if (attachment == null) {
                kept += line
            } else if (attachment.source in wantedSources && declared.add(attachment.source)) {
                val replacement = inOrder.next()
                kept += if (replacement == attachment) line else directive(replacement)
            }
        }
        val missing = inOrder.asSequence().toList()
        if (missing.isNotEmpty()) {
            kept.addAll(ChordProHeaderLayout.metadataInsertionIndex(kept, ChordProMetaItems.ATTACHMENT_NAME), missing.map(::directive))
        }
        return if (kept == lines) text else ChordProLines.joinLines(kept, text)
    }

    /**
     * [text] with the file of every attachment line [referencedSources] reads replaced by what [rewrite] makes of it, or
     * left as it is where [rewrite] returns null or something [ChordProMetaItems.isAttachmentSource] would not read back as
     * a file: what Save does with a file that had to take another name in the library. Only the file changes, its name
     * and every other byte stay; a text with nothing to rewrite returns unchanged.
     */
    public fun withAttachmentSourcesRewritten(text: String, rewrite: (String) -> String?): String {
        val lines = ChordProLines.splitLines(text)
        var isChanged = false
        val rewritten = lines.map { line ->
            val range = ChordProAttachmentDirectives.sourceRangeOn(line) ?: return@map line
            val source = line.substring(range.first, range.last + 1)
            val replacement = rewrite(source)?.takeIf { it != source && ChordProMetaItems.isAttachmentSource(it) } ?: return@map line
            isChanged = true
            line.replaceRange(range, replacement)
        }
        return if (isChanged) ChordProLines.joinLines(rewritten, text) else text
    }

    /**
     * [text] without a line for any of [sources]: every line [referencedSources] reads one of them on, commented-out ones
     * included, so that nothing in the text names a file that does not travel with it. What a shared link's copy does
     * with an attachment it leaves out for its size; a text naming none of them returns unchanged.
     */
    public fun withoutAttachments(text: String, sources: Set<String>): String {
        if (sources.isEmpty()) return text
        val lines = ChordProLines.splitLines(text)
        val kept = lines.filterNot { ChordProAttachmentDirectives.sourceOn(it) in sources }
        return if (kept.size == lines.size) text else ChordProLines.joinLines(kept, text)
    }

    /** The line that carries [attachment], what the editor inserts; its name cleaned as [setAttachments] cleans it. */
    public fun directive(attachment: ChordProAttachment): String =
        "{meta: ${ChordProMetaItems.ATTACHMENT_NAME} ${listOfNotNull(attachment.source, ChordProDirectiveValues.name(attachment.name)).joinToString(" ")}}"
}
