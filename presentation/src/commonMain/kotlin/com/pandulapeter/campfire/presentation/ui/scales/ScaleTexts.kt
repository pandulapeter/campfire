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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scales_diagram
import com.pandulapeter.campfire.presentation.resources.scales_sheet_title
import com.pandulapeter.campfire.presentation.resources.scales_steps_augmented
import com.pandulapeter.campfire.presentation.resources.scales_steps_half
import com.pandulapeter.campfire.presentation.resources.scales_steps_whole
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName

/** The name of [scale], or its id where this version has none for it. */
@Composable
internal fun scaleName(scale: Scale) = scaleNameResource(scale.id)?.let { stringResource(it) } ?: scale.id

/**
 * The other names of [scale] in the app's language, comma-separated, or its English ones where this version has no
 * translation, or null for a scale known by one name.
 */
@Composable
internal fun scaleAliases(scale: Scale): String? =
    scaleAliasesResource(scale.id)?.let { stringResource(it) } ?: scale.aliases.joinToString(", ").ifEmpty { null }

/** [scale] in the key of [rootName], a note in the standard notation, named in [notation]: "C Dorian". */
@Composable
internal fun scaleTitle(scale: Scale, rootName: String, notation: ChordNotation) =
    stringResource(Res.string.scales_sheet_title, shownChordName(rootName, notation), scaleName(scale))

/** What a screen reader says of a diagram of [scale] on [rootName]: the scale, then its notes. */
@Composable
internal fun scaleDiagramDescription(scale: Scale, rootName: String, notation: ChordNotation) = stringResource(
    Res.string.scales_diagram,
    scaleTitle(scale, rootName, notation),
    shownScaleNoteNames(scale, rootName, notation).joinToString(" "),
)

/** The steps between the notes of [scale], the last closing the octave, as a chart writes them: `W W H W W W H`. */
@Composable
internal fun scaleStepsText(scale: Scale): String {
    val whole = stringResource(Res.string.scales_steps_whole)
    val half = stringResource(Res.string.scales_steps_half)
    val augmented = stringResource(Res.string.scales_steps_augmented)
    return Scales.steps(scale).joinToString(" ") { step ->
        when (step) {
            1 -> half
            2 -> whole
            3 -> augmented
            else -> step.toString()
        }
    }
}
