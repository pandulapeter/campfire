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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.songs_selection_done
import org.jetbrains.compose.resources.painterResource

/**
 * What ends the selection mode, at the bottom end of the list where the setlist reorder mode has its own: "Done", and
 * after it how many songs are ticked, on a small pill in the button's content color that scales in with the first tick
 * and out with the last, its number rolling up or down as it changes. It is read out as one sentence, the count in it.
 */
@Composable
internal fun SongSelectionDoneButton(
    modifier: Modifier = Modifier,
    count: Int,
    onClick: () -> Unit,
) {
    val description = pluralTextResource(Res.plurals.songs_selection_done, count, count.toString())
    val containerColor = FloatingActionButtonDefaults.containerColor
    // The last count is kept while the pill leaves with the last untick, rather than a zero being shown on its way out.
    var shownCount by remember { mutableIntStateOf(count) }
    if (count > 0) shownCount = count
    ExtendedFloatingActionButton(
        modifier = modifier.semantics { contentDescription = description },
        containerColor = containerColor,
        onClick = onClick,
        // The shadow is drawn around the button by FloatingControlVisibility, which fades it with the button.
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        icon = { Icon(painterResource(Res.drawable.ic_check), contentDescription = null) },
        text = {
            Row(
                modifier = Modifier.clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(Res.string.done))
                AnimatedVisibility(
                    visible = count > 0,
                    enter = fadeIn() + scaleIn() + expandHorizontally(),
                    exit = fadeOut() + scaleOut() + shrinkHorizontally(),
                ) {
                    SelectionCount(
                        modifier = Modifier.padding(start = 8.dp),
                        count = shownCount,
                        textColor = containerColor,
                    )
                }
            }
        },
    )
}

@Composable
private fun SelectionCount(
    modifier: Modifier = Modifier,
    count: Int,
    textColor: Color,
) = Box(
    modifier = modifier
        .background(color = LocalContentColor.current, shape = CircleShape)
        .defaultMinSize(minWidth = COUNT_PILL_HEIGHT, minHeight = COUNT_PILL_HEIGHT)
        .padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically { it * direction } + fadeIn()) togetherWith (slideOutVertically { -it * direction } + fadeOut())
        },
    ) { shownCount ->
        Text(
            text = shownCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

/** As tall as a line of the label it stands beside, so that the button keeps Material's height. */
private val COUNT_PILL_HEIGHT = 20.dp
