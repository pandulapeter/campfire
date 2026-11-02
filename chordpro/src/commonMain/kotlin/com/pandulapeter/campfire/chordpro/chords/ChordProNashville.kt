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

import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ACCIDENTALS
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.NOTE_COUNT

/**
 * The chords of a song counted from its key, as [ChordNotation.NASHVILLE] and [ChordNotation.ROMAN] show them. The aim
 * is the chart a working musician writes by hand rather than the one an algorithm finds simplest:
 *
 * - The steps are counted from the key's own note, a minor key's included, so that the chord a song resolves to reads
 *   as home: in A minor `Am F C G E7` is `1- b6 b3 b7 5(7)`, the flats being what its players already call those chords.
 *   The steps between are always written `b2 b3 #4 b6 b7`, whatever the spelling of the key.
 * - In numbers a minor chord is marked with a `-`, the number alone is major, and every other quality is kept as the
 *   file writes it (`2-7`, `4maj7`, `5sus4`, `7dim`), an extension that starts with a digit in parentheses, since
 *   written straight after the step it would read as one number with it (`5(7)`, `b7(7sus4)`, `1(6/9)`, a group of the
 *   file's own left after them: `1(7)(b9)`). `M`, `maj` and `Maj` are major sevenths and stay.
 * - In numerals the quality is the case of the numeral: capitals for major, small letters for minor, a `°` after a
 *   small numeral for diminished, an `ø` for half-diminished and a `+` after a capital for augmented, everything else
 *   kept after it (`V7`, `ii7`, `vii°`, `viiø7`, `I+`, `IVmaj7`).
 * - A bass note is an Arabic step in both (`5/7`, `V/7`): in Roman analysis a numeral after a slash is an applied chord
 *   (`V/V`), which a reader who knows that would read `V/VII` as.
 */
internal object ChordProNashville {

    /**
     * The pitch class the steps of a song in [key] are counted from: the note the key starts with, whether the key is
     * major or minor, since the chord on that step carries its own `-` or small numeral. Null where the key starts with
     * no note. The key is in the standard notation, and may be spelled out in words (`A minor`, `a-moll`) or carry a
     * note after it (`Dm (capo 2)`).
     */
    fun tonicOf(key: String) = ChordProChords.pitchClass(key.trim())

    /**
     * [name], a chord in the standard notation, as the step of the key on [tonic] it stands on, in numbers or in
     * [isRoman] numerals. A word that is no chord name (`N.C.`, a `[Chorus 2x]`) is returned as it was.
     */
    fun number(name: String, tonic: Int, isRoman: Boolean): String {
        if (!ChordProChordNames.isChordName(name)) return name
        val isParenthesized = name.length > 2 && name.first() == '(' && name.last() == ')'
        val chord = if (isParenthesized) name.substring(1, name.length - 1) else name
        val notes = ChordProChordNames.notes(chord)
        val reader = QualityReader()
        ChordProChordNames.read(notes.first(), reader)
        val root = reader.root ?: return name
        val degree = ((ChordProChords.pitchClass(root) ?: return name) - tonic).mod(NOTE_COUNT)
        val suffix = notes.first().substring(root.length)
        val numbered = if (isRoman) numeral(romanDegrees[degree], reader.quality, suffix) else nashvilleDegrees[degree] + extended(nashvilleSuffix(reader.quality, suffix))
        val bass = notes.getOrNull(1)?.let { note -> ChordProChords.pitchClass(note)?.let { nashvilleDegrees[(it - tonic).mod(NOTE_COUNT)] } ?: note }
        val rewritten = if (bass == null) numbered else "$numbered/$bass"
        return if (isParenthesized) "($rewritten)" else rewritten
    }

    /**
     * [name], a chord in the standard notation, as the numeral on [step], a degree of a scale as its formula writes it
     * (`b5`, `#2`, `bb7`), its quality the case and sign of the numeral as [number] gives them: what a scale's own chords
     * are numbered by, so that the chord on a Locrian scale's fifth reads `bV` as the scale's `b5` does, where counting
     * its semitones from the root would make it a `#IV`. A word that is no chord name, or a step with no degree in it,
     * is returned as it was.
     */
    fun numeral(name: String, step: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        val reader = QualityReader()
        ChordProChordNames.read(ChordProChordNames.notes(name).first(), reader)
        val root = reader.root ?: return name
        val digit = step.trimStart { it in ACCIDENTALS }.toIntOrNull()?.takeIf { it in 1..romanSteps.size } ?: return name
        val signs = step.substring(0, step.length - digit.toString().length)
        return numeral(signs + romanSteps[digit - 1], reader.quality, name.substring(root.length))
    }

    /**
     * Whether [word] is a chord written as a step of a key, in numbers or in numerals: a step, then whatever the rest
     * of a chord name may be (checked by putting a `C` in its place), and an Arabic step after a `/`. A bare `I` is not
     * one: it is a word far more often than it is a chord, and a row of chords that holds one holds another.
     */
    fun isDegree(word: String): Boolean {
        if (word == "I") return false
        val name = word.removeSurrounding("(", ")")
        val slash = name.lastIndexOf('/')
        val chord = if (slash >= 0 && arabicStepEnd(name, slash + 1) == name.length) name.substring(0, slash) else name
        val stepEnd = arabicStepEnd(chord, 0).takeIf { it > 0 } ?: romanStepEnd(chord) ?: return false
        val rest = chord.substring(stepEnd)
        return ChordProChordNames.isChordName("C" + rest) || ChordProChordNames.isChordName("C" + withoutExtensionParentheses(rest))
    }

    /**
     * The chord the numeral [word] names on the key whose note is [tonic], the inverse of [number] in numerals, read in
     * every way a sheet writes one. A step is counted on the major scale from [tonic], as [number] counts it, unless
     * [isOnMinorScale]: then `III`, `VI` and `VII` are the minor scale's, as classical analysis writes a minor key. Its
     * root is spelled with sharps; the caller respells it for the key.
     *
     * - Where [isCaseQuality], a small numeral is minor and a capital major (`vi`, `IV`); otherwise the case says nothing
     *   and only a quality written after the numeral does (`VIm`, `vim`). A minor written after a small numeral is minor
     *   once, never twice, and a `-` right after the numeral is a minor (`II-7`).
     * - A `°` or an `o` after the numeral is diminished, an `ø` or a `⌀` half-diminished, a `+` augmented; a quality,
     *   an extension, or an alteration after it is kept (`IVmaj7`, `VIIdim`).
     * - After a `/`, an Arabic step (`V/7`) or a numeral (`I/iii`) is the bass, the root of that step whatever its case;
     *   a numeral after a dominant (`V7/V`, `V/vi`, `vii°/V`) is the step it is applied to, the chord counted on the major
     *   scale from that step's root.
     *
     * Null where [word] is no numeral, or names no chord.
     */
    fun chordOfNumeral(word: String, tonic: Int, isOnMinorScale: Boolean, isCaseQuality: Boolean): String? {
        val isParenthesized = word.length > 2 && word.first() == '(' && word.last() == ')'
        val name = if (isParenthesized) word.substring(1, word.length - 1) else word
        val slash = name.lastIndexOf('/')
        val after = if (slash >= 0) name.substring(slash + 1) else null
        val isArabicBass = slash >= 0 && arabicStepEnd(name, slash + 1) == name.length
        val target = if (after == null || isArabicBass) null else (stepOf(after, isOnMinorScale) ?: return null)
        val chord = if (after == null) name else name.substring(0, slash)
        val start = if (chord.firstOrNull()?.let { it in ACCIDENTALS } == true) 1 else 0
        val numeral = numeralAt(chord, start) ?: return null
        val isSmall = numeral == numeral.lowercase()
        val end = start + numeral.length
        val sign = chord.getOrNull(end)?.takeIf { it in "\u00b0\u00f8\u2300+" || it == 'o' && chord.getOrNull(end + 1)?.isLetter() != true }
        val written = chord.substring(if (sign == null) end else end + 1)
        val isMinor = written.startsWith('-') || written.startsWith('m') && !written.startsWith("ma")
        val quality = when (sign) {
            null -> if (isCaseQuality && isSmall && !isMinor) "m" else ""
            '+' -> AUGMENTED
            '\u00b0', 'o' -> "dim"
            else -> "m7b5"
        }
        val rest = when {
            sign == '\u00f8' || sign == '\u2300' -> written.removePrefix("7")
            written.startsWith('-') -> "m" + written.substring(1)
            else -> written
        }
        val isDominant = numeral.uppercase() == "V" || numeral.uppercase() == "VII" && (quality == "dim" || quality == "m7b5" || rest.startsWith("dim"))
        val isApplied = target != null && isDominant
        val root = (if (isApplied) tonic + target else tonic) + stepOf(chord.substring(0, end), isOnMinorScale && !isApplied)!!
        val bass = when {
            isArabicBass -> "/" + noteOn(tonic + arabicStep(after!!))
            target != null && !isApplied -> "/" + noteOn(tonic + target)
            else -> ""
        }
        val result = noteOn(root) + quality + rest + bass
        if (!ChordProChordNames.isChordName(result)) return null
        return if (isParenthesized) "($result)" else result
    }

    /** The numeral at [start] of [chord], in capitals or in small letters throughout (`IV`, `iv`), or null. */
    private fun numeralAt(chord: String, start: Int) =
        romanNumerals.firstOrNull { chord.startsWith(it, start) } ?: romanNumerals.map { it.lowercase() }.firstOrNull { chord.startsWith(it, start) }

    /** The semitones above the tonic of a numeral with nothing after it but its sign (`bVII`, `iii`), or null. */
    private fun stepOf(text: String, isOnMinorScale: Boolean): Int? {
        val start = if (text.firstOrNull()?.let { it in ACCIDENTALS } == true) 1 else 0
        val numeral = numeralAt(text, start)?.takeIf { start + it.length == text.length }?.uppercase() ?: return null
        val lowered = if (isOnMinorScale && numeral in minorScaleLowered) 1 else 0
        return romanDegrees.indexOf(numeral) + (if (start == 1) accidentalOf(text) else 0) - lowered
    }

    /** The semitones a step written with [text] at its start is moved by: a flat down, a sharp up. */
    private fun accidentalOf(text: String) = when (text.first()) {
        'b', '\u266d' -> -1
        '#', '\u266f' -> 1
        else -> 0
    }

    /** The semitones above the tonic of an Arabic step [arabicStepEnd] has read, `b7` or `5`. */
    private fun arabicStep(step: String) = romanDegrees.indexOf(romanSteps[step.last().digitToInt() - 1]) + accidentalOf(step)

    private fun noteOn(pitchClass: Int) = sharpNotes[pitchClass.mod(NOTE_COUNT)]

    /**
     * [rest], what follows a step in numbers, with its extension in parentheses where it starts with a digit: written
     * straight after the step, `57` and `b77sus4` would read as one number. A group the file already wrote (`7(b9)`) is
     * left after the parentheses rather than put inside them.
     */
    private fun extended(rest: String): String {
        if (rest.firstOrNull()?.let { it in '0'..'9' } != true) return rest
        val group = rest.indexOf('(').takeIf { it >= 0 } ?: rest.length
        return "(" + rest.substring(0, group) + ")" + rest.substring(group)
    }

    /** [rest] with the parentheses [extended] puts around an extension taken off (`(6/9)` → `6/9`, `(7)(b9)` → `7(b9)`). */
    private fun withoutExtensionParentheses(rest: String): String {
        val close = rest.indexOf(')')
        return if (rest.startsWith('(') && close > 1 && rest[1] in '0'..'9') rest.substring(1, close) + rest.substring(close + 1) else rest
    }

    private fun nashvilleSuffix(quality: String?, suffix: String) =
        if (quality != null && quality in minorQualities) MINOR_SIGN + suffix.removePrefix(quality) else suffix

    private fun numeral(step: String, quality: String?, suffix: String): String {
        val rest = if (quality == null) suffix else suffix.removePrefix(quality)
        return when (quality) {
            in minorQualities -> when {
                rest.startsWith(HALF_DIMINISHED_SUFFIX) -> step.lowercase() + HALF_DIMINISHED + "7" + rest.removePrefix(HALF_DIMINISHED_SUFFIX)
                else -> step.lowercase() + rest
            }
            "dim", "°" -> step.lowercase() + DIMINISHED + rest
            "ø" -> step.lowercase() + HALF_DIMINISHED + rest
            "aug", "+" -> step + AUGMENTED + rest
            else -> step + suffix
        }
    }

    /** The end of an Arabic step written at [start] (`5`, `b7`, `#4`, `♭7`), or -1 where there is none. */
    private fun arabicStepEnd(text: String, start: Int): Int {
        val digit = if (text.getOrNull(start)?.let { it in ACCIDENTALS } == true) start + 1 else start
        return if (text.getOrNull(digit)?.let { it in '1'..'7' } == true) digit + 1 else -1
    }

    /** The end of a numeral the chord starts with, the sign after a small one included, or null where there is none. */
    private fun romanStepEnd(chord: String): Int? {
        val start = if (chord.firstOrNull()?.let { it in ACCIDENTALS } == true) 1 else 0
        val numeral = romanNumerals.firstOrNull { chord.startsWith(it, start) } ?: romanNumerals.map { it.lowercase() }.firstOrNull { chord.startsWith(it, start) } ?: return null
        val end = start + numeral.length
        return if (chord.getOrNull(end)?.let { it in "°ø+" } == true) end + 1 else end
    }

    /** Collects the root and the word right after it, which is all a step needs to know of a chord. */
    private class QualityReader : ChordNameReader {
        var root: String? = null
        var quality: String? = null
        override fun root(note: String) {
            root = note
        }
        override fun quality(quality: String) {
            this.quality = quality
        }
        override fun number(number: String) = Unit
        override fun altered() = Unit
        override fun added(degree: String) = Unit
        override fun alteration(word: String, degree: String) = Unit
        override fun omitted(degree: String) = Unit
        override fun bass(note: String) = Unit
    }

    private const val MINOR_SIGN = "-"
    private const val DIMINISHED = "°"
    private const val HALF_DIMINISHED = "ø"
    private const val HALF_DIMINISHED_SUFFIX = "7b5"
    private const val AUGMENTED = "+"
    private val minorQualities: Set<String?> = setOf("m", "mi", "min", "-")
    private val nashvilleDegrees = listOf("1", "b2", "2", "b3", "3", "4", "#4", "5", "b6", "6", "b7", "7")
    private val romanDegrees = listOf("I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII")
    private val romanSteps = listOf("I", "II", "III", "IV", "V", "VI", "VII")
    private val romanNumerals = listOf("VII", "VI", "V", "IV", "III", "II", "I")
    private val minorScaleLowered = setOf("III", "VI", "VII")
    private val sharpNotes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
}
