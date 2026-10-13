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
 * @property id What a stored choice names the tuning by, equal to the tuner's id of the same tuning (`guitar_drop_d`),
 *   so the two can be matched without this module knowing the tuner. The standard tunings' ids are their instruments'.
 * @property strings The open strings as MIDI note numbers, in [ChordInstrument.tuning]'s order: the real strings, which
 *   is not always a plain move of the standard ones (the baritone ukulele's fourth string is an octave lower than that).
 * @property kind How the tuning is made from its instrument's standard one.
 */
public enum class ChordTuning(
    public val id: String,
    public val instrument: ChordInstrument,
    public val strings: List<Int>,
    public val kind: Kind,
) {
    GUITAR("guitar", ChordInstrument.GUITAR, ChordInstrument.GUITAR.tuning, Kind.Standard),
    GUITAR_DROP_D("guitar_drop_d", ChordInstrument.GUITAR, listOf(38, 45, 50, 55, 59, 64), Kind.Dropped(0)),
    GUITAR_HALF_STEP_DOWN("guitar_half_step_down", ChordInstrument.GUITAR, listOf(39, 44, 49, 54, 58, 63), Kind.Shifted(-1)),
    GUITAR_FULL_STEP_DOWN("guitar_full_step_down", ChordInstrument.GUITAR, listOf(38, 43, 48, 53, 57, 62), Kind.Shifted(-2)),
    GUITAR_DROP_C_SHARP("guitar_drop_c_sharp", ChordInstrument.GUITAR, listOf(37, 44, 49, 54, 58, 63), Kind.Dropped(-1)),
    GUITAR_DROP_C("guitar_drop_c", ChordInstrument.GUITAR, listOf(36, 43, 48, 53, 57, 62), Kind.Dropped(-2)),
    GUITAR_DROP_B("guitar_drop_b", ChordInstrument.GUITAR, listOf(35, 42, 47, 52, 56, 61), Kind.Dropped(-3)),
    GUITAR_DOUBLE_DROP_D("guitar_double_drop_d", ChordInstrument.GUITAR, listOf(38, 45, 50, 55, 59, 62), Kind.Dropped(0)),
    UKULELE("ukulele", ChordInstrument.UKULELE, ChordInstrument.UKULELE.tuning, Kind.Standard),
    UKULELE_LOW_G("ukulele_low_g", ChordInstrument.UKULELE, listOf(55, 60, 64, 69), Kind.Shifted(0)),
    UKULELE_BARITONE("ukulele_baritone", ChordInstrument.UKULELE, listOf(50, 55, 59, 64), Kind.Shifted(-5)),
    UKULELE_D("ukulele_d", ChordInstrument.UKULELE, listOf(69, 62, 66, 71), Kind.Shifted(2)),
    KEYBOARD("keyboard", ChordInstrument.KEYBOARD, ChordInstrument.KEYBOARD.tuning, Kind.Standard),
    MANDOLIN("mandolin", ChordInstrument.MANDOLIN, ChordInstrument.MANDOLIN.tuning, Kind.Standard),
    MANDOLIN_HALF_STEP_DOWN("mandolin_half_step_down", ChordInstrument.MANDOLIN, listOf(54, 61, 68, 75), Kind.Shifted(-1)),
    ;

    /** How a tuning is made from its instrument's standard one. */
    public sealed interface Kind {

        /** The standard tuning, which a `{define}` and the tables are written for. */
        public data object Standard : Kind

        /**
         * Every string moved by [semitones] (as pitch classes: an octave of one string's is the [strings]' to say), so
         * that every standard shape is still a shape, sounding [semitones] away: a chord is drawn as the standard shape
         * of the chord [semitones] above it, which sounds the chord itself.
         */
        public data class Shifted(
            public val semitones: Int,
        ) : Kind

        /**
         * A shifted tuning, [shift] semitones from the standard one, with some strings lowered further: drop D, and
         * drop C♯ to drop B, which are drop D a half step to a step and a half down. Which strings and how far is the
         * difference of [strings] from the shifted standard. The shapes are the standard ones re-fretted on the lowered
         * strings where that still makes the chord, and the search's otherwise, each sounding the chord it is drawn for.
         */
        public data class Dropped(
            public val shift: Int,
        ) : Kind
    }

    /** How many semitones the tuning moves the standard one by, the dropped strings not counted. */
    internal val shift: Int
        get() = when (kind) {
            Kind.Standard -> 0
            is Kind.Shifted -> kind.semitones
            is Kind.Dropped -> kind.shift
        }

    /**
     * Whether a song's own `{define}` is drawn in this tuning. A definition has no tuning of its own (ChordPro keeps one
     * in the program's configuration, not in the song), and nearly every file is written for standard tuning, so one is
     * drawn only where a standard tuning shape sounds what it says: standard tuning, and the ukulele's low G, whose
     * strings are the standard ones but for the fourth string's octave.
     */
    public val drawsDefinitions: Boolean get() = kind == Kind.Standard || kind is Kind.Shifted && kind.semitones == 0

    /**
     * What the player's own choices of shape are stored under: the instrument's id for every tuning whose shapes are
     * the standard ones ([drawsDefinitions]), and the tuning's own id otherwise, so that a shape chosen a half step down
     * or in drop D, which sounds another chord in standard tuning, never turns up there.
     */
    public val voicingKey: String get() = if (drawsDefinitions) instrument.id else id

    public companion object {

        /** The standard tuning of [instrument]. */
        public fun standard(instrument: ChordInstrument): ChordTuning = entries.first { it.instrument == instrument && it.kind == Kind.Standard }

        /** The tunings of [instrument], its standard one first. */
        public fun of(instrument: ChordInstrument): List<ChordTuning> = entries.filter { it.instrument == instrument }

        /** The tuning [id] names, or null for one this version does not know. */
        public fun fromId(id: String): ChordTuning? = entries.firstOrNull { it.id == id }
    }
}
