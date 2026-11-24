/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp

/**
 * One card of the deck next to the navigation chrome, inset from the navigation rail or bar rather than clipped, so
 * that the chrome stays visible beside it. While [isChromeDrawn], it draws the chrome's last recording under itself
 * where [NavigationChromeScaffold] rests it ([ChromeLayers]), so that the chrome rides this screen's parallax and scrim
 * under a card, and nothing moves as the one hands over to the other.
 *
 * A [Surface], so that it blocks touches from reaching the screen it covers during a transition. It reaches the window's
 * top, under the status bar: none of the top level screens has a top app bar, so each places itself against the top
 * inset its content padding carries ([KeyboardAwarePadding]), its fixed controls below it and what it scrolls going on
 * up under it, faded out there.
 */
@Composable
internal fun TopLevelScreenSurface(
    railWidth: () -> Dp,
    navigationBarHeight: () -> Dp,
    chromeLayers: ChromeLayers,
    isChromeDrawn: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val chromeRoom = ChromeRoomPadding(start = railWidth, bottom = navigationBarHeight)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (isChromeDrawn()) {
                    chromeLayers.outgoingChrome.draw(this)
                    chromeLayers.chrome.draw(this)
                }
            },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(chromeRoom)
                // The chrome covers the insets on its own edge, so nothing inside should apply them a second time.
                .consumeWindowInsets(chromeRoom),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
