/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation

/**
 * The names of a scale's notes in a key, by the rule [ChordProChords.spelledNoteNames] spells a chord's by, so that a note
 * is named the same in a chord sheet and in a scale: one letter per degree, counted from the root's letter by the degree's
 * number, and its accidental from the pitch class. A note that would need a double sharp or flat, or would be one of `Cb`,
 * `Fb`, `E#`, `B#`, is named with the root's accidental instead (flats for a root written with `b`, else sharps), since a
 * player reads keys and strings rather than theory: F♯ major's seventh reads `F`.
 *
 * Every root is given in the standard notation, as the key row writes it (`Eb`, `C#`), and every name comes back in the
 * notation asked for, in letters for a numbering.
 */
public object ScaleSpelling {

    /** The notes of [scale] played on [rootName], from the root up, one per interval. */
    public fun noteNames(scale: Scale, rootName: String, notation: ChordNotation = ChordNotation.STANDARD): List<String> {
        val root = ChordProChords.pitchClass(rootName) ?: 0
        return scale.intervals.zip(scale.degrees) { interval, degree ->
            ChordProNotation.shownName(spell(rootName, letterStep(degree), (root + interval).mod(12)), notation)
        }
    }

    /**
     * The root of the scale [scale] is a mode of where it is played on [rootName], spelled by the letter it stands below
     * the root by: C for D Dorian, Eb for Bb Mixolydian. Null where it is a mode of none.
     */
    public fun parentRootName(scale: Scale, rootName: String, notation: ChordNotation = ChordNotation.STANDARD): String? {
        val (parentId, degree) = scale.modeOf ?: return null
        val parent = Scales.fromId(parentId) ?: return null
        val pitchClass = Scales.parentRoot(scale, ChordProChords.pitchClass(rootName) ?: 0) ?: return null
        return ChordProNotation.shownName(spell(rootName, -letterStep(parent.degrees[degree - 1]), pitchClass), notation)
    }

    /**
     * The note [letterStep] letters away from [rootName]'s that is the pitch class [pitchClass], in the standard notation,
     * named with the root's accidental where the letter cannot name it with a single sharp or flat or names a white key
     * by the other's letter.
     */
    internal fun spell(rootName: String, letterStep: Int, pitchClass: Int): String {
        val fallback = if (rootName.getOrNull(1) == 'b') flatNames else sharpNames
        val rootLetter = LETTERS.indexOf(rootName.firstOrNull()?.uppercaseChar() ?: ' ')
        if (rootLetter < 0) return fallback[pitchClass]
        val letter = LETTERS[(rootLetter + letterStep).mod(LETTERS.length)]
        val spelled = when ((pitchClass - naturals[LETTERS.indexOf(letter)]).mod(12)) {
            0 -> letter.toString()
            1 -> "$letter#"
            11 -> "${letter}b"
            else -> null
        }
        return spelled?.takeIf { it !in whiteKeyEnharmonics } ?: fallback[pitchClass]
    }

    /** How many letters above the root a degree label (`b3`, `#4`, `bb7`) is written: its number less one. */
    internal fun letterStep(degree: String) = (degree.trimStart('b', '#').toIntOrNull() ?: 1) - 1

    private const val LETTERS = "CDEFGAB"
    private val naturals = listOf(0, 2, 4, 5, 7, 9, 11)
    private val whiteKeyEnharmonics = setOf("Cb", "Fb", "E#", "B#")
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
}
