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

import com.pandulapeter.campfire.chordpro.model.ChordProMetadata

/**
 * The directives that say what a song is — what it is called, who made it, what record it came out on — set directly in
 * its text and leaving every other byte of it as it was, for the same reason [ChordProTags] does: what comes out of here
 * is written back to the user's own file.
 */
object ChordProMetadataFields {

    /**
     * One directive a song says one thing with, under the name the app knows it by (see
     * [ChordProSyntax.metadataKind]). The repeatable ones — tags, languages, links — and the cover have editors of
     * their own.
     *
     * How the song is played is here too — [KEY], [CAPO], [TEMPO] and [TIME] — as the values the song itself declares,
     * which a setlist or a device may override where the song is read without ever touching the file. A later `{key}`,
     * `{tempo}` or `{time}` is a change in the body rather than a second value of its field, so [KEY], [TEMPO] and [TIME]
     * edit the line the song starts in and leave every other line of theirs where it stands.
     */
    enum class Field(val directiveName: String) {
        TITLE("title"),
        SUBTITLE("subtitle"),
        ARTIST("artist"),
        COMPOSER("composer"),
        LYRICIST("lyricist"),
        ALBUM("album"),
        YEAR("year"),
        DURATION("duration"),
        KEY("key"),
        CAPO("capo"),
        TEMPO("tempo"),
        TIME("time"),
    }

    /** What [metadata] says for [field], as text, or null where the song declares nothing for it. */
    fun valueOf(metadata: ChordProMetadata, field: Field): String? = when (field) {
        Field.TITLE -> metadata.title
        Field.SUBTITLE -> metadata.subtitle
        Field.ARTIST -> metadata.artist
        Field.COMPOSER -> metadata.composer
        Field.LYRICIST -> metadata.lyricist
        Field.ALBUM -> metadata.album
        Field.YEAR -> metadata.year
        Field.DURATION -> metadata.duration
        Field.KEY -> metadata.key
        Field.CAPO -> metadata.capo?.toString()
        Field.TEMPO -> metadata.tempo
        Field.TIME -> metadata.time
    }?.takeIf { it.isNotBlank() }

    /**
     * Makes each value of [values] what the song says for its field, in one pass over [text]; a field not in [values]
     * is left alone. The line the parser reads the value from is rewritten where it stands, in the spelling it was
     * written in (`{t: …}` stays short, `{meta: title …}` stays a `meta`), and the other lines of the same field, which
     * the parser reads past, are dropped — except for [Field.KEY], [Field.TEMPO] and [Field.TIME], whose other lines are
     * changes mid-song. A field the song does not declare yet gets a line in the header, where
     * [ChordProSyntax.metadataInsertionIndex] puts it. A null or blank value removes the field instead. A line break
     * in a value is read as a space, since it would otherwise end the directive and leave the rest in the song as
     * lyrics. A text that already says all of it returns unchanged.
     */
    fun set(text: String, values: Map<Field, String?>): String = values.entries.fold(text) { current, (field, value) ->
        set(text = current, field = field, value = value)
    }

    private fun set(text: String, field: Field, value: String?): String {
        val newValue = value?.replace('\r', ' ')?.replace('\n', ' ')?.trim()?.takeIf { it.isNotEmpty() }
        val lines = ChordProSyntax.splitLines(text)
        val indices = lines.indices.filter { lines[it].kind() == field.directiveName }
        // The line the parser takes the value from: the first key, tempo or time signature that names one, since a later
        // one is a change mid-song, and the last line that says anything of every other field. An empty line of the template stands in where
        // none says anything, so that filling a field in fills the line the new song was created with.
        val declaring = indices.filter { !lines[it].value().isNullOrEmpty() }
        val effectiveIndex = if (field.isChangedInTheBody) declaring.firstOrNull() ?: indices.firstOrNull() else declaring.lastOrNull() ?: indices.lastOrNull()
        val kept = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            when {
                index !in indices || (field.isChangedInTheBody && index != effectiveIndex) -> kept += line
                index == effectiveIndex && newValue != null -> kept += if (line.value() == newValue) line else line.rewritten(field, newValue)
            }
        }
        if (newValue != null && effectiveIndex == null) {
            kept.add(ChordProSyntax.metadataInsertionIndex(kept, field.directiveName), "{${field.directiveName}: $newValue}")
        }
        return if (kept == lines) text else ChordProSyntax.joinLines(kept, text)
    }

    /** Whether a later line of this field is a change from where it stands rather than a duplicate the parser reads past. */
    private val Field.isChangedInTheBody get() = this == Field.KEY || this == Field.TEMPO || this == Field.TIME

    private fun String.directive() = ChordProSyntax.matchDirective(trim())

    private fun String.kind() = directive()?.let(ChordProSyntax::metadataKind)

    private fun String.value() = directive()?.let { ChordProSyntax.standardMeta(it) ?: it }?.value?.trim()

    private fun String.rewritten(field: Field, value: String): String {
        val indentation = takeWhile { it.isWhitespace() }
        val name = directive()?.name ?: field.directiveName
        return if (name == META) "$indentation{$META: ${field.directiveName} $value}" else "$indentation{$name: $value}"
    }

    private const val META = "meta"
}
