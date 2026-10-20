/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.sync.SyncProgress

/**
 * The hint that a sync run is going, laid along the top edge of the window over everything else, whatever the run: one
 * the user started, the one an edit asks for, or one another device's change brought. Without a track, so that between
 * runs and at its unfilled end nothing covers what is under it.
 */
@Composable
internal fun SyncProgressBar(
    modifier: Modifier = Modifier,
    progress: SyncProgress?,
) {
    // The last progress is kept while the bar fades out, so that a run that ends leaves the bar where it got to rather
    // than turning it back into the spinning one on its way out.
    var shownProgress by remember { mutableStateOf(progress) }
    if (progress != null) shownProgress = progress
    AnimatedVisibility(
        visible = progress != null,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Crossfade(targetState = shownProgress?.fraction == null, label = "syncProgressKind") { isPreparing ->
            if (isPreparing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                )
            } else {
                // Animated, or a run that finishes several files between frames would make the bar jump.
                val fraction by animateFloatAsState(targetValue = shownProgress?.fraction ?: 0f, label = "syncProgress")
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
