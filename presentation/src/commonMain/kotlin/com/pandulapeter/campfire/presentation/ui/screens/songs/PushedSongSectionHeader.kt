/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeader
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeaderPlacement

/**
 * The outgoing header of the song list, drawn over the grid while the next one pushes it up. A composable of its own,
 * so that the push restarts nothing but it, and it only once per section: where it is and how far it has faded change
 * on every frame, and are read while it is laid out and drawn.
 */
@Composable
internal fun PushedSongSectionHeader(
    listState: LazyGridState,
    sectionIndex: SongSectionIndex,
    endPadding: Dp,
    appBarOverlap: () -> AppBarOverlap,
) {
    val pushed = pushedSectionHeader(listState, contentType = "header")
    val key by remember(pushed) { derivedStateOf { pushed.value?.key } }
    val header = key?.let(sectionIndex::headerForKey) ?: return
    SectionHeader(
        modifier = Modifier.pushedSectionHeaderPlacement(pushed).clearAndSetSemantics {},
        text = header.displayText(),
        // Pinned for as long as it is being pushed away: it keeps the width it had in the bar's place rather than
        // widening again as it leaves.
        state = { SectionHeaderState(visibleFraction = pushed.value?.visibleFraction ?: 0f, pinnedFraction = 1f) },
        endPadding = endPadding,
        onClick = null,
        contentOpacity = { (pushed.value?.visibleFraction ?: 0f) * appBarOverlap().coverage },
        pushedDistancePx = { pushed.value?.pushedDistance ?: 0 },
        appBarOverlap = appBarOverlap,
    )
}
