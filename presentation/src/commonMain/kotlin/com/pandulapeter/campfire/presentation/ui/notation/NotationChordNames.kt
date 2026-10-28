/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine

/**
 * How the chords over a song's lyrics are named on the page, which an engraved tune's chord symbols follow so that the
 * staff and the lyrics never disagree: moved by [semitones] (the file's `{transpose}` and the reader's transposition,
 * the capo counted in), spelled with flats or sharps as [preferFlats] says (null: as the key asks), and shown in
 * [notation].
 */
internal data class NotationChordSpelling(val semitones: Int, val preferFlats: Boolean?, val notation: ChordNotation) {

    /** This spelling moved [offset] semitones further, for a tune under a key change ([abcTunesOf]). */
    fun movedBy(offset: Int) = if (offset == 0) this else copy(semitones = semitones + offset)

    companion object {
        val AS_WRITTEN = NotationChordSpelling(semitones = 0, preferFlats = null, notation = ChordNotation.STANDARD)
    }
}

/**
 * The chord symbols of an ABC tune (`"Am7"`), in the order they are written, annotations (`"^text"`) left out, and only
 * from the lines of music: not from a field (`K:`, `w:`), a comment, or a format line.
 */
internal fun abcChordSymbols(tune: String): List<String> = tune.lineSequence()
    .filterNot { line -> ABC_FIELD.matches(line) || line.trimStart().startsWith('%') }
    .flatMap { line -> CHORD_SYMBOL.findAll(line.withoutComment()).map { it.groupValues[1] } }
    .filter { symbol -> symbol.firstOrNull()?.let { it in ANNOTATION_PLACEMENTS } != true }
    .toList()

/**
 * [symbols] as the page names them in [spelling], in the same order, through the same transposition and notation the
 * lyrics' chords go through; a numbering counts them from the tune's first `K:`. Where the song model does not give one
 * name back for each symbol, the symbols come back as they are.
 */
internal fun abcChordNames(tune: String, symbols: List<String>, spelling: NotationChordSpelling): List<String> {
    if (symbols.isEmpty()) return symbols
    val key = abcKeyOf(tune)?.let { "{key: $it}\n" }.orEmpty()
    val song = ChordProParser.parse(key + symbols.joinToString(separator = "\n") { "[$it]" }, ChordNotation.STANDARD)
    val shown = ChordProNotation.withAccidentalSigns(
        ChordProNotation.toNotation(ChordProTransposer.transpose(song, spelling.semitones, spelling.preferFlats), spelling.notation),
    )
    val names = shown.blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>().flatMap { line -> line.chords.map { it.name } } }
    return if (names.size == symbols.size) names else symbols
}

/**
 * [this] with its chord symbols named [names] in place of the names the renderer wrote, which it transposed by
 * [semitones] from [symbols] (`names[i]` being `symbols[i]`'s). Each drawn chord takes the name of the symbols it is a
 * transposition of (the same chord, its root moved by [semitones]), whatever order the renderer drew them in, voice by
 * voice or system by system; one that matches none, or symbols named differently (`C#` and `Db` both drawn as `D`
 * under a numbering), keeps the renderer's name, which is still the right chord. A chord drawn as several runs (a jazz
 * chord's superscript) is read whole, and renamed, it is one run in its first one's font. The drawing keeps [names], in
 * the order written, as what a screen reader is told the chords are ([notationReadingOf]).
 */
internal fun NotationDrawing.withChordNames(symbols: List<String>, names: List<String>, semitones: Int): NotationDrawing {
    val namesByDrawn = HashMap<String, String?>()
    fun nameOf(drawn: String) = namesByDrawn.getOrPut(drawn) {
        symbols.indices.filter { isTransposedChord(symbols[it], drawn, semitones) }.map { names[it] }.distinct().singleOrNull()
    }
    return NotationDrawing(
        systems.map { system ->
            NotationSystem(
                width = system.width,
                height = system.height,
                shapes = system.shapes,
                glyphs = system.glyphs,
                texts = system.texts.map { text ->
                    val chordRuns = text.runs.filterIsInstance<NotationTextRun.Words>().filter { it.role == NotationTextRole.CHORD }
                    val drawn = chordRuns.joinToString(separator = "") { it.text }
                    val name = chordRuns.firstOrNull()?.let { nameOf(drawn) }
                    if (name == null || name == drawn) {
                        text
                    } else {
                        NotationText(x = text.x, y = text.y, anchor = text.anchor, runs = listOf(chordRuns.first().copy(text = name)), isBoxed = text.isBoxed)
                    }
                },
            )
        },
        chordNames = names,
    )
}

/**
 * Whether [drawn] is [written] as abc2svg transposes a chord symbol by [semitones]: the first letter from A to G of each
 * part around a slash, with its sharp or flat, moved by them, and the rest of the part kept.
 */
internal fun isTransposedChord(written: String, drawn: String, semitones: Int): Boolean {
    val writtenParts = written.split('/')
    val drawnParts = drawn.split('/')
    return writtenParts.size == drawnParts.size && writtenParts.indices.all { index ->
        val before = ChordRoot.of(writtenParts[index])
        val after = ChordRoot.of(drawnParts[index])
        when {
            before == null || after == null -> before == after && writtenParts[index].normalized() == drawnParts[index].normalized()
            else -> before.prefix == after.prefix && before.suffix == after.suffix && (after.pitchClass - before.pitchClass - semitones).mod(12) == 0
        }
    }
}

/** The root of one part of a chord symbol, as abc2svg finds it: the first letter from A to G, whatever stands before it. */
private class ChordRoot(val prefix: String, val pitchClass: Int, val suffix: String) {
    companion object {
        fun of(part: String): ChordRoot? {
            val letter = part.indexOfFirst { it in 'A'..'G' }.takeIf { it >= 0 } ?: return null
            var end = letter + 1
            var pitchClass = PITCH_CLASSES.getValue(part[letter])
            while (end < part.length && part[end] in "#♯b♭" && end - letter <= 2) {
                pitchClass += if (part[end] == '#' || part[end] == '♯') 1 else -1
                end++
            }
            return ChordRoot(prefix = part.substring(0, letter), pitchClass = pitchClass, suffix = part.substring(end).normalized())
        }

        private val PITCH_CLASSES = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    }
}

/** The tune's first key (`K:G`, `K:Em`, `K:A dorian`) as a ChordPro key, a mode with a minor third read as minor. */
internal fun abcKeyOf(tune: String): String? {
    val value = tune.lineSequence().firstOrNull { it.startsWith("K:") }?.substringAfter(':')?.withoutComment() ?: return null
    val match = ABC_KEY.find(value.trim()) ?: return null
    val mode = match.groupValues[3].lowercase()
    val isMinor = mode.startsWith("m") && !mode.startsWith("maj") && !mode.startsWith("mix") || MINOR_MODES.any { mode.startsWith(it) }
    return match.groupValues[1] + match.groupValues[2] + if (isMinor) "m" else ""
}

private fun String.normalized() = replace('♯', '#').replace('♭', 'b')

/** The line up to its comment, a `%` outside any quotes. */
private fun String.withoutComment(): String {
    var isQuoted = false
    forEachIndexed { index, character ->
        when {
            character == '"' -> isQuoted = !isQuoted
            character == '%' && !isQuoted -> return substring(0, index)
        }
    }
    return this
}

private val ABC_FIELD = Regex("""^\s*[A-Za-z+]:.*""")
private val CHORD_SYMBOL = Regex(""""([^"]*)"""")
private val ABC_KEY = Regex("""^([A-G])([#b]?)\s*([A-Za-z]*)""")
private const val ANNOTATION_PLACEMENTS = "^_<>@"
private val MINOR_MODES = listOf("aeo", "dor", "phr", "loc")
