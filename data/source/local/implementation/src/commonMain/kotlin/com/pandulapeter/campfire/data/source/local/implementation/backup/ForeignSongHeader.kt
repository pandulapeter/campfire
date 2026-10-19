/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.chordpro.edit.ChordProLinks

/**
 * How the readers of other apps' libraries write what those apps keep beside a song's text into the header of its
 * ChordPro file, and name the files of one batch. Shared so that a song reads the same whichever app it came from.
 */
internal object ForeignSongHeader {

    /**
     * [content] with the header [header] collects in front of it. A directive is written only where the text does not
     * declare the same thing itself, under its name or one of its aliases.
     */
    fun songText(content: String, header: Header.() -> Unit): String {
        val declared = DIRECTIVE.findAll(content).mapTo(mutableSetOf()) { it.groupValues[1].lowercase() }
        val lines = Header(declared).apply(header).lines
        return (lines + content.trimStart('\n', '\r')).joinToString(separator = "\n").trimEnd() + "\n"
    }

    /** The directives of one song's header, in the order they are added. */
    class Header(private val declared: Set<String>) {

        val lines = mutableListOf<String>()

        fun directive(name: String, value: String?, vararg aliases: String) {
            val written = value?.let(::headerValue) ?: return
            if ((aliases.toList() + name).none { it in declared }) lines += "{$name: $written}"
        }

        fun link(url: String?) {
            url?.let(::linkUrl)?.let { lines += "{meta: link $it}" }
        }

        fun tags(names: List<String>) {
            names.mapNotNull(::headerValue).distinct().forEach { lines += "{tag: $it}" }
        }
    }

    /**
     * A value as one directive can hold it: on one line, its line breaks a " / " and every other run of whitespace one
     * space, and no braces, which would end the directive early. The other apps' fields are free text, and a multi-line
     * copyright reads as two credits, which is why the line breaks are kept as a separator rather than a space.
     */
    fun headerValue(text: String) = text
        .split(LINE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")
        .replace(WHITESPACE, " ")
        .replace('{', '(').replace('}', ')')
        .takeIf { it.isNotBlank() }

    /**
     * [text] as an address a `{meta: link}` directive keeps whole, or null where the parser would not read one: a space
     * or a brace has no place in a directive's address, so they are percent-encoded - which still opens the page
     * the other app did, since a browser encodes them the same way - and a line break is dropped.
     */
    private fun linkUrl(text: String) = ChordProLinks.usableUrl(
        text.replace(LINE_BREAK, "").replace(WHITESPACE, "%20").replace("{", "%7B").replace("}", "%7D"),
    )

    /**
     * A name for the file in the batch, which only has to be unique within it: a setlist entry points at its song by it,
     * and the song is named by its own header once it is imported. It is what the import report shows for the file, so
     * it is the title as it was written, without what a file name cannot hold and without the leading dot that would
     * make it a hidden file.
     */
    fun uniqueName(base: String, extension: String, usedNames: BatchNames): String {
        val cleaned = base.replace(UNSAFE_CHARACTERS, " ").trim().trimStart('.').ifBlank { "untitled" }
        var name = cleaned + extension
        val key = name.lowercase()
        var number = usedNames.nextNumbers[key] ?: 2
        while (!usedNames.used.add(name.lowercase())) name = "$cleaned ${number++}$extension"
        usedNames.nextNumbers[key] = number
        return name
    }

    /** The name of every directive the text declares, `{title: …}` and `{t:…}` alike. */
    private val DIRECTIVE = Regex("""\{\s*([A-Za-z_]+)\s*[:}\s]""")

    private val LINE_BREAK = Regex("[\\r\\n\\u2028\\u2029]+")

    private val WHITESPACE = Regex("""\s+""")

    private val UNSAFE_CHARACTERS = Regex("""[/\\:*?"<>|\u0000-\u001f]+""")
}
