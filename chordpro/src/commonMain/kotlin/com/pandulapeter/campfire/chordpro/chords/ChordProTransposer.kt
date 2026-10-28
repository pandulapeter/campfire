/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.chords

import com.pandulapeter.campfire.chordpro.abc.AbcTranspositions
import com.pandulapeter.campfire.chordpro.voicings.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabRetuner
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabTransposer
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.ChordRewrite
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.anyWrittenChordName
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.keepingLowercaseMinors
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.keyWordOf
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.keyWords
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.rewriteChordNamesInText
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.rewriteChords
import com.pandulapeter.campfire.chordpro.syntax.ChordProChordRewriter.writtenChordNames
import com.pandulapeter.campfire.chordpro.syntax.ChordProOffsetMapping
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.NOTE_COUNT

/**
 * Moves chords up or down by a number of semitones, either on the model or directly on the raw text.
 */
public object ChordProTransposer {

    /**
     * Transposes a chord name, keeping an optional chord's parentheses and its bass note.
     *
     * A word that is not a chord name is returned as it was. Brackets are how a chart marks its parts as often as
     * they hold chords — `[Intro]`, `[Break]`, `[Chorus 2x]` — and every one of those that starts with a note letter
     * would otherwise be moved, with the `b` after an `E` eaten as a flat sign. [ChordProNotation.toGerman] has
     * asked the same question of the same names since it was written.
     */
    internal fun transposeChord(name: String, semitones: Int, preferFlats: Boolean): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
    }

    /**
     * Transposes every chord in the model (lyrics chords, grid chords, tabs; never annotations) and the key.
     *
     * @param preferFlats Forces the spelling of the accidentals; null leaves it to [prefersFlats], which is what the
     *   song itself asks for. Forced, it is worth doing even for no semitones at all: respelling the chords of a song
     *   that is not transposed is exactly what a reader who always wants flats (or sharps) is asking for.
     * @param tabPlayer Who the tabs are read by, which moves every run of tablature to their strings as well as by the
     *   transposition, in one move, so that it is put on the neck by one decision rather than two (see
     *   [ChordProTabRetuner]). Null moves the frets by the transposition alone, as the text transposition does.
     * @param capo How many of [semitones] a tab's frets are not moved by: a capo's, counted in to name the chords that
     *   sound. A tab's frets are counted from the capo, as tablature is, so they move by the rest alone, which is negative
     *   where the capo played is higher than the one the chords are named for, while every chord name, a tab's included,
     *   moves by all of it.
     */
    public fun transpose(song: ChordProSong, semitones: Int, preferFlats: Boolean? = null, tabPlayer: TabPlayer? = null, capo: Int = 0): ChordProSong {
        val isModulated = song.blocks.any { it is ChordProBlock.Transpose }
        if (semitones == 0 && capo == 0 && preferFlats == null && !isModulated && tabPlayer == null) return song
        val rewrites = mutableMapOf<Int, ChordRewrite>()
        return rewriteChords(song) { offset ->
            rewrites.getOrPut(semitones + offset) { rewriteBy(song, semitones + offset, preferFlats, tabPlayer, capo) }
        }
    }

    /**
     * Moving by [shift] semitones, spelled for the key the song is in once it has moved that far. No move and no forced
     * spelling leaves the chords exactly as written, as it does for a song with no modulation at all, and the tabs too
     * where nobody is named to read them and the [capo] does not move their frets either.
     */
    private fun rewriteBy(song: ChordProSong, shift: Int, preferFlats: Boolean?, tabPlayer: TabPlayer?, capo: Int): ChordRewrite {
        val isMoved = shift != 0 || preferFlats != null
        val flats = if (isMoved) preferFlats ?: prefersFlats(song, shift) else false
        val rename = if (isMoved) { name: String -> transposeChord(name, shift, flats) } else { name: String -> name }
        val fretShift = shift - capo
        return ChordRewrite(
            rewriteTabLines = if (isMoved || fretShift != 0) { lines -> ChordProTabTransposer.transpose(lines, fretShift, rename) } else { lines -> lines },
            rename = rename,
            rewriteDefinition = if (isMoved) { definition -> ChordProDefinitions.transposed(definition, shift, rename) } else { definition -> definition },
            rewriteTabRun = tabPlayer?.let { player -> { lines, selector -> ChordProTabRetuner.retune(lines, fretShift, rename, selector, player) } },
        )
    }

    /**
     * Transposes raw ChordPro text in place, keeping its formatting and original notation. The tunes of an `abc` block
     * keep their notes and chord symbols as written and are moved by a `%%transpose` line of their own, which abc2svg
     * adds to the reader's transposition ([AbcTranspositions]).
     */
    public fun transposeText(text: String, semitones: Int, preferFlats: Boolean? = null): String {
        if (semitones == 0 && preferFlats == null) return text
        val written = ChordProNotation.withLowercaseMinorsExpanded(ChordProParser.parseAsWritten(text))
        val isGermanNotated = ChordProNotation.isGermanNotated(written)
        val song = if (isGermanNotated) ChordProNotation.fromGerman(written) else written
        val flats = preferFlats ?: prefersFlats(song, semitones)
        val transposeName = { name: String -> transposeChord(name, semitones, flats) }
        if (!isGermanNotated) return rewriteText(text, semitones, keepingLowercaseMinors(transposeName))
        val staysGermanNotated = anyWrittenChordName(song) { name ->
            ChordProNotation.isGermanName(ChordProNotation.toGerman(transposeName(name)))
        }
        return rewriteText(
            text,
            semitones,
            keepingLowercaseMinors { name ->
                val transposedName = transposeName(ChordProNotation.fromGerman(name))
                if (staysGermanNotated) ChordProNotation.toGerman(transposedName) else transposedName
            },
        )
    }

    /**
     * Whether a `{key}` value names a key, which the editor marks where it does not: a note with anything after it
     * (`D dorian`, `Dm (capo 2)`, `a-moll`), but not `S`. It may still be one [transposeText] cannot move, see
     * [isMovableKey].
     */
    public fun isKey(key: String): Boolean = ChordProChordRewriter.isKey(key)

    /**
     * Whether [transposeText] moves a song's `{key}` of [key], read the way it reads one, so that a stepper offering to
     * move a song with no chords offers it only where the key would change: `D dorian` and `a-moll` stay as written.
     */
    public fun isMovableKey(key: String): Boolean = "{key: $key}".let { line -> transposeText(line, 1) != line }

    /**
     * Where [offset] of [before] is in [after], for an [after] that [transposeText] made of [before]: an editor that
     * transposes the document under the caret keeps the caret next to the text it was next to, rather than at the
     * same character count, which every chord name that grew or shrank above it would have moved.
     *
     * A transposition changes nothing but chord names (and the frets of a tab) and the transposition line of an ABC tune,
     * which it may write or remove, so the offset keeps its line (paired over those), and inside the line it keeps its
     * place between the brackets; a line with no brackets to go by (a `{key}`, a grid or a tab line) keeps the text in
     * front of its first change and behind its last one. A caret on a transposition line that was removed goes to the
     * start of the line after it.
     */
    public fun transposedOffset(before: String, after: String, offset: Int): Int = ChordProOffsetMapping.transposedOffset(before, after, offset)

    /** [text] with its chords renamed by [rename] and its ABC tunes moved by [semitones], see [AbcTranspositions.moved]. */
    private fun rewriteText(text: String, semitones: Int, rename: (String) -> String) = AbcTranspositions.moved(
        text = rewriteChordNamesInText(
            text = text,
            rewriteTab = { lines -> ChordProTabTransposer.transpose(lines, semitones, rename) },
            rename = rename,
            rewriteDefinition = { rawLine, selector -> ChordProDefinitions.rewrittenLine(rawLine, selector, rename, semitones) },
        ),
        semitones = semitones,
    )

    /** Whether flats should be preferred for the key this song arrives in after the transposition. */
    internal fun prefersFlats(song: ChordProSong, semitones: Int): Boolean {
        val names = writtenChordNames(song)
        val key = song.metadata.key?.let(::keyOf)
            ?: names.firstNotNullOfOrNull { name -> name.takeIf(ChordProChordNames::isChordName)?.let(::keyOf) }
        return key?.transposedBy(semitones)?.prefersFlats ?: isWrittenInFlats(names)
    }

    private fun keyOf(name: String): Key? {
        val root = ChordProChordNames.notes(name.trim()).first()
        val noteIndex = noteIndices[root.getOrNull(0)] ?: return null
        val accidental = accidentals[root.getOrNull(1)]
        val suffix = root.substring(if (accidental == null) 1 else 2).trimStart()
        val word = keyWordOf(suffix)
        val isMinor = if (word in keyWords) word in minorKeyWords else suffix.startsWith("m") && !suffix.startsWith("maj", ignoreCase = true)
        return Key((noteIndex + (accidental ?: 0)).mod(NOTE_COUNT), isMinor)
    }

    private class Key(val tonic: Int, val isMinor: Boolean) {
        val prefersFlats get() = if (isMinor) flatNames[tonic] + "m" in flatMinorKeys else flatNames[tonic] in flatMajorKeys
        fun transposedBy(semitones: Int) = Key((tonic + semitones).mod(NOTE_COUNT), isMinor)
    }

    private fun isWrittenInFlats(names: List<String>): Boolean {
        var flats = 0
        var sharps = 0
        names.filter(ChordProChordNames::isChordName).flatMap(ChordProChordNames::notes).forEach { note ->
            if (noteIndices.containsKey(note.getOrNull(0))) {
                when (accidentals[note.getOrNull(1)]) {
                    1 -> sharps++
                    -1 -> flats++
                }
            }
        }
        return flats > sharps
    }

    private fun transposeNote(part: String, semitones: Int, preferFlats: Boolean): String {
        val noteIndex = noteIndices[part.getOrNull(0)] ?: return part
        val accidental = accidentals[part.getOrNull(1)]
        val suffixStartIndex = if (accidental == null) 1 else 2
        val transposedNoteIndex = (noteIndex + (accidental ?: 0) + semitones).mod(NOTE_COUNT)
        return (if (preferFlats) flatNames else sharpNames)[transposedNoteIndex] + part.substring(suffixStartIndex)
    }

    private val minorKeyWords = setOf("minor", "min", "moll", "menor", "mineur", "minore")
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val flatMajorKeys = setOf("C", "F", "Bb", "Eb", "Ab", "Db")
    private val flatMinorKeys = setOf("Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm")
    private val noteIndices = mapOf(
        'C' to 0,
        'D' to 2,
        'E' to 4,
        'F' to 5,
        'G' to 7,
        'A' to 9,
        'B' to 11,
        'H' to 11, // Only a word that is not a chord name still gets here with an H: the parser has read the rest into B.
    )
    private val accidentals = mapOf(
        '#' to 1,
        'b' to -1,
        '♯' to 1, // ♯
        '♭' to -1, // ♭
    )
}
