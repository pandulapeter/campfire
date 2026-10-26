/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.scales.ScaleDiagram
import com.pandulapeter.campfire.presentation.ui.scales.ScaleDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * One scale of the Scales tab: its [title], the scale in its key ("C Dorian", as the Chords tab names each chord in the
 * key), in the chords' accent color over a small diagram of it on the player's instrument, without labels, on a card
 * that opens the scale sheet. A tuning or an instrument picked on the tab crossfades the diagram into the new one, and
 * the tab's capo slides the veil over the frets below it.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ScaleLibraryCell(
    modifier: Modifier = Modifier,
    title: String,
    geometry: ScaleDiagramGeometry,
    description: String,
    onClick: () -> Unit,
) = Surface(
    // The description is set here, without clearing, and the content cleared below: a clearing node here would also clear
    // the click action and the button role the clickable Surface adds after this modifier.
    modifier = modifier.semantics { contentDescription = description },
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LocalSecondAccentColor.current,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        updateTransition(targetState = geometry, label = "scaleLibraryDiagram").Crossfade(
            // A keyboard is taller than a neck, so the cell's height follows the fade rather than jumping at its end.
            modifier = Modifier.animateContentSize(),
            // A capo moved keeps the diagram, whose veil slides to the new fret, rather than fading in another one.
            contentKey = { it.withoutCapo() },
        ) { shown ->
            ScaleDiagram(
                modifier = Modifier
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    .height(if (shown is ScaleDiagramGeometry.Keyboard) KEYBOARD_HEIGHT else NECK_HEIGHT),
                geometry = shown,
                labels = null,
                description = description,
            )
        }
    }
}

/** How narrow a column of the Scales tab gets: two to a phone held upright. */
internal val SCALE_CELL_MIN_WIDTH = 160.dp

private val NECK_HEIGHT = 52.dp
private val KEYBOARD_HEIGHT = 64.dp

/** [this] with no capo on it: what decides whether a change is another diagram. */
private fun ScaleDiagramGeometry.withoutCapo() = if (this is ScaleDiagramGeometry.Fretboard) copy(capo = 0) else this
