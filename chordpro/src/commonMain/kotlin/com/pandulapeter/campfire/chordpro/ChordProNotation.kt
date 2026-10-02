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

import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * Converts chords between [ChordNotation.STANDARD], which every file is stored in and the model works in, and the
 * notation a reader prefers. It runs at the two boundaries between the two and nowhere else: on the way to the screen
 * ([toNotation] for a parsed song), and in and out of the editor's field ([convertText]), whose text is shown in the
 * reader's notation and written back in the standard one.
 *
 * German notation is the only other one today: the note the standard notation calls `B` is written `H`, and the note it
 * calls `Bb` is written `B`, while the other six letters, the `#` and `b` signs, the quality and the extensions stay as
 * they are, the bass note after `/` rewritten by the same rule as the root. The classical German names spell every
 * accidental out (`Cis`, `Es`, `Ais`), but chord charts in those countries stop at the two letters, and so does this.
 *
 * The lowercase minors of Central European charts (`a` for `Am`, `h` for `Hm`) are a spelling of their own rather than
 * a notation: they are read as the minor chord they stand for in either one, and a conversion keeps them lowercase.
 */
object ChordProNotation {

    /**
     * Rewrites every chord of a parsed song, which is in [ChordNotation.STANDARD], in [notation]: its key, the chords
     * over its lyrics, the chords of its grids and the rows of chord names above its tabs. Run it last, after any
     * transposition, which works in the standard notation.
     */
    fun toNotation(song: ChordProSong, notation: ChordNotation) = when (notation) {
        ChordNotation.STANDARD -> song
        ChordNotation.GERMAN -> ChordProTransposer.rewriteChords(
            song = song,
            rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, ::toGerman) },
            rename = ::toGerman,
        )
    }

    /**
     * Rewrites the chords of a raw document written in [from] in [to], leaving every other character where it was.
     * A text read from a file is converted from [ChordNotation.STANDARD], a text somebody typed in their own notation
     * from that one, which is what makes it unambiguous (see [ChordNotation]). Whatever it is converted to, the chords
     * come out with ASCII accidentals, which is how the standard notation is written: so converting a text from the
     * standard notation to itself is what brings a file written before there was one notation, or with `♯` and `♭`,
     * into it, and returns any other text unchanged.
     *
     * Converting a text out of a notation and back into it returns it, apart from that folding of the accidentals and
     * a German `Bb`, which is read as the B flat its writer meant rather than as a double flat.
     */
    fun convertText(text: String, from: ChordNotation, to: ChordNotation): String {
        val isGerman = from == ChordNotation.GERMAN || isGermanNotated(ChordProParser.parseAsWritten(text))
        if (!isGerman && to == ChordNotation.STANDARD && text.none { it == SHARP_SIGN || it == FLAT_SIGN }) return text
        val rename = ChordProTransposer.keepingLowercaseMinors { name ->
            val standard = withAsciiAccidentals(if (isGerman) fromGerman(name) else name)
            when (to) {
                ChordNotation.STANDARD -> standard
                ChordNotation.GERMAN -> toGerman(standard)
            }
        }
        return ChordProTransposer.rewriteChordNamesInText(
            text = text,
            rewriteTab = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
            rename = rename,
        )
    }

    /**
     * Rewrites one chord name, its bass note included, in German notation. A word that is not a chord — `N.C.`, or a
     * `[Bridge]` somebody wrote without the `*` that makes it an annotation — is returned as it was, which is what keeps
     * this from turning prose starting with a `B` into prose starting with an `H`.
     */
    internal fun toGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteToGerman)
    }

    /** Whether [name] is a real chord that uses German notation's `H`, at its root or bass, a lowercase `h` minor or bass note included. */
    internal fun isGermanName(name: String) = (ChordProChordNames.lowercaseMinorExpanded(name) ?: name).let { chord ->
        ChordProChordNames.isChordName(chord) && ChordProChordNames.notes(chord).any { it.uppercase().startsWith(GERMAN_B_NATURAL) }
    }

    /** Whether a file's song was written in German notation, which it says by using an `H` chord anywhere. */
    internal fun isGermanNotated(song: ChordProSong) = ChordProTransposer.writtenChordNames(song).any(::isGermanName)

    /** Reads a German-notated chord into the notation the rest of the app works in. */
    internal fun fromGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteFromGerman)
    }

    /** The same for a whole song as its file spells it. */
    internal fun fromGerman(song: ChordProSong) = ChordProTransposer.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, ::fromGerman) },
        rename = ::fromGerman,
    )

    /** [song] with its lowercase minor chords spelled out, and nothing else changed. */
    internal fun withLowercaseMinorsExpanded(song: ChordProSong) = ChordProTransposer.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> lines },
        rename = { name -> ChordProChordNames.lowercaseMinorExpanded(name) ?: name },
    )

    /** A chord name with musical accidental signs folded to the ASCII spelling the app writes. */
    internal fun withAsciiAccidentals(name: String) = name.replace(SHARP_SIGN, '#').replace(FLAT_SIGN, 'b')

    /** [song], its file written in [notation], in the app's own: English note names and ASCII accidentals. */
    internal fun normalized(song: ChordProSong, notation: ChordNotation): ChordProSong {
        val names = ChordProTransposer.writtenChordNames(song).toList()
        val german = notation == ChordNotation.GERMAN || names.any(::isGermanName)
        val hasLowercaseMinors = names.any { ChordProChordNames.lowercaseMinorExpanded(it) != null }
        if (!german && !hasLowercaseMinors && names.none { SHARP_SIGN in it || FLAT_SIGN in it }) return song
        // The expansion comes first, so that a German `b` (B flat minor) becomes `Bm` and then `Bbm`.
        val rename = { name: String ->
            val expanded = ChordProChordNames.lowercaseMinorExpanded(name) ?: name
            withAsciiAccidentals(if (german) fromGerman(expanded) else expanded)
        }
        return ChordProTransposer.rewriteChords(
            song = song,
            rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines, rename) },
            rename = rename,
        )
    }

    private fun noteToGerman(note: String): String {
        // `H` is already the German name of the note, and `Hb` an unusual but unambiguous way of writing the flat one.
        val letter = note.firstOrNull()
        if (letter != 'B' && letter != 'H') return note
        val accidental = note.getOrNull(1)
        return if (accidental == 'b' || accidental == '♭') {
            "B" + note.substring(2) // Bb -> B: the flat is in the letter now, so its sign goes away with it.
        } else {
            "H" + note.substring(1) // B -> H, and B# -> H#, which the classical German names would spell His.
        }
    }

    private fun noteFromGerman(note: String) = when (note.firstOrNull()) {
        'H' -> "B" + note.substring(1)
        'B' -> if (note.getOrNull(1) in accidentalSigns) note else "Bb" + note.substring(1)
        else -> note
    }

    private const val GERMAN_B_NATURAL = "H"
    private const val SHARP_SIGN = '♯'
    private const val FLAT_SIGN = '♭'
    private val accidentalSigns = setOf('#', 'b', '♯', '♭')
}
