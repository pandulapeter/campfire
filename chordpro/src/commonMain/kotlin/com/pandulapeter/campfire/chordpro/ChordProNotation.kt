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
 */
object ChordProNotation {

    /**
     * Rewrites one chord name, its bass note included. A word that is not a chord — `N.C.`, or a `[Bridge]` somebody
     * wrote without the `*` that makes it an annotation — is returned as it was, which is what keeps this from
     * turning prose starting with a `B` into prose starting with an `H`.
     */
    fun toGerman(name: String): String {
        if (!ChordProSyntax.chordNameRegex.matches(name)) return name
        return name.split(BASS_NOTE_SEPARATOR, limit = 2).joinToString(BASS_NOTE_SEPARATOR, transform = ::noteToGerman)
    }

    /**
     * Rewrites every chord of a parsed song: its key, the chords over its lyrics, the chords of its grids and the
     * rows of chord names above its tabs. Run it last, after any transposition — the transposition works in the
     * notation the file is written in, and this is what that result is then read as.
     */
    fun toGerman(song: ChordProSong) = ChordProTransposer.rewriteChords(
        song = song,
        rewriteTabLines = { lines -> ChordProTabTransposer.rewriteChordNames(lines) { name -> toGerman(name) } },
        rename = { name -> toGerman(name) },
    )

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

    private const val BASS_NOTE_SEPARATOR = "/"
}
