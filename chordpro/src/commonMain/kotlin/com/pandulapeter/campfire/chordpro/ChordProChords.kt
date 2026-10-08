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

import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * What a chord name means: the notes it is made of.
 *
 * A chord name is whatever [ChordProChordNames] recognizes as one, and this reads the same walk of it, so a name is
 * read exactly where it is recognized. The reading is the one chord charts agree on, with the choices a chart leaves
 * open made once here:
 *
 * - `11` leaves out the major third it would clash with (`C11` is `C9sus4`, the way it is fingered), a minor one
 *   stays (`Cm11`), and `13` is the seventh with the thirteenth, leaving out the ninth and the eleventh, which no
 *   chart's `13` shape holds.
 * - `dim` and `°` are the triad and `dim7` and `°7` the diminished seventh; `ø` is the half diminished seventh.
 * - `2` alone adds the second (`C2` is `Cadd2`), `4` alone is `sus4`, `5` alone is the power chord.
 * - A `5` after a quality names the fifth the quality has (`C+5` is `Caug`, `Cdim5` is `Cdim`), and `C-5` is the flat
 *   five `C7-5` writes.
 * - `alt` is the seventh with a flat ninth's neighbor, the sharp ninth, and the flat thirteenth, and no fifth: the
 *   altered notes a hand can hold at once.
 * - A bare `+` after the number raises the fifth, as `C7+` is written for an augmented seventh.
 */
object ChordProChords {

    /**
     * The chord [name] stands for, as shown in [notation], or null where it is no chord name: a word in brackets,
     * `N.C.`, an annotation. A parenthesized chord, a lowercase minor (`a`, `f#7`), a Latin name and the `♯` and `♭`
     * signs are read as well, since this is handed what a page draws. A step of a key is not read, since it says nothing
     * without the key: in a numbering a chord is named in letters, see [ChordProNotation.shownName].
     */
    fun parse(name: String, notation: ChordNotation = ChordNotation.STANDARD): Chord? {
        val reader = Reader()
        return if (ChordProChordNames.read(ChordProNotation.read(name, isGerman = notation == ChordNotation.GERMAN), reader)) reader.chord() else null
    }

    /**
     * The notes of [chord] as names in [notation], in letters for a numbering, spelled with flats or sharps as
     * [preferFlats] says: the bass of a slash chord first where it is not one of the chord's own notes, then the
     * chord's from its root up.
     */
    fun noteNames(chord: Chord, notation: ChordNotation = ChordNotation.STANDARD, preferFlats: Boolean = false): List<String> {
        val pitchClasses = chord.intervals.map { (chord.root + it) % 12 }
        val bass = chord.bass?.takeIf { it !in pitchClasses }
        return (listOfNotNull(bass) + pitchClasses).map { pitchClass ->
            ChordProNotation.shownName((if (preferFlats) flatNames else sharpNames)[pitchClass], notation)
        }
    }

    /**
     * The notes of the chord [name] stands for, as [noteNames] lists them, each spelled from the letter of the root as
     * [name] writes it by the degree it is: `Cm` is `C Eb G` and `F7` is `F A C Eb`, as a chart spells them, where
     * [noteNames] can only choose sharps or flats for the whole chord. A note that would need a double sharp or flat,
     * or would be one of `Cb`, `Fb`, `E#`, `B#`, is named as [noteNames] would with the root's accidental (`Gbm` is
     * `Gb A Db`, `C#` is `C# F G#`), since a player reads keys and strings rather than theory, and the bass of a slash
     * chord as the name writes it. [name] is in the standard notation, its notes named in [notation]. Null where [name]
     * is no chord.
     */
    fun spelledNoteNames(name: String, notation: ChordNotation = ChordNotation.STANDARD): List<String>? {
        val reader = Reader()
        // The standard notation always, since in German the standard `B` this is handed would be read as B flat.
        if (!ChordProChordNames.read(ChordProNotation.read(name, isGerman = false), reader)) return null
        val chord = reader.chord()
        val rootName = reader.rootName?.let(::standardSpelling)
        val rootLetter = rootName?.let { LETTERS.indexOf(it.first()) } ?: -1
        if (rootName == null || rootLetter < 0) return noteNames(chord, notation)
        val fallback = if (rootName.getOrNull(1) == 'b') flatNames else sharpNames
        val pitchClasses = chord.intervals.map { (chord.root + it) % 12 }
        val bass = chord.bass?.takeIf { it !in pitchClasses }?.let { reader.bassName?.let(::standardSpelling) ?: fallback[it] }
        val notes = chord.intervals.map { interval ->
            val letter = LETTERS[(rootLetter + letterStep(interval, chord.intervals)) % LETTERS.length]
            val pitchClass = (chord.root + interval) % 12
            val spelled = when ((pitchClass - noteIndices.getValue(letter)).mod(12)) {
                0 -> letter.toString()
                1 -> "$letter#"
                11 -> "${letter}b"
                else -> null
            }
            spelled?.takeIf { it !in whiteKeyEnharmonics } ?: fallback[pitchClass]
        }
        return (listOfNotNull(bass) + notes).map { ChordProNotation.shownName(it, notation) }
    }

    /**
     * How many letters above the root the note [interval] semitones above it is written: a chart's degrees, with the
     * neighbors of a note the chord also holds telling a sharp ninth from a minor third, a sharp eleventh from a flat
     * fifth and a flat thirteenth from a sharp fifth, and a diminished seventh's `bb7` written as the sixth, as charts do.
     */
    private fun letterStep(interval: Int, intervals: List<Int>) = when (interval) {
        0 -> 0
        1, 2 -> 1
        3 -> if (4 in intervals) 1 else 2
        4 -> 2
        5 -> 3
        6 -> if (7 in intervals) 3 else 4
        7 -> 4
        8 -> if (7 in intervals) 5 else 4
        9 -> 5
        else -> 6
    }

    /** A note as written in the standard notation, with a capital letter and `#` or `b` for its accidental. */
    private fun standardSpelling(note: String) = note.first().uppercaseChar() + when (note.getOrNull(1)) {
        '#', '♯' -> "#"
        'b', '♭' -> "b"
        else -> ""
    }

    /**
     * The chords [song] plays, each once by the name it is written under and in the order they first appear: the
     * chords over its lyrics, the cells of its grids, the rows of chord names above its tabs and the brackets of its
     * comments and labels, where an intro is written down as a row of chords — every name the transposition moves, its
     * key aside, since a key is no chord anybody plays. A recalled chorus is read where it stands, which only matters
     * for a chorus whose chords appear nowhere else. Whether a name is a chord is [parse]'s to say.
     */
    internal fun namesIn(song: ChordProSong): List<String> {
        val names = LinkedHashSet<String>()
        forEachName(song) { name, _ -> names += name }
        return names.toList()
    }

    /**
     * Hands every name [namesIn] reads to [action], as often as it is written, with the offset the `{transpose}` in
     * force where it stands moved it by: 0 before the first, and a recall read where it stands.
     */
    internal fun forEachName(song: ChordProSong, action: (name: String, offset: Int) -> Unit) {
        var offset = 0
        fun addBrackets(text: String?) {
            text?.let { ChordProDirectives.brackets(it).map { bracket -> bracket.content.trim() }.filter { name -> name.isNotEmpty() && !name.startsWith("*") }.forEach { action(it, offset) } }
        }
        fun addBlocks(blocks: List<ChordProBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is ChordProBlock.Section -> {
                        addBrackets(block.label)
                        block.lines.forEach { line ->
                            when (line) {
                                is ChordProLine.Lyrics -> line.chords.filter { !it.isAnnotation }.forEach { action(it.name, offset) }
                                is ChordProLine.Grid -> {
                                    addBrackets(line.label)
                                    line.tokens.filterIsInstance<GridToken.Chord>().forEach { token -> ChordProTokens.cellChords(token.name).forEach { action(it, offset) } }
                                }
                                is ChordProLine.Tab -> {
                                    addBrackets(line.label)
                                    ChordProTabTransposer.chordNames(listOf(line.text)).forEach { action(it, offset) }
                                }
                                ChordProLine.Blank -> Unit
                            }
                        }
                    }
                    is ChordProBlock.ChorusRecall -> {
                        addBrackets(block.label)
                        addBlocks(block.blocks)
                    }
                    is ChordProBlock.Comment -> addBrackets(block.text)
                    is ChordProBlock.Transpose -> offset = block.semitones
                    else -> Unit
                }
            }
        }
        addBlocks(song.blocks)
    }

    /**
     * [name], a chord as shown in [notation], moved by [semitones] and spelled with flats or sharps as [preferFlats]
     * says, in the same notation, or in letters for a numbering; a word that is no chord is returned as it is.
     */
    fun transposedName(name: String, semitones: Int, notation: ChordNotation = ChordNotation.STANDARD, preferFlats: Boolean = false): String {
        val move = { chord: String ->
            ChordProNotation.shownName(ChordProTransposer.transposeChord(ChordProNotation.read(chord, isGerman = notation == ChordNotation.GERMAN), semitones, preferFlats), notation)
        }
        return if (notation == ChordNotation.LATIN) move(name) else ChordProChordRewriter.keepingLowercaseMinors(move)(name)
    }

    /** The pitch class of a note as written: a capital or lowercase letter, `H` as `B`, and an accidental or none. */
    internal fun pitchClass(note: String): Int? {
        val letter = noteIndices[note.firstOrNull()?.uppercaseChar()] ?: return null
        val accidental = when (note.getOrNull(1)) {
            '#', '♯' -> 1
            'b', '♭' -> -1
            else -> 0
        }
        return (letter + accidental).mod(12)
    }

    /** Collects the notes while [ChordProChordNames.read] walks a name already in the standard notation. */
    private class Reader : ChordNameReader {
        private var root = 0
        private var bass: Int? = null
        private var third: Int? = MAJOR_THIRD
        private var suspension: Int? = null
        private var fifth: Int? = PERFECT_FIFTH
        private var seventh: Int? = null
        private var isMajorSeventh = false
        private var isDiminished = false
        private var hasRoot = true
        private val added = mutableSetOf<Int>()
        private var quality: String? = null

        /** The root as the name writes it, which [spelledNoteNames] spells the other notes from. */
        var rootName: String? = null
            private set

        /** The bass of a slash chord as the name writes it. */
        var bassName: String? = null
            private set

        override fun root(note: String) {
            rootName = note
            root = pitchClass(note) ?: 0
        }

        override fun quality(quality: String) {
            this.quality = quality
            when (quality) {
                "m", "min", "mi", "-" -> third = MINOR_THIRD
                "maj", "Maj", "M" -> isMajorSeventh = true
                "Δ", "∆" -> {
                    isMajorSeventh = true
                    seventh = MAJOR_SEVENTH
                }
                "dim", "°" -> {
                    third = MINOR_THIRD
                    fifth = DIMINISHED_FIFTH
                    isDiminished = true
                }
                "ø" -> {
                    third = MINOR_THIRD
                    fifth = DIMINISHED_FIFTH
                    seventh = MINOR_SEVENTH
                }
                "aug", "+" -> fifth = AUGMENTED_FIFTH
                "sus" -> suspension = PERFECT_FOURTH
            }
        }

        override fun number(number: String) {
            when (quality) {
                "sus" -> if (number == "2") suspension = MAJOR_SECOND else if (number != "4") extend(number)
                "add" -> add(number)
                "ø" -> if (number != "7") extend(number)
                else -> when (number) {
                    "2" -> added += MAJOR_SECOND
                    "4" -> suspension = PERFECT_FOURTH
                    "5" -> when (quality) {
                        null -> third = null
                        // `-` is the flat sign wherever a `5` follows it (`C7-5`), so `C-5` is the major triad with a flat
                        // fifth rather than a minor one with its fifth named twice.
                        "-" -> {
                            third = MAJOR_THIRD
                            fifth = DIMINISHED_FIFTH
                        }
                        else -> Unit
                    }
                    else -> extend(number)
                }
            }
        }

        override fun altered() {
            if (seventh == null) seventh = MINOR_SEVENTH
            fifth = null
            added += setOf(MINOR_THIRD, MINOR_SIXTH)
        }

        override fun added(degree: String) {
            when (degree) {
                "7" -> seventh = seventh ?: seventhOfQuality()
                else -> add(degree)
            }
        }

        override fun alteration(word: String, degree: String) {
            when (word) {
                "maj", "Maj", "M" -> {
                    isMajorSeventh = true
                    if (degree.isEmpty()) seventh = MAJOR_SEVENTH else extend(degree)
                }
                "min", "mi" -> third = MINOR_THIRD
                "dim" -> {
                    fifth = DIMINISHED_FIFTH
                    if (degree == "7") seventh = DIMINISHED_SEVENTH
                }
                "aug" -> fifth = AUGMENTED_FIFTH
                "sus" -> suspension = if (degree == "2") MAJOR_SECOND else PERFECT_FOURTH
                "add" -> add(degree)
                "#", "♯", "+" -> when (degree) {
                    "", "5" -> fifth = AUGMENTED_FIFTH
                    else -> degreeInterval(degree)?.let { added += (it + 1) % 12 }
                }
                "b", "♭", "-" -> when (degree) {
                    "5" -> fifth = DIMINISHED_FIFTH
                    "" -> Unit
                    else -> degreeInterval(degree)?.let { added += (it + 11) % 12 }
                }
            }
        }

        override fun omitted(degree: String) {
            when (degree) {
                "1", "8" -> hasRoot = false
                "3" -> {
                    third = null
                    suspension = null
                }
                "5" -> fifth = null
                else -> degreeInterval(degree)?.let { added -= it }
            }
        }

        override fun bass(note: String) {
            bassName = note
            bass = pitchClass(note)
        }

        fun chord(): Chord {
            val intervals = buildSet {
                if (hasRoot) add(0)
                (suspension ?: third)?.let(::add)
                fifth?.let(::add)
                seventh?.let(::add)
                addAll(added)
            }
            return Chord(root = root, intervals = intervals.sorted(), bass = bass?.takeIf { it != root })
        }

        /** A number that stacks up to itself: `9` and `11` bring the seventh and the ninth under them, `13` the seventh. */
        private fun extend(number: String) {
            when (number) {
                "6" -> added += MAJOR_SIXTH
                "69" -> added += setOf(MAJOR_SIXTH, MAJOR_SECOND)
                "7" -> seventh = seventhOfQuality()
                "9" -> {
                    seventh = seventhOfQuality()
                    added += MAJOR_SECOND
                }
                "11" -> {
                    seventh = seventhOfQuality()
                    added += setOf(MAJOR_SECOND, PERFECT_FOURTH)
                    if (third == MAJOR_THIRD) third = null
                }
                "13" -> {
                    seventh = seventhOfQuality()
                    added += MAJOR_SIXTH
                }
                else -> add(number)
            }
        }

        private fun add(degree: String) {
            degreeInterval(degree)?.let { added += it }
        }

        private fun seventhOfQuality() = when {
            isMajorSeventh -> MAJOR_SEVENTH
            isDiminished -> DIMINISHED_SEVENTH
            else -> seventh ?: MINOR_SEVENTH
        }
    }

    /** The interval a scale degree names above the root, folded into the octave, or null for no degree a chord has. */
    private fun degreeInterval(degree: String) = degree.toIntOrNull()?.let { degreeIntervals[it] }

    private const val MAJOR_SECOND = 2
    private const val MINOR_THIRD = 3
    private const val MAJOR_THIRD = 4
    private const val PERFECT_FOURTH = 5
    private const val DIMINISHED_FIFTH = 6
    private const val PERFECT_FIFTH = 7
    private const val AUGMENTED_FIFTH = 8
    private const val MINOR_SIXTH = 8
    private const val MAJOR_SIXTH = 9
    private const val DIMINISHED_SEVENTH = 9
    private const val MINOR_SEVENTH = 10
    private const val MAJOR_SEVENTH = 11
    private val degreeIntervals = mapOf(1 to 0, 2 to 2, 3 to 4, 4 to 5, 5 to 7, 6 to 9, 7 to 10, 8 to 0, 9 to 2, 10 to 4, 11 to 5, 12 to 7, 13 to 9)
    private val noteIndices = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11, 'H' to 11)
    private const val LETTERS = "CDEFGAB"
    private val whiteKeyEnharmonics = setOf("Cb", "Fb", "E#", "B#")
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
}
