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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_no_scales
import com.pandulapeter.campfire.presentation.resources.song_details_scales_other
import com.pandulapeter.campfire.presentation.resources.song_details_scales_unheld
import com.pandulapeter.campfire.presentation.ui.chords.ScaleChip

/**
 * The song's scale suggestions as one line of chips in the dock, in the order the song's Scales section drew them (the
 * home group's untitled, then each other group and each chord worth a scale of its own under its title, "Over E7"), a
 * chorded part no scale holds said so in place of chips. A chip opens its scale's sheet, in read only mode too. A song
 * with none (a songbook and a song of too many chords among them, whose scales are never worked out) says so, since the
 * row is the screen's rather than the song's. Emits nothing where the scale suggestions are off. The row moves only
 * under the reader's finger, never by itself.
 */
@Composable
internal fun DockScaleChips(content: DockContent) {
    val suggestions = content.scaleSuggestions ?: return
    val groups = content.model.scaleGroups
    if (groups.isEmpty()) {
        Text(
            text = stringResource(Res.string.song_details_dock_no_scales),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val section = remember(groups) { RenderSection.Scales(groups) }
    val isGrouped = groups.size > 1
    val other = stringResource(Res.string.song_details_scales_other)
    section.rows.forEach { row ->
        val title = when {
            row.exception != null -> row.exception.title()
            isGrouped -> groups[row.groupIndex].takeIf { !it.isHome }?.title(other)
            else -> null
        }
        if (title != null) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val scales = row.scalesOf(groups)
        if (scales.isEmpty()) {
            Text(
                text = stringResource(Res.string.song_details_scales_unheld),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        scales.forEach { scale ->
            ScaleChip(
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
