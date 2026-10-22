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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The hint that a sync run is moving files, laid along the top edge of the window over everything else, whatever the
 * run: one the user started, the one an edit asks for, or one another device's change brought. A run that only lists
 * both sides and finds nothing to transfer, as the one on every return to the front mostly does, never shows it; once
 * shown, it stays for the rest of the run (a second pass lists again) and for at least [MINIMUM_VISIBLE_DURATION], so
 * a run of a single file does not flash it. Without a track, so that between runs and at its unfilled end nothing
 * covers what is under it.
 */
@Composable
internal fun SyncProgressBar(
    modifier: Modifier = Modifier,
    progress: SyncProgress?,
) {
    val isRunning = progress != null
    val hasFilesToTransfer = progress?.fraction != null
    var isVisible by remember { mutableStateOf(false) }
    val shownAt = remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    // The last fraction is kept while the bar fades out, so that a run that ends leaves the bar where it got to.
    var shownFraction by remember { mutableFloatStateOf(0f) }
    progress?.fraction?.let { shownFraction = it }
    LaunchedEffect(isRunning, hasFilesToTransfer) {
        when {
            hasFilesToTransfer -> if (!isVisible) {
                isVisible = true
                shownAt.value = TimeSource.Monotonic.markNow()
            }
            !isRunning && isVisible -> {
                delay(MINIMUM_VISIBLE_DURATION - shownAt.value.elapsedNow())
                isVisible = false
            }
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Animated, or a run that finishes several files between frames would make the bar jump.
        val fraction by animateFloatAsState(targetValue = shownFraction, label = "syncProgress")
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = Color.Transparent,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private val MINIMUM_VISIBLE_DURATION = 500.milliseconds
