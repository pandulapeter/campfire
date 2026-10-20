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

import com.pandulapeter.campfire.chordpro.model.Chord

/**
 * The scales a chord's quality is played over first, by the textbook: Dorian over a minor seventh, Locrian over a
 * half-diminished chord, the whole-tone scale over an augmented seventh. [Scales.common] orders what holds a chord by how
 * often a player reaches for a scale at all, which over a lone chord puts the blues before Locrian on a `Cm7b5` and the
 * minor key before Dorian on a `Cm7`; the chord's own tensions say better what it wants.
 */
internal object ChordScaleChoices {

    /** The ids of the scales to offer first over [chord], in order, the first rule its intervals match deciding. */
    fun firstChoices(chord: Chord): List<String> {
        val intervals = chord.intervals.map { it.mod(12) }.toSet()
        return rules.firstOrNull { rule -> intervals.containsAll(rule.has) && intervals.none { it in rule.lacks } }?.choices.orEmpty()
    }

    private class Rule(val has: Set<Int>, val lacks: Set<Int>, val choices: List<String>)

    private fun rule(has: String, lacks: String, vararg choices: String) = Rule(
        has = has.split(' ').map(String::toInt).toSet(),
        lacks = lacks.split(' ').filter { it.isNotEmpty() }.map(String::toInt).toSet(),
        choices = choices.toList(),
    )

    private val rules = listOf(
        rule("3 6 9", "10", "diminished"),
        rule("3 6 10", "", "locrian", "locrian_natural_2"),
        rule("3 6", "7 10", "diminished", "locrian"),
        rule("4 10 1", "", "dominant_diminished", "phrygian_dominant", "altered"),
        rule("4 10 8", "", "whole_tone", "altered", "mixolydian_flat_6"),
        rule("4 10 6", "7", "whole_tone", "lydian_dominant", "altered"),
        rule("4 10 6", "", "lydian_dominant"),
        rule("3 4 10", "", "blues", "minor_pentatonic", "dominant_diminished", "altered"),
        rule("5 10", "3 4", "mixolydian", "dorian"),
        rule("4 10", "", "mixolydian"),
        rule("3 11", "10", "melodic_minor", "harmonic_minor"),
        rule("3 9", "10", "dorian", "melodic_minor"),
        rule("3 10", "", "dorian", "natural_minor"),
        rule("4 8 11", "", "lydian_augmented", "ionian_sharp_5"),
        rule("4 8", "10 11", "whole_tone", "augmented", "lydian_augmented", "ionian_sharp_5"),
        rule("4 11 6", "", "lydian"),
        rule("4 11", "", "major", "lydian"),
    )
}
