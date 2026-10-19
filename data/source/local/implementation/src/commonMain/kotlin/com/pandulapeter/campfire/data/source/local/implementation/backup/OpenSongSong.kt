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

import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.ChordSheetLyrics
import com.pandulapeter.campfire.data.formats.document.XmlElement
import com.pandulapeter.campfire.data.formats.document.parseXml
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.song.Song

/**
 * Reads one song of OpenSong, the XML file (usually without an extension) that OpenSong and OpenSongApp keep each song
 * in, and that OpenSongApp's `.osb` backup is a zip archive of. Its `<lyrics>` are a text of their own: `[V1]` opens a
 * section, a line starting with `.` is a chord row over the lyric line under it, one starting with a space is a lyric
 * line, one starting with a digit is the lyric of that verse sharing the chord rows above it, one starting with `;` a
 * comment, `||` and `|` are line and column breaks, and `---` ends a slide.
 *
 * An OpenLyrics document has a root `song` and `lyrics` too, but its lyrics are elements, so it is not read here.
 */
internal object OpenSongSong {

    /** The song as ChordPro, or null where [text] is not one of OpenSong's; [fallbackTitle] titles one without a title. */
    fun read(text: String, fallbackTitle: String): String? {
        val root = try {
            parseXml(text)
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: IllegalStateException) {
            return null
        }
        val lyrics = root.child("lyrics")?.takeIf { root.localName == "song" && it.children.isEmpty() } ?: return null
        return ForeignSongHeader.songText(body(lyrics.text)) {
            directive("title", root.value("title") ?: fallbackTitle, "t")
            directive("artist", root.value("author"))
            directive("key", root.value("key"))
            directive("capo", root.number("capo")?.takeIf { it > 0 && it in Song.CAPO_RANGE }?.toString())
            directive("tempo", root.number("tempo")?.takeIf { it in MetronomeSettings.TEMPO_RANGE }?.toString())
            directive("time", root.value("timesig")?.takeIf { ChordProTime.parse(it) != null })
            directive("copyright", root.value("copyright"))
            tags(root.value("theme")?.split(';').orEmpty())
        }
    }

    private fun XmlElement.value(name: String) = child(name)?.text?.trim()?.takeIf { it.isNotEmpty() }

    private fun XmlElement.number(name: String) = value(name)?.takeWhile(Char::isDigit)?.toIntOrNull()

    private fun body(lyrics: String): String {
        val sections = mutableListOf<Section>()
        for (line in lyrics.lines()) {
            val tag = SECTION_TAG.matchEntire(line.trim())?.groupValues?.get(1)
            if (tag != null) sections += Section(tag) else (sections.lastOrNull() ?: Section(null).also(sections::add)).lines += line
        }
        return sections.flatMap(Section::verses).joinToString(separator = "\n\n")
    }

    /** The lines under one section tag, or before the first one under none. */
    private class Section(val tag: String?) {
        val lines = mutableListOf<String>()

        /** The section written out, once per verse where its lyric lines are numbered. */
        fun verses(): List<String> {
            val numbers = lines.mapNotNull { it.firstOrNull()?.digitToIntOrNull() }.distinct().sorted()
            if (numbers.isEmpty()) return listOfNotNull(written(label = label(number = null), verse = null))
            return numbers.mapNotNull { number -> written(label = label(number), verse = number) }
        }

        private fun written(label: Label?, verse: Int?): String? {
            val body = blocks(verse).takeIf { it.isNotBlank() } ?: return null
            return when {
                label == null -> body
                label.environment == null -> "{comment: ${label.name}}\n$body"
                else -> "{start_of_${label.environment}: ${label.name}}\n$body\n{end_of_${label.environment}}"
            }
        }

        /** The lyric lines of [verse] (every lyric line where it is null) set under their chord rows, block by block. */
        private fun blocks(verse: Int?): String {
            val written = mutableListOf<String>()
            val block = mutableListOf<String>()
            fun endBlock() {
                if (block.isEmpty()) return
                val text = block.joinToString("\n")
                written += ChordSheetConverter.convertFragment(text, String::normalizedToNfc)?.trimEnd() ?: ChordSheetLyrics.escaped(text)
                block.clear()
            }
            for (line in lines) {
                val number = line.firstOrNull()?.digitToIntOrNull()
                when {
                    line.isBlank() || line.trim() == "---" -> endBlock()
                    line.startsWith(';') -> {
                        endBlock()
                        line.drop(1).trim().takeIf { it.isNotEmpty() }?.let { written += "{comment: ${ForeignSongHeader.headerValue(it)}}" }
                    }
                    // Dropping the marker shifts the row by one column, as dropping a lyric line's space does, so the
                    // chords stay over their syllables.
                    line.startsWith('.') -> block += line.drop(1).trimEnd()
                    number != null -> if (number == verse) block += lyric(line.drop(1))
                    // A hand-written file leaves out the space a lyric line starts with.
                    else -> block += lyric(if (line.startsWith(' ')) line.drop(1) else line)
                }
            }
            endBlock()
            return written.joinToString("\n\n")
        }

        /** A lyric line, its `||` breaks made lines of their own and its `|` column breaks spaces. */
        private fun lyric(line: String) = line.split("||").map { it.replace('|', ' ').trimEnd() }

        private fun label(number: Int?): Label? {
            val tag = tag ?: return null
            val kind = tag.first().uppercaseChar()
            val ownNumber = tag.drop(1).trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            val suffix = (ownNumber ?: number?.toString())?.let { " $it" }.orEmpty()
            val (name, environment) = KINDS[kind]?.takeIf { ownNumber != null || tag.length == 1 } ?: return Label(ForeignSongHeader.headerValue(tag) ?: tag, null)
            return Label(name + suffix, environment)
        }
    }

    private class Label(val name: String, val environment: String?)

    /** What OpenSong's one-letter section tags stand for, and the ChordPro environment each opens, if any. */
    private val KINDS = mapOf(
        'V' to ("Verse" to "verse"),
        'C' to ("Chorus" to "chorus"),
        'B' to ("Bridge" to "bridge"),
        'P' to ("Pre-chorus" to null),
        'T' to ("Tag" to null),
        'I' to ("Intro" to null),
        'O' to ("Outro" to null),
    )

    private val SECTION_TAG = Regex("""\[([^\[\]]+)]""")
}
