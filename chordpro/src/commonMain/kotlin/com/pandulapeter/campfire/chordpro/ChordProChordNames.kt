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

/** Linear recognition and rewriting of the notes in a chord name. */
internal object ChordProChordNames {

    fun isChordName(word: String) = read(word, reader = null)

    /**
     * Walks a chord name, handing each part of it to [reader] in the order it is written, and answers whether the
     * whole of it was a chord name. [isChordName] is this walk with nobody listening, and [ChordProChords.parse] is it
     * with a reader that collects the notes, which is what keeps the two from ever disagreeing about a name: a part is
     * only handed over once it has been recognized, and a name the walk rejects halfway has handed over parts that the
     * caller then throws away.
     */
    fun read(word: String, reader: ChordNameReader?): Boolean {
        val name = unwrapped(word)
        if (name.isEmpty()) return false
        var index = noteEnd(name, 0)
        if (index < 0) return false
        reader?.root(name.substring(0, index))
        qualityAt(name, index)?.let { quality ->
            reader?.quality(quality)
            index += quality.length
        }
        val numberStart = index
        index = digitsEnd(name, index)
        if (index > numberStart) reader?.number(name.substring(numberStart, index))
        if (name.startsWith("alt", index) && index > numberStart) {
            reader?.altered()
            index += 3
        }
        while (index < name.length) {
            when (name[index]) {
                '(' -> index = groupEnd(name, index + 1, reader)
                '/' -> {
                    val digits = digitsEnd(name, index + 1)
                    if (digits > index + 1) {
                        reader?.added(name.substring(index + 1, digits))
                        index = digits
                    } else {
                        if (bassNoteEnd(name, index + 1) != name.length) return false
                        reader?.bass(name.substring(index + 1))
                        return true
                    }
                }
                else -> index = alterationEnd(name, index, reader)
            }
            if (index < 0) return false
        }
        return true
    }

    fun notes(name: String): List<String> {
        val chord = unwrapped(name)
        val separator = chord.lastIndexOf('/')
        return if (separator >= 0 && bassNoteEnd(chord, separator + 1) >= 0) listOf(chord.substring(0, separator), chord.substring(separator + 1)) else listOf(chord)
    }

    fun rewriteNotes(name: String, rewrite: (String) -> String): String {
        val notes = notes(name)
        val rewritten = (listOf(rewrite(notes.first())) + notes.drop(1).map { bass -> rewriteBassNote(bass, rewrite) }).joinToString("/")
        return if (isParenthesized(name)) "($rewritten)" else rewritten
    }

    /**
     * [rewrite] applied to the bass note of a chord name, in capitals however the file spells it, and folded back to
     * the case it was written in. Every rewriter here knows the capital letters only, and a chart that writes `D/f#`
     * has to get `E/g#` back rather than `E/G#`: the file keeps its own convention, the same way
     * [ChordProTransposer.transposeText] keeps a lowercase minor root. The root is handed over as it is written,
     * since a lowercase one reaching this far is a word in brackets (`[fine]`) rather than a note, see
     * [lowercaseMinorExpanded].
     */
    private fun rewriteBassNote(note: String, rewrite: (String) -> String): String {
        val letter = note.firstOrNull() ?: return rewrite(note)
        if (!letter.isLowerCase()) return rewrite(note)
        val rewritten = rewrite(letter.uppercaseChar() + note.substring(1))
        val first = rewritten.firstOrNull() ?: return rewritten
        return first.lowercaseChar() + rewritten.substring(1)
    }

    /**
     * [word] as the minor chord a lowercase root stands for in Central European charts — `a` is `Am`, `h7` is `Hm7`,
     * `f#` is `F#m`, `(e)` is `(Em)` — or null for anything else. A suffix that starts with an `m` is not taken, since
     * `am` would say minor twice and `amaj7` would be neither. The bass note after `/` is left as it is written, in
     * either case, see [bassNoteEnd].
     */
    fun lowercaseMinorExpanded(word: String): String? {
        val name = unwrapped(word)
        val root = name.firstOrNull()?.takeIf { it in 'a'..'h' } ?: return null
        val noteLength = if (name.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
        if (name.startsWith("m", noteLength)) return null
        val expanded = root.uppercaseChar() + name.substring(1, noteLength) + "m" + name.substring(noteLength)
        if (!isChordName(expanded)) return null
        return if (isParenthesized(word)) "($expanded)" else expanded
    }

    /**
     * [word] as the chord a Latin name stands for — `Lam` is `Am`, `Sib7` is `Bb7`, `DO/MI` is `C/E`, `Re/fa#` is
     * `D/f#`, `(Sol)` is `(G)` — or null for anything else. The root is read in title case or in capitals (`Do`, `DO`,
     * and the accented `Dó`, `Ré`, `Fá` and `Lá`), never in lowercase, which is how a word is written; the bass note
     * after the last `/` in any case, as a standard one is (see [bassNoteEnd]).
     *
     * No name is a chord in both readings, which is what lets this be asked of every name a file holds: a standard
     * chord that starts like a Latin note would need an `o`, an `e`, an `i` or an `a` right after its letter, and
     * `ChordProChordNamesTest` sweeps every quality and alteration there is to keep it so — a sign written `o` for a
     * diminished chord would make `Do` both C and D diminished.
     */
    fun latinExpanded(word: String): String? {
        val name = unwrapped(word)
        // A cheap refusal first, since this is asked of every name of every file that is read.
        if (name.firstOrNull()?.let { it in LATIN_INITIALS } != true || name.getOrNull(1)?.let { it in LATIN_SECOND_LETTERS } != true) return null
        val (letter, length) = latinNoteAt(name, 0, isAnyCase = false) ?: return null
        var rest = name.substring(length)
        val slash = rest.lastIndexOf('/')
        if (slash >= 0) {
            latinNoteAt(rest, slash + 1, isAnyCase = true)?.let { (bassLetter, bassLength) ->
                val noteEnd = slash + 1 + bassLength
                val end = if (rest.getOrNull(noteEnd)?.let { it in ACCIDENTALS } == true) noteEnd + 1 else noteEnd
                if (end == rest.length) {
                    val bass = if (rest[slash + 1].isLowerCase()) bassLetter.lowercaseChar() else bassLetter
                    rest = rest.substring(0, slash + 1) + bass + rest.substring(noteEnd)
                }
            }
        }
        val expanded = letter + rest
        if (!isChordName(expanded)) return null
        return if (isParenthesized(word)) "($expanded)" else expanded
    }

    /**
     * Whether [word] is a chord name as a page may show it once [ChordProNotation.toNotation] has written it: in the
     * standard or the German notation, in the Latin one, or as a step of the key in either numbering. Only what reads a
     * song that has already been converted asks this, which is the viewer cutting a tab into rows.
     */
    fun isDisplayedChordName(word: String) = isChordName(word) || latinExpanded(word) != null || ChordProNashville.isDegree(word)

    /** The length of the Latin note [key] starts with, in title case or capitals and without its accidental, or null. */
    fun latinNoteLength(key: String) = latinNoteAt(key, 0, isAnyCase = false)?.second

    /** The standard letter of the Latin note [text] spells at [index], and its length, or null where it spells none. */
    private fun latinNoteAt(text: String, index: Int, isAnyCase: Boolean): Pair<Char, Int>? {
        latinNotes.forEach { (spelling, letter) ->
            val candidate = text.substring(index, (index + spelling.length).coerceAtMost(text.length))
            if (candidate == spelling || candidate == spelling.uppercase() || (isAnyCase && candidate.lowercase() == spelling.lowercase())) {
                return letter to spelling.length
            }
        }
        return null
    }

    /** The other way: [name], a minor chord, written with a lowercase root and no `m`. */
    fun lowercaseMinorFolded(name: String): String {
        val chord = unwrapped(name)
        val noteLength = if (chord.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
        val folded = chord[0].lowercaseChar() + chord.substring(1, noteLength) + chord.substring(noteLength).removePrefix("m")
        return if (isParenthesized(name)) "($folded)" else folded
    }

    private fun isParenthesized(name: String) = name.length > 2 && name.first() == '(' && name.last() == ')'
    private fun unwrapped(name: String) = if (isParenthesized(name)) name.substring(1, name.length - 1) else name
    private fun noteEnd(name: String, index: Int): Int {
        if (name.getOrNull(index) !in 'A'..'H') return -1
        return if (name.getOrNull(index + 1)?.let { it in "#b♯♭" } == true) index + 2 else index + 1
    }
    /**
     * The same as [noteEnd] for the note after a `/`. A chart that writes its minor roots in lowercase writes the
     * bass note that way too (`D/f#`, `C/h`), and unlike a root there is nothing a lowercase letter there could be
     * instead: the slash has already said a note follows. The root stays capitals only, since a lowercase one is
     * how the same charts write a minor chord, see [lowercaseMinorExpanded].
     */
    private fun bassNoteEnd(name: String, index: Int): Int {
        val letter = name.getOrNull(index) ?: return -1
        if (letter.uppercaseChar() !in 'A'..'H') return -1
        return if (name.getOrNull(index + 1)?.let { it in "#b♯♭" } == true) index + 2 else index + 1
    }
    private fun digitsEnd(name: String, start: Int): Int { var index = start; while (name.getOrNull(index)?.isAsciiDigit == true) index++; return index }

    /**
     * A chord's numbers are ASCII digits. [Char.isDigit] takes every script's, which `C٣7` would pass, and what
     * `toInt` makes of those differs between the JVM and the other platforms.
     */
    private val Char.isAsciiDigit get() = this in '0'..'9'
    private fun qualityAt(name: String, index: Int) = qualities.firstOrNull { name.startsWith(it, index) }
    private fun alterationEnd(name: String, index: Int, reader: ChordNameReader?): Int {
        val word = alterations.firstOrNull { name.startsWith(it, index) }
            ?: name[index].toString().takeIf { it == "+" || it == "-" }
            ?: return -1
        val end = digitsEnd(name, index + word.length)
        if (word == "-" && end == index + 1) return -1
        reader?.alteration(word, name.substring(index + word.length, end))
        return end
    }
    private fun groupEnd(name: String, start: Int, reader: ChordNameReader?): Int {
        var index = start
        var requiresItem = true
        while (index < name.length && name[index] != ')') {
            if (!requiresItem && name[index] == ',') { index++; while (name.getOrNull(index) == ' ') index++; requiresItem = true; continue }
            val number = digitsEnd(name, index)
            index = if (number > index) {
                reader?.added(name.substring(index, number))
                number
            } else {
                val omission = omissions.firstOrNull { name.startsWith(it, index) }
                if (omission != null) {
                    val end = digitsEnd(name, index + omission.length).takeIf { it > index + omission.length } ?: return -1
                    reader?.omitted(name.substring(index + omission.length, end))
                    end
                } else {
                    alterationEnd(name, index, reader)
                }
            }
            if (index < 0) return -1
            requiresItem = false
        }
        return if (index > start && !requiresItem && name.getOrNull(index) == ')') index + 1 else -1
    }

    private val qualities = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "m", "M", "+", "-", "°", "ø", "Δ", "∆")
    private val alterations = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "M", "#", "b", "♯", "♭")
    private val omissions = listOf("no", "omit")

    /** Every spelling of a Latin note that is read, the longest first, and the standard letter of each. */
    private val latinNotes = listOf(
        "Sol" to 'G',
        "Do" to 'C',
        "Dó" to 'C',
        "Re" to 'D',
        "Ré" to 'D',
        "Mi" to 'E',
        "Fa" to 'F',
        "Fá" to 'F',
        "La" to 'A',
        "Lá" to 'A',
        "Si" to 'B',
    )
    private const val LATIN_INITIALS = "DRMFSL"
    private const val LATIN_SECOND_LETTERS = "oeiaOEIAóéáÓÉÁ"
    private const val ACCIDENTALS = "#b♯♭"
}

/**
 * What [ChordProChordNames.read] hands over while it walks a name, each part once it has been recognized. The numbers
 * are the digits as written, never empty except for an [alteration] written without one (`+`, `sus`, `maj`).
 */
internal interface ChordNameReader {

    /** The root as written, its accidental included: `C`, `F#`, `Bb`, `H`. */
    fun root(note: String)

    /** The word right after the root: `m`, `maj`, `dim`, `sus`, `add`, `+`, `°`, `ø`, `Δ` and the rest. */
    fun quality(quality: String)

    /** The number after the quality: `7` in `Cm7`, `69` in `C69`, `2` in `Csus2`. */
    fun number(number: String)

    /** The `alt` after the number of `C7alt`. */
    fun altered()

    /** A degree added on its own: a number in parentheses (`C7(9)`) or after a slash (`C6/9`). */
    fun added(degree: String)

    /** A word or sign after the number, with its digits: `b` and `5` in `Cm7b5`, `add` and `9` in `Cm(add9)`. */
    fun alteration(word: String, degree: String)

    /** The degree of a `no3` or an `omit5`. */
    fun omitted(degree: String)

    /** The note after the last slash, as written, which may be in lowercase. */
    fun bass(note: String)
}
