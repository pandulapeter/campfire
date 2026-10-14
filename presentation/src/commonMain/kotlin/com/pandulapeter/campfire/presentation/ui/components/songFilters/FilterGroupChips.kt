/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.controls.CONTROLS_PADDING
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_filters_show_all
import com.pandulapeter.campfire.presentation.resources.songs_filters_show_less

/** A part of a filter group that comes and goes with the whole group, as one child of [FilterGroupsLayout]. */
@Composable
internal fun FilterGroupPart(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    content()
}

/**
 * The chips of a filter group, cut down to the room [FilterGroupsLayout] gives them, with the "Show all" that opens
 * the rest under them. That one stays under the chips, since what it asks about is the list it is at the end of - and
 * unlike the clearing of the filter it comes and goes with the room the chips have rather than with what is selected.
 */
@Composable
internal fun <T : Any> FilterGroupChips(
    lookaheadScope: LookaheadScope,
    isVisible: Boolean,
    items: List<T>,
    key: (T) -> Any,
    isExpanded: Boolean,
    isPinned: List<Boolean>,
    onExpandedChanged: (Boolean) -> Unit,
    chip: @Composable (T) -> Unit,
) = FilterGroupPart(
    modifier = Modifier.layoutId(if (isExpanded) FilterSlot.EXPANDED_CHIPS else FilterSlot.CHIPS),
    isVisible = isVisible,
) {
    CollapsibleChipFlow(
        items = items,
        key = key,
        isExpanded = isExpanded,
        isPinned = isPinned,
        lookaheadScope = lookaheadScope,
        horizontalPadding = CONTROLS_PADDING,
        gap = CHIP_GAP,
        toggle = {
            TextButton(
                modifier = Modifier.padding(horizontal = CONTROLS_PADDING - BUTTON_INSET),
                onClick = { onExpandedChanged(!isExpanded) },
            ) {
                // The button rides the group's edge while it opens or closes, so its label turns into the other one
                // where it is rather than the button going and coming back.
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                ) { expanded ->
                    Text(
                        text = if (expanded) {
                            stringResource(Res.string.songs_filters_show_less)
                        } else {
                            stringResource(Res.string.songs_filters_show_all, items.size)
                        }
                    )
                }
            }
        },
        chip = chip,
    )
}
