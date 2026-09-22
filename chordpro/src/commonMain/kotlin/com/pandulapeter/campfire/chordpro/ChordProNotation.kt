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
 * Writes chord names in German notation, the one a reader in Germany, Austria, Scandinavia, Hungary, Czechia, Poland
 * or Slovakia grew up with: the note the rest of the app calls `B` is written `H`, and the note it calls `Bb` is
 * written `B`. Everything else is left exactly as it was — the other six letters, the `#` and `b` signs, the quality,
 * the extensions, and the bass note after `/` is rewritten by the same rule as the root.
 *
 * The classical German names spell every accidental out (`Cis`, `Es`, `Ais`, and `B` for the note that would
 * otherwise be `Bes`), and that is the system this takes its two letters from. Chord charts in those countries stop
 * there, though: they keep `#` and `b` and swap only `B` and `H`, which is what this does.
 *
 * It is a spelling and nothing else. The library files, and everything the data layer reads, writes, syncs and
 * exports, stay in the notation [ChordProParser] and [ChordProTransposer] work in; this runs on the way to the
 * screen and nowhere else, so a song can be read as `H` and still be edited and shared as `B`.
 * The other direction reads a German-notated file into the app's notation before it is drawn; only the editor's
 * transposition writes a German-notated file back in that notation.
 */
object ChordProNotation {

    /**
     * Rewrites one chord name, its bass note included. A word that is not a chord — `N.C.`, or a `[Bridge]` somebody
     * wrote without the `*` that makes it an annotation — is returned as it was, which is what keeps this from
     * turning prose starting with a `B` into prose starting with an `H`.
     */
    fun toGerman(name: String): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name, ::noteToGerman)
    }

    /**
     * Rewrites every chord of a parsed song: its key, the chords over its lyrics, the chords of its grids and the
     * rows of chord names above its tabs. Run it last, after any transposition — the parser has already brought the
     * model into the app's own notation, and this is what that result is then read as.
     */
    fun toGerman(song: ChordProSong) = ChordProTransposer.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines) { name -> toGerman(name) } },
        rename = { name -> toGerman(name) },
    )

    /** Whether [name] is a real chord that uses German notation's `H`, at its root or bass, a lowercase `h` minor included. */
    internal fun isGermanName(name: String) = (ChordProChordNames.lowercaseMinorExpanded(name) ?: name).let { chord ->
        ChordProChordNames.isChordName(chord) && ChordProChordNames.notes(chord).any { it.startsWith(GERMAN_B_NATURAL) }
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

    /** [song] in the app's own notation: English note names and ASCII accidentals. */
    internal fun normalized(song: ChordProSong): ChordProSong {
        val names = ChordProTransposer.writtenChordNames(song).toList()
        val german = names.any(::isGermanName)
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
