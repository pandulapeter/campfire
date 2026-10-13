/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * A tuning the chord diagrams can be drawn for: what a diagram stands for wherever an instrument used to, the keyboard
 * included, whose only entry is its standard one.
 *
 * A chord name always names the chord that sounds, in every tuning: a half step down, an A is drawn as the shape that
 * sounds A on those strings (`x 1 3 3 3 1`, the standard tuning's B♭ shape), and on a baritone ukulele a G is the
 * soprano's C shape. So a diagram is always a shape on the strings the player has, its root dots marking the root of
 * the chord the page names on those strings.
 *
 * A tuning either has a table of shapes of its own (the standard tunings, drop D, double drop D, open G, D, and C, DADGAD,
 * and the mandolin's GDGD), or is one of those with every string moved by the same interval (half and full step down,
 * drop C♯ to drop B, open E and A, the ukulele's low G, D, and baritone tunings, and the mandolin's AEAE), whose shapes
 * are that table's, moved along the chords rather than along the neck.
 *
 * @property id What a stored choice names the tuning by, equal to the tuner's id of the same tuning (`guitar_drop_d`),
 *   so the two can be matched without this module knowing the tuner. The standard tunings' ids are their instruments'.
 * @property strings The open strings as MIDI note numbers, in [ChordInstrument.tuning]'s order: the real strings, which
 *   is not always a plain move of the standard ones (the baritone ukulele's fourth string is an octave lower than that).
 */
public enum class ChordTuning(
    public val id: String,
    public val instrument: ChordInstrument,
    public val strings: List<Int>,
) {
    GUITAR("guitar", ChordInstrument.GUITAR, ChordInstrument.GUITAR.tuning),
    GUITAR_DROP_D("guitar_drop_d", ChordInstrument.GUITAR, listOf(38, 45, 50, 55, 59, 64)),
    GUITAR_HALF_STEP_DOWN("guitar_half_step_down", ChordInstrument.GUITAR, listOf(39, 44, 49, 54, 58, 63)),
    GUITAR_FULL_STEP_DOWN("guitar_full_step_down", ChordInstrument.GUITAR, listOf(38, 43, 48, 53, 57, 62)),
    GUITAR_DROP_C_SHARP("guitar_drop_c_sharp", ChordInstrument.GUITAR, listOf(37, 44, 49, 54, 58, 63)),
    GUITAR_DROP_C("guitar_drop_c", ChordInstrument.GUITAR, listOf(36, 43, 48, 53, 57, 62)),
    GUITAR_DROP_B("guitar_drop_b", ChordInstrument.GUITAR, listOf(35, 42, 47, 52, 56, 61)),
    GUITAR_DOUBLE_DROP_D("guitar_double_drop_d", ChordInstrument.GUITAR, listOf(38, 45, 50, 55, 59, 62)),
    GUITAR_OPEN_G("guitar_open_g", ChordInstrument.GUITAR, listOf(38, 43, 50, 55, 59, 62)),
    GUITAR_OPEN_D("guitar_open_d", ChordInstrument.GUITAR, listOf(38, 45, 50, 54, 57, 62)),
    GUITAR_OPEN_E("guitar_open_e", ChordInstrument.GUITAR, listOf(40, 47, 52, 56, 59, 64)),
    GUITAR_OPEN_A("guitar_open_a", ChordInstrument.GUITAR, listOf(40, 45, 52, 57, 61, 64)),
    GUITAR_OPEN_C("guitar_open_c", ChordInstrument.GUITAR, listOf(36, 43, 48, 55, 60, 64)),
    GUITAR_DADGAD("guitar_dadgad", ChordInstrument.GUITAR, listOf(38, 45, 50, 55, 57, 62)),
    UKULELE("ukulele", ChordInstrument.UKULELE, ChordInstrument.UKULELE.tuning),
    UKULELE_LOW_G("ukulele_low_g", ChordInstrument.UKULELE, listOf(55, 60, 64, 69)),
    UKULELE_BARITONE("ukulele_baritone", ChordInstrument.UKULELE, listOf(50, 55, 59, 64)),
    UKULELE_D("ukulele_d", ChordInstrument.UKULELE, listOf(69, 62, 66, 71)),
    KEYBOARD("keyboard", ChordInstrument.KEYBOARD, ChordInstrument.KEYBOARD.tuning),
    MANDOLIN("mandolin", ChordInstrument.MANDOLIN, ChordInstrument.MANDOLIN.tuning),
    MANDOLIN_HALF_STEP_DOWN("mandolin_half_step_down", ChordInstrument.MANDOLIN, listOf(54, 61, 68, 75)),
    MANDOLIN_AEAE("mandolin_aeae", ChordInstrument.MANDOLIN, listOf(57, 64, 69, 76)),
    MANDOLIN_GDGD("mandolin_gdgd", ChordInstrument.MANDOLIN, listOf(55, 62, 67, 74)),
    ;

    /** Whether this is its instrument's standard tuning, which a `{define}` and the standard tables are written for. */
    public val isStandard: Boolean get() = id == instrument.id

    /**
     * The tuning whose table this one's shapes come from: itself where it has a table of its own, and otherwise the
     * one it is with every string moved by [shift]. `ChordVoicingTablesTest` holds every such pair to one interval.
     */
    internal val tableTuning: ChordTuning
        get() = when (this) {
            GUITAR_HALF_STEP_DOWN, GUITAR_FULL_STEP_DOWN -> GUITAR
            GUITAR_DROP_C_SHARP, GUITAR_DROP_C, GUITAR_DROP_B -> GUITAR_DROP_D
            GUITAR_OPEN_E -> GUITAR_OPEN_D
            GUITAR_OPEN_A -> GUITAR_OPEN_G
            UKULELE_LOW_G, UKULELE_BARITONE, UKULELE_D -> UKULELE
            MANDOLIN_HALF_STEP_DOWN -> MANDOLIN
            MANDOLIN_AEAE -> MANDOLIN_GDGD
            GUITAR, GUITAR_DROP_D, GUITAR_DOUBLE_DROP_D, GUITAR_OPEN_G, GUITAR_OPEN_D, GUITAR_OPEN_C, GUITAR_DADGAD, UKULELE, KEYBOARD, MANDOLIN,
            MANDOLIN_GDGD,
            -> this
        }

    /**
     * Whether this is an open or a modal tuning, whose open strings are tuned to a chord (or to the fifths of a fiddle's
     * cross tuning) and ring under whatever is played on them. There the chord's fifth may sound lowest where it is an
     * open string, the way open G's G is all six strings with the open D under them, which no other tuning allows.
     */
    internal val isOpen: Boolean
        get() = when (tableTuning) {
            GUITAR_OPEN_G, GUITAR_OPEN_D, GUITAR_OPEN_C, GUITAR_DADGAD, MANDOLIN_GDGD -> true
            else -> false
        }

    /** The pitch classes every string is moved by from [tableTuning]'s, 0 to 11: a half step down is 11. */
    internal val shift: Int get() = if (strings.isEmpty()) 0 else (strings.first() - tableTuning.strings.first()).mod(12)

    /**
     * Whether a song's own `{define}` is drawn in this tuning. A definition has no tuning of its own (ChordPro keeps one
     * in the program's configuration, not in the song), and nearly every file is written for standard tuning, so one is
     * drawn only where a standard tuning shape sounds what it says: standard tuning, and the ukulele's low G, whose
     * strings are the standard ones but for the fourth string's octave.
     */
    public val drawsDefinitions: Boolean get() = tableTuning.isStandard && shift == 0

    /**
     * What the player's own choices of shape are stored under: the instrument's id for every tuning whose shapes are
     * the standard ones ([drawsDefinitions]), and the tuning's own id otherwise, so that a shape chosen a half step down
     * or in drop D, which sounds another chord in standard tuning, never turns up there.
     */
    public val voicingKey: String get() = if (drawsDefinitions) instrument.id else id

    public companion object {

        /** The standard tuning of [instrument]. */
        public fun standard(instrument: ChordInstrument): ChordTuning = entries.first { it.instrument == instrument && it.isStandard }

        /** The tunings of [instrument], its standard one first. */
        public fun of(instrument: ChordInstrument): List<ChordTuning> = entries.filter { it.instrument == instrument }

        /** The tuning [id] names, or null for one this version does not know. */
        public fun fromId(id: String): ChordTuning? = entries.firstOrNull { it.id == id }
    }
}
