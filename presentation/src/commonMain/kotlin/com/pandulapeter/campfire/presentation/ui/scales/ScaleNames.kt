/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scale_altered
import com.pandulapeter.campfire.presentation.resources.scale_aliases_altered
import com.pandulapeter.campfire.presentation.resources.scale_aliases_augmented
import com.pandulapeter.campfire.presentation.resources.scale_aliases_blues
import com.pandulapeter.campfire.presentation.resources.scale_aliases_diminished
import com.pandulapeter.campfire.presentation.resources.scale_aliases_dorian_flat_2
import com.pandulapeter.campfire.presentation.resources.scale_aliases_dominant_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_aliases_dorian_sharp_4
import com.pandulapeter.campfire.presentation.resources.scale_aliases_double_harmonic
import com.pandulapeter.campfire.presentation.resources.scale_aliases_egyptian
import com.pandulapeter.campfire.presentation.resources.scale_aliases_hungarian_minor
import com.pandulapeter.campfire.presentation.resources.scale_aliases_in
import com.pandulapeter.campfire.presentation.resources.scale_aliases_ionian_sharp_5
import com.pandulapeter.campfire.presentation.resources.scale_aliases_locrian_natural_2
import com.pandulapeter.campfire.presentation.resources.scale_aliases_lydian_dominant
import com.pandulapeter.campfire.presentation.resources.scale_aliases_major
import com.pandulapeter.campfire.presentation.resources.scale_aliases_man_gong
import com.pandulapeter.campfire.presentation.resources.scale_aliases_melodic_minor
import com.pandulapeter.campfire.presentation.resources.scale_aliases_minor_6_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_aliases_mixolydian
import com.pandulapeter.campfire.presentation.resources.scale_aliases_mixolydian_flat_6
import com.pandulapeter.campfire.presentation.resources.scale_aliases_natural_minor
import com.pandulapeter.campfire.presentation.resources.scale_aliases_phrygian_dominant
import com.pandulapeter.campfire.presentation.resources.scale_aliases_prometheus
import com.pandulapeter.campfire.presentation.resources.scale_aliases_ritusen
import com.pandulapeter.campfire.presentation.resources.scale_aliases_tritone
import com.pandulapeter.campfire.presentation.resources.scale_aliases_ultralocrian
import com.pandulapeter.campfire.presentation.resources.scale_augmented
import com.pandulapeter.campfire.presentation.resources.scale_bebop_dominant
import com.pandulapeter.campfire.presentation.resources.scale_bebop_dorian
import com.pandulapeter.campfire.presentation.resources.scale_bebop_harmonic_minor
import com.pandulapeter.campfire.presentation.resources.scale_bebop_major
import com.pandulapeter.campfire.presentation.resources.scale_bebop_melodic_minor
import com.pandulapeter.campfire.presentation.resources.scale_blues
import com.pandulapeter.campfire.presentation.resources.scale_chinese
import com.pandulapeter.campfire.presentation.resources.scale_chromatic
import com.pandulapeter.campfire.presentation.resources.scale_diminished
import com.pandulapeter.campfire.presentation.resources.scale_dominant_diminished
import com.pandulapeter.campfire.presentation.resources.scale_dominant_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_dorian
import com.pandulapeter.campfire.presentation.resources.scale_dorian_flat_2
import com.pandulapeter.campfire.presentation.resources.scale_dorian_sharp_4
import com.pandulapeter.campfire.presentation.resources.scale_double_harmonic
import com.pandulapeter.campfire.presentation.resources.scale_egyptian
import com.pandulapeter.campfire.presentation.resources.scale_enigmatic
import com.pandulapeter.campfire.presentation.resources.scale_family_bebop
import com.pandulapeter.campfire.presentation.resources.scale_family_blues_and_hexatonic
import com.pandulapeter.campfire.presentation.resources.scale_family_harmonic_minor_modes
import com.pandulapeter.campfire.presentation.resources.scale_family_major_modes
import com.pandulapeter.campfire.presentation.resources.scale_family_melodic_minor_modes
import com.pandulapeter.campfire.presentation.resources.scale_family_minor_and_harmonic
import com.pandulapeter.campfire.presentation.resources.scale_family_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_family_symmetric
import com.pandulapeter.campfire.presentation.resources.scale_family_world
import com.pandulapeter.campfire.presentation.resources.scale_flamenco
import com.pandulapeter.campfire.presentation.resources.scale_harmonic_major
import com.pandulapeter.campfire.presentation.resources.scale_harmonic_minor
import com.pandulapeter.campfire.presentation.resources.scale_hirajoshi
import com.pandulapeter.campfire.presentation.resources.scale_hungarian_major
import com.pandulapeter.campfire.presentation.resources.scale_hungarian_minor
import com.pandulapeter.campfire.presentation.resources.scale_in
import com.pandulapeter.campfire.presentation.resources.scale_insen
import com.pandulapeter.campfire.presentation.resources.scale_ionian_sharp_5
import com.pandulapeter.campfire.presentation.resources.scale_iwato
import com.pandulapeter.campfire.presentation.resources.scale_kumoi
import com.pandulapeter.campfire.presentation.resources.scale_leading_whole_tone
import com.pandulapeter.campfire.presentation.resources.scale_locrian
import com.pandulapeter.campfire.presentation.resources.scale_locrian_natural_2
import com.pandulapeter.campfire.presentation.resources.scale_locrian_natural_6
import com.pandulapeter.campfire.presentation.resources.scale_lydian
import com.pandulapeter.campfire.presentation.resources.scale_lydian_augmented
import com.pandulapeter.campfire.presentation.resources.scale_lydian_dominant
import com.pandulapeter.campfire.presentation.resources.scale_lydian_minor
import com.pandulapeter.campfire.presentation.resources.scale_lydian_sharp_2
import com.pandulapeter.campfire.presentation.resources.scale_major
import com.pandulapeter.campfire.presentation.resources.scale_major_blues
import com.pandulapeter.campfire.presentation.resources.scale_major_hexatonic
import com.pandulapeter.campfire.presentation.resources.scale_major_locrian
import com.pandulapeter.campfire.presentation.resources.scale_major_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_man_gong
import com.pandulapeter.campfire.presentation.resources.scale_melodic_minor
import com.pandulapeter.campfire.presentation.resources.scale_minor_6_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_minor_hexatonic
import com.pandulapeter.campfire.presentation.resources.scale_minor_pentatonic
import com.pandulapeter.campfire.presentation.resources.scale_mixolydian
import com.pandulapeter.campfire.presentation.resources.scale_mixolydian_flat_6
import com.pandulapeter.campfire.presentation.resources.scale_natural_minor
import com.pandulapeter.campfire.presentation.resources.scale_neapolitan_major
import com.pandulapeter.campfire.presentation.resources.scale_neapolitan_minor
import com.pandulapeter.campfire.presentation.resources.scale_oriental
import com.pandulapeter.campfire.presentation.resources.scale_persian
import com.pandulapeter.campfire.presentation.resources.scale_phrygian
import com.pandulapeter.campfire.presentation.resources.scale_phrygian_dominant
import com.pandulapeter.campfire.presentation.resources.scale_prometheus
import com.pandulapeter.campfire.presentation.resources.scale_ritusen
import com.pandulapeter.campfire.presentation.resources.scale_spanish_eight_tone
import com.pandulapeter.campfire.presentation.resources.scale_tritone
import com.pandulapeter.campfire.presentation.resources.scale_ultralocrian
import com.pandulapeter.campfire.presentation.resources.scale_whole_tone
import org.jetbrains.compose.resources.StringResource

/**
 * The name of the scale with the catalogue's [id] (`Scale.id`), or null for an id this version has no name for, which
 * only a dialog saved by another version can name.
 */
internal fun scaleNameResource(id: String): StringResource? = when (id) {
    "major" -> Res.string.scale_major
    "dorian" -> Res.string.scale_dorian
    "phrygian" -> Res.string.scale_phrygian
    "lydian" -> Res.string.scale_lydian
    "mixolydian" -> Res.string.scale_mixolydian
    "natural_minor" -> Res.string.scale_natural_minor
    "locrian" -> Res.string.scale_locrian
    "harmonic_minor" -> Res.string.scale_harmonic_minor
    "locrian_natural_6" -> Res.string.scale_locrian_natural_6
    "ionian_sharp_5" -> Res.string.scale_ionian_sharp_5
    "dorian_sharp_4" -> Res.string.scale_dorian_sharp_4
    "phrygian_dominant" -> Res.string.scale_phrygian_dominant
    "lydian_sharp_2" -> Res.string.scale_lydian_sharp_2
    "ultralocrian" -> Res.string.scale_ultralocrian
    "melodic_minor" -> Res.string.scale_melodic_minor
    "dorian_flat_2" -> Res.string.scale_dorian_flat_2
    "lydian_augmented" -> Res.string.scale_lydian_augmented
    "lydian_dominant" -> Res.string.scale_lydian_dominant
    "mixolydian_flat_6" -> Res.string.scale_mixolydian_flat_6
    "locrian_natural_2" -> Res.string.scale_locrian_natural_2
    "altered" -> Res.string.scale_altered
    "harmonic_major" -> Res.string.scale_harmonic_major
    "double_harmonic" -> Res.string.scale_double_harmonic
    "hungarian_minor" -> Res.string.scale_hungarian_minor
    "hungarian_major" -> Res.string.scale_hungarian_major
    "neapolitan_major" -> Res.string.scale_neapolitan_major
    "neapolitan_minor" -> Res.string.scale_neapolitan_minor
    "major_pentatonic" -> Res.string.scale_major_pentatonic
    "minor_pentatonic" -> Res.string.scale_minor_pentatonic
    "minor_6_pentatonic" -> Res.string.scale_minor_6_pentatonic
    "dominant_pentatonic" -> Res.string.scale_dominant_pentatonic
    "egyptian" -> Res.string.scale_egyptian
    "man_gong" -> Res.string.scale_man_gong
    "ritusen" -> Res.string.scale_ritusen
    "hirajoshi" -> Res.string.scale_hirajoshi
    "in" -> Res.string.scale_in
    "insen" -> Res.string.scale_insen
    "iwato" -> Res.string.scale_iwato
    "kumoi" -> Res.string.scale_kumoi
    "chinese" -> Res.string.scale_chinese
    "blues" -> Res.string.scale_blues
    "major_blues" -> Res.string.scale_major_blues
    "major_hexatonic" -> Res.string.scale_major_hexatonic
    "minor_hexatonic" -> Res.string.scale_minor_hexatonic
    "prometheus" -> Res.string.scale_prometheus
    "tritone" -> Res.string.scale_tritone
    "whole_tone" -> Res.string.scale_whole_tone
    "augmented" -> Res.string.scale_augmented
    "diminished" -> Res.string.scale_diminished
    "dominant_diminished" -> Res.string.scale_dominant_diminished
    "chromatic" -> Res.string.scale_chromatic
    "bebop_dominant" -> Res.string.scale_bebop_dominant
    "bebop_major" -> Res.string.scale_bebop_major
    "bebop_dorian" -> Res.string.scale_bebop_dorian
    "bebop_melodic_minor" -> Res.string.scale_bebop_melodic_minor
    "bebop_harmonic_minor" -> Res.string.scale_bebop_harmonic_minor
    "persian" -> Res.string.scale_persian
    "enigmatic" -> Res.string.scale_enigmatic
    "oriental" -> Res.string.scale_oriental
    "major_locrian" -> Res.string.scale_major_locrian
    "lydian_minor" -> Res.string.scale_lydian_minor
    "leading_whole_tone" -> Res.string.scale_leading_whole_tone
    "flamenco" -> Res.string.scale_flamenco
    "spanish_eight_tone" -> Res.string.scale_spanish_eight_tone
    else -> null
}

/**
 * The other names the scale with the catalogue's [id] is known by, as one comma-separated list in the app's language
 * (the catalogue's `Scale.aliases` are the English ones), or null for a scale known by one name, or an id this version
 * has no names for.
 */
internal fun scaleAliasesResource(id: String): StringResource? = when (id) {
    "major" -> Res.string.scale_aliases_major
    "mixolydian" -> Res.string.scale_aliases_mixolydian
    "natural_minor" -> Res.string.scale_aliases_natural_minor
    "ionian_sharp_5" -> Res.string.scale_aliases_ionian_sharp_5
    "dorian_sharp_4" -> Res.string.scale_aliases_dorian_sharp_4
    "phrygian_dominant" -> Res.string.scale_aliases_phrygian_dominant
    "ultralocrian" -> Res.string.scale_aliases_ultralocrian
    "melodic_minor" -> Res.string.scale_aliases_melodic_minor
    "dorian_flat_2" -> Res.string.scale_aliases_dorian_flat_2
    "lydian_dominant" -> Res.string.scale_aliases_lydian_dominant
    "mixolydian_flat_6" -> Res.string.scale_aliases_mixolydian_flat_6
    "locrian_natural_2" -> Res.string.scale_aliases_locrian_natural_2
    "altered" -> Res.string.scale_aliases_altered
    "double_harmonic" -> Res.string.scale_aliases_double_harmonic
    "hungarian_minor" -> Res.string.scale_aliases_hungarian_minor
    "egyptian" -> Res.string.scale_aliases_egyptian
    "man_gong" -> Res.string.scale_aliases_man_gong
    "minor_6_pentatonic" -> Res.string.scale_aliases_minor_6_pentatonic
    "dominant_pentatonic" -> Res.string.scale_aliases_dominant_pentatonic
    "ritusen" -> Res.string.scale_aliases_ritusen
    "in" -> Res.string.scale_aliases_in
    "blues" -> Res.string.scale_aliases_blues
    "prometheus" -> Res.string.scale_aliases_prometheus
    "tritone" -> Res.string.scale_aliases_tritone
    "augmented" -> Res.string.scale_aliases_augmented
    "diminished" -> Res.string.scale_aliases_diminished
    else -> null
}

/** The name of a family of scales, the chip it is picked with. */
internal val ScaleFamily.label: StringResource
    get() = when (this) {
        ScaleFamily.MAJOR_MODES -> Res.string.scale_family_major_modes
        ScaleFamily.HARMONIC_MINOR_MODES -> Res.string.scale_family_harmonic_minor_modes
        ScaleFamily.MELODIC_MINOR_MODES -> Res.string.scale_family_melodic_minor_modes
        ScaleFamily.MINOR_AND_HARMONIC -> Res.string.scale_family_minor_and_harmonic
        ScaleFamily.PENTATONIC -> Res.string.scale_family_pentatonic
        ScaleFamily.BLUES_AND_HEXATONIC -> Res.string.scale_family_blues_and_hexatonic
        ScaleFamily.SYMMETRIC -> Res.string.scale_family_symmetric
        ScaleFamily.BEBOP -> Res.string.scale_family_bebop
        ScaleFamily.WORLD -> Res.string.scale_family_world
    }
