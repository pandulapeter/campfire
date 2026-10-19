/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.presentation.ui.components.controls.SelectableChip
import com.pandulapeter.campfire.presentation.ui.scales.scaleTitle

/** A chip naming [scale] on [rootName] (a note in the standard notation) in [notation], handing its id and that root to [onScaleSelected]. */
@Composable
internal fun ScaleChip(
    modifier: Modifier = Modifier,
    scale: Scale,
    rootName: String,
    notation: ChordNotation,
    onScaleSelected: (scaleId: String, rootName: String) -> Unit,
) = SelectableChip(
    modifier = modifier,
    isSelected = false,
    role = Role.Button,
    onClick = { onScaleSelected(scale.id, rootName) },
) { contentColor ->
    Text(
        text = scaleTitle(scale, rootName, notation),
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
    )
}
