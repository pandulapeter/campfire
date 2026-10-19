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

/**
 * The groups [Scales.all] is browsed by, in the order they are offered: the modes of the three parent scales a player
 * meets first, then the rest by how often a song asks for them.
 *
 * @property id Stable and lowercase: what a string resource is keyed by.
 */
public enum class ScaleFamily(public val id: String) {
    MAJOR_MODES("major_modes"),
    HARMONIC_MINOR_MODES("harmonic_minor_modes"),
    MELODIC_MINOR_MODES("melodic_minor_modes"),
    MINOR_AND_HARMONIC("minor_and_harmonic"),
    PENTATONIC("pentatonic"),
    BLUES_AND_HEXATONIC("blues_and_hexatonic"),
    SYMMETRIC("symmetric"),
    BEBOP("bebop"),
    WORLD("world"),
}
