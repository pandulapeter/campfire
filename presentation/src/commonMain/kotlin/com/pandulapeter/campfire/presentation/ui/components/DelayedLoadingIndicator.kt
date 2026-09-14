/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A loading indicator for work that is local and nearly always over before anybody could see it: a song file read
 * from the device's own storage. It stays out of sight until [LOADING_INDICATOR_DELAY] has passed while it is still in
 * the composition, so a read that finishes in time goes from nothing straight to its content, rather than flashing
 * an indicator for the few frames it takes. The ones that are still there after it - a large library being scanned
 * on a slow device, the browser's storage waking up - fade in the way they otherwise would have.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_INDICATOR_DELAY)
        isVisible = true
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ContainedLoadingIndicator()
    }
}

private val LOADING_INDICATOR_DELAY = 300.milliseconds
