/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.presentation.ui.songView.title
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_scales_other
import com.pandulapeter.campfire.presentation.resources.song_details_scales_unheld
import com.pandulapeter.campfire.presentation.ui.chords.ScaleChip
import com.pandulapeter.campfire.presentation.ui.components.animatePlacement

/**
 * The song's scale suggestions as one line of chips in the dock, in the order the song's Scales section drew them (the
 * home group's untitled, then each other group and each chord worth a scale of its own under its title, "Over E7"), a
 * chorded part no scale holds said so in place of chips. A chip opens its scale's sheet, in read only mode too. Emits
 * nothing where the scale suggestions are off or the song has none, whose dock then has no scale row
 * ([DockRows.forSong]).
 *
 * A group none of whose sections is on the screen is faded back ([order]'s `groupsOnScreen`, [DIMMED_ALPHA]), its title
 * and chips alike, as the diagram strip fades the chords off it, the home group too; a faded chip still opens its sheet.
 * The groups on the screen are brought before the faded ones once the screen has settled ([order]'s `groupOrder`,
 * [dockScaleRowOrder]), gliding to their places, the home group kept first since nothing else names it.
 */
@Composable
internal fun DockScaleChips(content: DockContent, order: DockRowsOrder) {
    val suggestions = content.scaleSuggestions ?: return
    val groups = content.model.scaleGroups
    if (groups.isEmpty()) return
    val section = remember(groups) { RenderSection.Scales(groups) }
    val isGrouped = groups.size > 1
    val other = stringResource(Res.string.song_details_scales_other)
    val groupsOnScreen = order.groupsOnScreen
    val groupOrder = order.groupOrder
    val rows = remember(section, groupOrder) { dockScaleRowOrder(section.rows, groupOf = { it.groupIndex }, isHome = { groups[it].isHome }, onScreen = groupOrder) }
    val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
    rows.forEach { row ->
        key(row) {
            val alpha by animateFloatAsState(if (groupsOnScreen == null || row.groupIndex in groupsOnScreen) 1f else DIMMED_ALPHA)
            val rowModifier = { index: Int ->
                placement
                    .onSizeChanged { order.scaleWidths.record(row, index, it.width) }
                    .graphicsLayer { this.alpha = alpha }
            }
            val title = when {
                row.exception != null -> row.exception.title()
                isGrouped -> groups[row.groupIndex].takeIf { !it.isHome }?.title(other)
                else -> null
            }
            if (title != null) {
                Text(
                    modifier = rowModifier(-1).semantics { heading() },
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val scales = row.scalesOf(groups)
            if (scales.isEmpty()) {
                Text(
                    modifier = rowModifier(0),
                    text = stringResource(Res.string.song_details_scales_unheld),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            scales.forEachIndexed { index, scale ->
                ScaleChip(
                    modifier = rowModifier(index),
                    scale = scale,
                    rootName = row.rootNameOf(groups),
                    notation = suggestions.notation,
                    onScaleSelected = { scaleId, rootName ->
                        println("Dock: scale chip")
                        suggestions.onScaleClicked(scaleId, rootName, suggestions.capo)
                    },
                )
            }
        }
    }
}
