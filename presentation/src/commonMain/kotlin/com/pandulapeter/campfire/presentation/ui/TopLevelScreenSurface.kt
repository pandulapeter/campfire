/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.components.WindowSize

/**
 * One card of the deck next to the navigation chrome, inset from the navigation rail or bar rather than clipped, so
 * that the chrome stays visible beside it. Given a [chrome], it draws that under itself where
 * [NavigationChromeScaffold] places the shared one, so that nothing moves as the one hands over to the other.
 *
 * A [Surface], so that it blocks touches from reaching the screen it covers during a transition, and so that it keeps
 * its screen below the status bar: none of the top level screens has a top app bar of its own to do that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopLevelScreenSurface(
    scrim: NavigationScrim,
    windowSize: WindowSize,
    railWidth: Dp,
    navigationBarHeight: Dp,
    chrome: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    val scrimCoverage = rememberScrimCoverage(scrim)
    val scrimColor = MaterialTheme.colorScheme.scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .coveredScreenScrim(scrimColor) { scrimCoverage.value },
    ) {
        if (chrome != null) {
            Box(
                modifier = Modifier.align(if (windowSize.usesNavigationRail) Alignment.TopStart else Alignment.BottomStart),
            ) {
                chrome()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = railWidth, bottom = navigationBarHeight)
                // The chrome covers the insets on its own edge, so nothing inside should apply them a second time.
                .consumeWindowInsets(PaddingValues(start = railWidth, bottom = navigationBarHeight)),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Top).withMinTopEdge)) {
                content()
            }
        }
    }
}
