/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The one sign of work on the library outside the screen that started it, laid along the top edge of the window over
 * everything else and every inset: an import, files read into a review, the selection's edits, or a sync run, whichever
 * started it ([workProgress] decides which). Once shown, it stays for as long as the work runs and for at least
 * [MINIMUM_VISIBLE_DURATION], so that work of a moment does not flash it. Work that can say how far it has got fills it,
 * with the [WorkProgressPulse] running on past the fill so that a slow transfer still moves; work that cannot runs the
 * indeterminate line, and the two crossfade as the work changes. Without a track, so that nothing covers what is under
 * it but the line itself.
 */
@Composable
internal fun WorkProgressBar(
    modifier: Modifier = Modifier,
    progress: WorkProgress?,
) {
    val isRunning = progress != null
    val isWorthShowing = progress?.isWorthShowing == true
    var isVisible by remember { mutableStateOf(false) }
    val shownAt = remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    // The last progress worth drawing is kept while the bar fades out, so that work that ends leaves the bar where it got
    // to, and while a sync run lists again between passes.
    var shown by remember { mutableStateOf(WorkProgress(fraction = null)) }
    progress?.takeIf { it.isWorthShowing }?.let { shown = it }
    // And the last fill, for the determinate line fading out as the work turns into one that cannot say.
    var lastFraction by remember { mutableFloatStateOf(0f) }
    shown.fraction?.let { lastFraction = it }
    LaunchedEffect(isRunning, isWorthShowing) {
        when {
            isWorthShowing -> if (!isVisible) {
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
        Crossfade(targetState = shown.fraction != null, label = "workProgressKind") { isDeterminate ->
            if (isDeterminate) {
                DeterminateLine(fraction = shown.fraction ?: lastFraction)
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = Color.Transparent,
                    gapSize = 0.dp,
                )
            }
        }
    }
}

/** The fill of work that says how far it has got, with the pulse running on past its end. */
@Composable
private fun DeterminateLine(fraction: Float) {
    // Animated, or work that finishes several files between frames would make the bar jump. Composed afresh each time the
    // line turns determinate, so that a new piece of work fills from where it starts rather than from where the last
    // one ended.
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "workProgress")
    Box {
        WorkProgressPulse(
            modifier = Modifier.matchParentSize(),
            fraction = { animatedFraction },
        )
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = Color.Transparent,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private val MINIMUM_VISIBLE_DURATION = 500.milliseconds
