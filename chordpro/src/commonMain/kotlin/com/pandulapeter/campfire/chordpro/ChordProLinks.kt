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

import com.pandulapeter.campfire.chordpro.model.ChordProLink

/**
 * Adds and removes the links of a song directly in its text, leaving every other byte of it exactly as it was, for the
 * same reason [ChordProTags] does: what comes out of here is written back to the user's own file.
 *
 * ChordPro has no directive for a link, so each is a `{meta: link https://…}` line, a custom metadata item like the
 * cover (see [ChordProCoverArt]), which any other ChordPro program keeps and ignores. Any page is taken, whatever site
 * it is on: the library is the user's, and so are the addresses written into it.
 */
object ChordProLinks {

    /**
     * Writes a link line for [url], after the last link the file already has or, where it has none, into the header.
     * An address [usableUrl] does not take, or one the song already links to, returns the text unchanged.
     */
    fun addLink(text: String, url: String): String {
        val link = usableUrl(url) ?: return text
        if (ChordProParser.parseMetadata(text).links.any { it.url == link }) return text
        val lines = ChordProSyntax.splitLines(text).toMutableList()
        lines.add(ChordProSyntax.metadataInsertionIndex(lines, ChordProSyntax.LINK_NAME), "{meta: ${ChordProSyntax.LINK_NAME} $link}")
        return ChordProSyntax.joinLines(lines, text)
    }

    /** Drops every link line naming [url], which is more than one only in a file written by hand. */
    fun removeLink(text: String, url: String): String {
        val link = ChordProSyntax.webUrl(url) ?: return text
        val lines = ChordProSyntax.splitLines(text)
        val kept = lines.filterNot { line -> ChordProSyntax.matchDirective(line.trim())?.let(ChordProSyntax::link)?.url == link }
        return if (kept.size == lines.size) text else ChordProSyntax.joinLines(kept, text)
    }

    /**
     * Makes [links] the links the file carries, in the order of [links], preserving every unrelated byte and the line
     * of a link that is unchanged and still in its place. The links that stay take the places of the link lines the
     * file already has, one after the other, and the rest follow the last of them, so a reordered list moves the
     * links rather than the lines around them. Addresses are normalized by [usableUrl], each kept once; blank names
     * are omitted, and braces and line breaks are removed from names so a label cannot write another directive into
     * the file.
     */
    fun setLinks(text: String, links: List<ChordProLink>): String {
        val wanted = links.mapNotNull { link ->
            usableUrl(link.url)?.let { ChordProLink(url = it, name = cleanName(link.name)) }
        }.distinctBy { it.url }
        val wantedUrls = wanted.mapTo(mutableSetOf()) { it.url }
        // Every place a wanted address already has is filled with the next link of the list, which there always is,
        // since there are no more such places than wanted addresses.
        val inOrder = wanted.iterator()
        val declared = mutableSetOf<String>()
        val lines = ChordProSyntax.splitLines(text)
        val kept = mutableListOf<String>()
        lines.forEach { line ->
            val directive = ChordProSyntax.matchDirective(line.trim())
            val link = directive?.let(ChordProSyntax::link)
            if (link == null) {
                kept += line
            } else if (link.url in wantedUrls && declared.add(link.url)) {
                val replacement = inOrder.next()
                kept += if (replacement == link) line else "{meta: ${ChordProSyntax.LINK_NAME} ${value(replacement)}}"
            }
        }
        val missing = inOrder.asSequence().toList()
        if (missing.isNotEmpty()) {
            kept.addAll(
                ChordProSyntax.metadataInsertionIndex(kept, ChordProSyntax.LINK_NAME),
                missing.map { "{meta: ${ChordProSyntax.LINK_NAME} ${value(it)}}" },
            )
        }
        return if (kept == lines) text else ChordProSyntax.joinLines(kept, text)
    }

    /** The value of a link directive, shared by text editing and whole-song serialization. */
    internal fun value(link: ChordProLink): String = cleanName(link.name)?.let { "${link.url} $it" } ?: link.url

    private fun cleanName(name: String?): String? = name
        ?.filterNot { it == '{' || it == '}' }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    /**
     * [value] as the address [addLink] would write, or null where it would write nothing: trimmed, and only an `http`
     * or `https` address with no whitespace in it. An address typed without its scheme (`youtu.be/…`), which is how a
     * browser's address bar shows most of them, is taken as `https`, since a page that is only served over plain
     * `http` is rare enough to be typed out in full. That is only done where the text starts with a host, though: one
     * that starts with another scheme (`mailto:`), a mistyped one (`https:/`) or a user name (`me@`) would otherwise
     * be written as an `https` address that names nothing the user meant ([ChordProSyntax.typedWebUrl], which the cover
     * art's address shares). What a field the user types an address into checks against, so that it never offers to
     * save something the file would not keep.
     */
    fun usableUrl(value: String): String? = ChordProSyntax.typedWebUrl(value)
}
