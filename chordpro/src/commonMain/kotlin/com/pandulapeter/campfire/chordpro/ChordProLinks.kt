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
        if (link in ChordProParser.parseMetadata(text).links) return text
        val lines = ChordProSyntax.splitLines(text).toMutableList()
        lines.add(ChordProSyntax.metadataInsertionIndex(lines, ChordProSyntax.LINK_NAME), "{meta: ${ChordProSyntax.LINK_NAME} $link}")
        return ChordProSyntax.joinLines(lines, text)
    }

    /** Drops every link line naming [url], which is more than one only in a file written by hand. */
    fun removeLink(text: String, url: String): String {
        val link = ChordProSyntax.webUrl(url) ?: return text
        val lines = ChordProSyntax.splitLines(text)
        val kept = lines.filterNot { line -> ChordProSyntax.matchDirective(line.trim())?.let(ChordProSyntax::link) == link }
        return if (kept.size == lines.size) text else ChordProSyntax.joinLines(kept, text)
    }

    /**
     * [value] as the address [addLink] would write, or null where it would write nothing: trimmed, and only an `http`
     * or `https` address with no whitespace in it. An address typed without its scheme (`youtu.be/…`), which is how a
     * browser's address bar shows most of them, is taken as `https`, since a page that is only served over plain
     * `http` is rare enough to be typed out in full. That is only done where the text starts with a host, though: one
     * that starts with another scheme (`mailto:`), a mistyped one (`https:/`) or a user name (`me@`) would otherwise
     * be written as an `https` address that names nothing the user meant. What a field the user types an address into
     * checks against, so that it never offers to save something the file would not keep.
     */
    fun usableUrl(value: String): String? {
        val trimmed = value.trim()
        return ChordProSyntax.webUrl(trimmed) ?: trimmed.takeIf(::startsWithHost)?.let { ChordProSyntax.webUrl("https://$it") }
    }

    /**
     * Whether what [value] holds before its path, query or fragment is a plain host with an optional port: letters,
     * digits, `-` and `.`, with a dot somewhere other than at either end, then at most a `:` and a port number. Written
     * out by hand rather than as a regex, whose character classes the JVM, Kotlin/Native and the browser read differently.
     */
    private fun startsWithHost(value: String): Boolean {
        val authority = value.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', missingDelimiterValue = "")
        if (':' in authority && (port.isEmpty() || !port.all { it in '0'..'9' })) return false
        return host.all { it.isLetterOrDigit() || it == '-' || it == '.' } && host.indexOf('.') > 0 && host.lastIndexOf('.') < host.lastIndex
    }
}
