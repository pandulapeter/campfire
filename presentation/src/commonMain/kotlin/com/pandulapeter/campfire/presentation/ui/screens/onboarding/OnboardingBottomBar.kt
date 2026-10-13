/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.onboarding_next
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing

/**
 * Back, where there is a page to go back to, how far along the pages are, and the way on: Next, and on the last page
 * Start playing (Done where the onboarding was opened from Settings), which finishes it. Pinned under the page, since it is
 * what every page is finished with, and as wide as it is, so that the buttons stay near the content on a wide window.
 */
@Composable
internal fun OnboardingBottomBar(
    modifier: Modifier = Modifier,
    currentIndex: Int,
    count: Int,
    isLast: Boolean,
    isOpenedFromSettings: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) = Row(
    modifier = modifier.widthIn(max = ONBOARDING_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Faded rather than taken out, so that the indicator and Next never move; disabled, it takes no taps or focus.
        val canGoBack = currentIndex > 0
        val backAlpha by animateFloatAsState(if (canGoBack) 1f else 0f)
        TextButton(
            modifier = Modifier.alpha(backAlpha),
            enabled = canGoBack,
            onClick = onBack,
        ) { Text(stringResource(Res.string.back)) }
    }
    OnboardingPageIndicator(
        currentIndex = currentIndex,
        count = count,
    )
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Button(onClick = onNext) {
            AnimatedContent(
                targetState = isLast,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                contentAlignment = Alignment.Center,
            ) { isLast ->
                Text(
                    stringResource(
                        when {
                            !isLast -> Res.string.onboarding_next
                            isOpenedFromSettings -> Res.string.done
                            else -> Res.string.onboarding_start_playing
                        }
                    )
                )
            }
        }
    }
}
