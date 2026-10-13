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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.onboarding_next
import com.pandulapeter.campfire.presentation.resources.onboarding_skip
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing

/**
 * Back, where there is a page to go back to, how far along the pages are, and the way on: Next, and on the last page
 * Start playing (Done where the onboarding was opened from Settings), which finishes it - or Next still while an import
 * waits behind the onboarding ([isImportWaiting]), since what finishing it leads to is that import's screen. Pinned under the page, since it is
 * what every page is finished with, and as wide as it is, so that the buttons stay near the content on a wide window.
 *
 * On the first page, which has nothing to go back to, Back's place holds the way out of the whole onboarding instead
 * ([onClose]): Skip on a first run, Close where it was opened from Settings. It is a tonal pill rather than a text
 * button, so that it reads as a choice of its own rather than as a step of the pages, and the two crossfade into each
 * other as the first page is left or come back to, in one slot, so that the dots and Next never move.
 *
 * Each slot is as wide as the widest of the buttons it can hold on any page, measured from all of them rather than from
 * the one on screen, so that the dots do not move as one label crossfades into another. The dots are centered between
 * the slots where both fit, and moved toward the narrower one where they do not ([onboardingDotsStart]), which is what
 * keeps Start playing on one line on a 360dp phone with every page shown.
 */
@Composable
internal fun OnboardingBottomBar(
    modifier: Modifier = Modifier,
    steps: List<OnboardingStep>,
    currentStep: OnboardingStep,
    pagerPosition: () -> Float,
    isLast: Boolean,
    isOpenedFromSettings: Boolean,
    isImportWaiting: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
) {
    val backLabel = stringResource(Res.string.back)
    val closeLabel = stringResource(if (isOpenedFromSettings) Res.string.close else Res.string.onboarding_skip)
    val nextLabel = stringResource(Res.string.onboarding_next)
    val lastLabel = stringResource(
        when {
            isImportWaiting -> Res.string.onboarding_next
            isOpenedFromSettings -> Res.string.done
            else -> Res.string.onboarding_start_playing
        },
    )
    SubcomposeLayout(
        modifier = modifier.widthIn(max = ONBOARDING_CONTENT_WIDTH).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) { constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Measured and never placed, so they are neither drawn nor touched; their semantics are cleared all the same.
        val startSlotWidth = subcompose(FooterSlot.START_CANDIDATES) {
            TextButton(onClick = {}, modifier = Modifier.clearAndSetSemantics {}) { Text(backLabel) }
            FilledTonalButton(onClick = {}, modifier = Modifier.clearAndSetSemantics {}, contentPadding = FOOTER_BUTTON_PADDING) { Text(closeLabel) }
        }.maxOf { it.measure(looseConstraints).width }
        val endSlotWidth = subcompose(FooterSlot.END_CANDIDATES) {
            Button(onClick = {}, modifier = Modifier.clearAndSetSemantics {}, contentPadding = FOOTER_BUTTON_PADDING) { Text(nextLabel) }
            Button(onClick = {}, modifier = Modifier.clearAndSetSemantics {}, contentPadding = FOOTER_BUTTON_PADDING) { Text(lastLabel) }
        }.maxOf { it.measure(looseConstraints).width }
        // Measured on every pass, since the dots animate: the pill stretching, and a dot growing in or shrinking out.
        val dots = subcompose(FooterSlot.DOTS) { OnboardingPageIndicator(steps = steps, currentStep = currentStep, position = pagerPosition) }.single().measure(looseConstraints)
        val width = constraints.maxWidth
        // No gap of its own: the dots' padding already keeps them apart from the buttons.
        val dotsStart = onboardingDotsStart(width = width, dotsWidth = dots.width, startSlotWidth = startSlotWidth, endSlotWidth = endSlotWidth, gap = 0)
        val start = subcompose(FooterSlot.START) {
            AnimatedContent(
                targetState = previousOnboardingStep(currentStep, steps) != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                contentAlignment = Alignment.CenterStart,
            ) { canGoBack ->
                if (canGoBack) {
                    TextButton(onClick = onBack) { Text(backLabel) }
                } else {
                    FilledTonalButton(onClick = onClose, contentPadding = FOOTER_BUTTON_PADDING) { Text(closeLabel) }
                }
            }
        }.single().measure(looseConstraints.copy(maxWidth = dotsStart.coerceAtLeast(0)))
        val end = subcompose(FooterSlot.END) {
            Button(onClick = onNext, contentPadding = FOOTER_BUTTON_PADDING) {
                // Keyed on the label rather than on the page, so that an import arriving or settling on the last page
                // crossfades it too, and a last page that still says Next does not fade it into itself.
                AnimatedContent(
                    targetState = if (isLast) lastLabel else nextLabel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                    contentAlignment = Alignment.Center,
                ) { label ->
                    Text(label)
                }
            }
        }.single().measure(looseConstraints.copy(maxWidth = (width - dotsStart - dots.width).coerceAtLeast(0)))
        val height = maxOf(start.height, dots.height, end.height)
        layout(width, height) {
            start.placeRelative(0, (height - start.height) / 2)
            dots.placeRelative(dotsStart, (height - dots.height) / 2)
            end.placeRelative(width - end.width, (height - end.height) / 2)
        }
    }
}

private enum class FooterSlot {
    START_CANDIDATES,
    END_CANDIDATES,
    DOTS,
    START,
    END,
}

/** Material's small button padding: the default's 24dp sides would leave Start playing no room on a 360dp phone. */
private val FOOTER_BUTTON_PADDING = PaddingValues(
    start = 16.dp,
    top = ButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 16.dp,
    bottom = ButtonDefaults.ContentPadding.calculateBottomPadding(),
)
