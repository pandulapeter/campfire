/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_grid_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_grid_expand
import com.pandulapeter.campfire.presentation.resources.song_details_section_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_section_expand
import com.pandulapeter.campfire.presentation.resources.song_details_tab_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_tab_expand
import com.pandulapeter.campfire.presentation.ui.components.ExpandChevron
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind

/**
 * The app's fold chevron, named for what pressing it does to a tab, a grid, or a whole section where [kind] is null, or
 * by [description] where one is given.
 */
@Composable
internal fun FoldChevron(
    modifier: Modifier = Modifier,
    kind: FoldableKind?,
    isExpanded: Boolean,
    tint: Color,
    description: String? = null,
) = ExpandChevron(
    modifier = modifier,
    isExpanded = isExpanded,
    contentDescription = description ?: stringResource(
        when (kind) {
            FoldableKind.TAB -> if (isExpanded) Res.string.song_details_tab_collapse else Res.string.song_details_tab_expand
            FoldableKind.GRID -> if (isExpanded) Res.string.song_details_grid_collapse else Res.string.song_details_grid_expand
            null -> if (isExpanded) Res.string.song_details_section_collapse else Res.string.song_details_section_expand
        }
    ),
    tint = tint,
)
