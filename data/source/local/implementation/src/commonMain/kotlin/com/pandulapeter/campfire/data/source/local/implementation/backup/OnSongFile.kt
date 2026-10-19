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

import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.ChordSheetLyrics
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.song.Song

/**
 * Reads one of OnSong's `.onsong` files: a header up to the first blank line, of `Label: value` lines or of a bare title
 * line and a bare artist line with labelled ones after them, then a body of sections (`Verse 1:`), chords in brackets
 * or on rows over the lyrics, and the ChordPro directives OnSong accepts as they are.
 */
internal object OnSongFile {

    /** The song as ChordPro; [fallbackTitle], the file's name, titles one whose header names no title. */
    fun read(text: String, fallbackTitle: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        val header = mutableMapOf<String, String>()
        val bare = mutableListOf<String>()
        var bodyStart = lines.size
        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) {
                bodyStart = index + 1
                break
            }
            val label = LABELLED_LINE.matchEntire(line.trim())?.takeIf { it.groupValues[1].lowercase() in LABELS }
            if (label != null) {
                header.getOrPut(label.groupValues[1].lowercase()) { label.groupValues[2].trim() }
                continue
            }
            // A file with no header starts with its first chord row, unless that row is the one word the song is
            // called by: a song called "Am" is exported as Am.onsong. Without a blank line after it, the header ends
            // where a third bare line would be, or a bare line after a labelled one, since the bare ones come first.
            val isChordRow = index == 0 && ChordSheetConverter.isChordLine(line) && !line.trim().equals(fallbackTitle, ignoreCase = true)
            if (isChordRow || bare.size == 2 || header.isNotEmpty()) {
                bodyStart = index
                break
            }
            bare += line.trim()
        }
        val body = body(lines.drop(bodyStart))
        return ForeignSongHeader.songText(body) {
            directive("title", header["title"] ?: bare.getOrNull(0) ?: fallbackTitle, "t")
            directive("artist", header["artist"] ?: bare.getOrNull(1))
            directive("key", header["key"])
            directive("capo", header["capo"]?.toIntOrNull()?.takeIf { it > 0 && it in Song.CAPO_RANGE }?.toString())
            directive("tempo", header["tempo"]?.toIntOrNull()?.takeIf { it in MetronomeSettings.TEMPO_RANGE }?.toString())
            directive("time", header["time"]?.takeIf { ChordProTime.parse(it) != null })
            directive("duration", header["duration"]?.let(ChordProDuration::parse)?.let(ChordProDuration::format))
            directive("copyright", header["copyright"])
            tags(listOfNotNull(header["keywords"], header["topic"]).flatMap { it.split(',', ';') })
        }
    }

    /**
     * The body cut at its directive lines, which are written as they are, and every run between them set on its own:
     * the fragment converter takes no text holding a directive, so the whole body cannot go to it in one call. A run
     * with chords in brackets is ChordPro already, and one without a chord row to set is lyrics, escaped.
     */
    private fun body(lines: List<String>): String {
        val written = mutableListOf<String>()
        val run = mutableListOf<String>()
        fun endRun() {
            val text = run.joinToString("\n").trim('\n')
            run.clear()
            if (text.isBlank()) return
            written += when {
                INLINE_CHORD.containsMatchIn(text) -> text
                else -> ChordSheetConverter.convertFragment(text, String::normalizedToNfc)?.trimEnd() ?: ChordSheetLyrics.escaped(text)
            }
        }
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                endRun()
                written += trimmed
            } else {
                run += line.trimEnd()
            }
        }
        endRun()
        return written.joinToString("\n")
    }

    /** The labels OnSong writes into a header, in lower case. */
    private val LABELS = setOf("title", "artist", "key", "capo", "tempo", "time", "duration", "copyright", "ccli", "flow", "keywords", "topic")

    private val LABELLED_LINE = Regex("""([A-Za-z]+):\s*(.*)""")

    private val INLINE_CHORD = Regex("""\[[A-G][^\]\s]*]""")
}
