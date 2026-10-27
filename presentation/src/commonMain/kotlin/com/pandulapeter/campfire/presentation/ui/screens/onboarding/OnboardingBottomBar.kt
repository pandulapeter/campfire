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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.onboarding_next
import com.pandulapeter.campfire.presentation.resources.onboarding_skip
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Back, where there is a page to go back to, how far along the pages are, and the way on: Next, and on the last page
 * Start playing (Done where the onboarding was opened from Settings), which finishes it - or Next still while an import
 * waits behind the onboarding ([isImportWaiting]), since what finishing it leads to is that import's screen. Pinned under the page, since it is
 * what every page is finished with, and as wide as it is, so that the buttons stay near the content on a wide window;
 * [sideMargin] is the pages' own ([onboardingSideMargin]), so its edges line up with theirs.
 *
 * On the first page, which has nothing to go back to, Back's place holds the way out of the whole onboarding instead
 * ([onClose]): Skip on a first run, Close where it was opened from Settings. It is a tonal pill rather than a text
 * button, so that it reads as a choice of its own rather than as a step of the pages, and the two crossfade into each
 * other as the first page is left or come back to, in one slot, so that the dots and Next never move.
 *
 * Each slot is as wide as the widest of the buttons it can hold on any page, measured from all of them rather than from
 * the one on screen, so that the dots do not move as one label crossfades into another. The dots are centered between
 * the slots where both fit, and moved toward the narrower one where they do not ([onboardingDotsStart]). Where the slots
 * and the widest the dots can be cannot share the line at all (large text, a longer language), the dots go to a line of
 * their own above the buttons ([onboardingFooterIsStacked]), so that no label is ever broken, and travel between the
 * two when the window or the text size changes across the threshold.
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
    sideMargin: Dp,
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
    val motion = remember { FooterMotion() }
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    SubcomposeLayout(
        modifier = modifier
            .padding(horizontal = sideMargin)
            .widthIn(max = ONBOARDING_CONTENT_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        val gap = FOOTER_DOTS_GAP.roundToPx()
        val isStacked = onboardingFooterIsStacked(
            width = width,
            startSlotWidth = startSlotWidth,
            dotsMaxWidth = onboardingDotsMaxWidth(OnboardingStep.entries.size).roundToPx(),
            endSlotWidth = endSlotWidth,
            gap = gap,
        )
        val target = if (isStacked) 1f else 0f
        // The lookahead lays the footer out where it is headed, as PlacementAnimation does.
        val progress = if (isLookingAhead) target else motion.progressTowards(target, scope, spec)
        // On top of the dots' own 4dp padding, so that the last dot does not touch the button beside it, and shrunk
        // evenly on both sides where a phone has less room than that.
        val dotsStart = onboardingDotsStart(
            width = width,
            dotsWidth = dots.width,
            startSlotWidth = startSlotWidth,
            endSlotWidth = endSlotWidth,
            gap = gap,
        )
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
        }.single().measure(looseConstraints)
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
        }.single().measure(looseConstraints)
        val lineHeight = maxOf(start.height, dots.height, end.height)
        val stackedHeight = dots.height + gap + maxOf(start.height, end.height)
        val height = lerp(lineHeight, stackedHeight, progress)
        // The buttons keep their x and sink by what the footer gains; only the dots travel.
        val buttonsTop = height - lineHeight
        layout(width, height) {
            start.placeRelative(0, buttonsTop + (lineHeight - start.height) / 2)
            dots.placeRelative(lerp(dotsStart, (width - dots.width) / 2, progress), lerp((lineHeight - dots.height) / 2, 0, progress))
            end.placeRelative(width - end.width, buttonsTop + (lineHeight - end.height) / 2)
        }
    }
}

/**
 * How far the footer is on its way to the stacked arrangement. Not a state: only the [Animatable] is read, created at
 * the first measure's target so that opening the onboarding moves nothing.
 */
private class FooterMotion {
    private var progress: Animatable<Float, AnimationVector1D>? = null

    fun progressTowards(target: Float, scope: CoroutineScope, spec: FiniteAnimationSpec<Float>): Float {
        val animatable = progress ?: Animatable(target).also { progress = it }
        if (animatable.targetValue != target) {
            scope.launch { animatable.animateTo(target, spec) }
        }
        return animatable.value
    }
}

private enum class FooterSlot {
    START_CANDIDATES,
    END_CANDIDATES,
    DOTS,
    START,
    END,
}

/**
 * The room the footer keeps between the dots and each button where it has it (see [onboardingDotsStart]), and between
 * the dots and the buttons under them where they are stacked.
 */
private val FOOTER_DOTS_GAP = 8.dp

/** Material's small button padding: the default's 24dp sides would leave Start playing no room on a 360dp phone. */
private val FOOTER_BUTTON_PADDING = PaddingValues(
    start = 16.dp,
    top = ButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 16.dp,
    bottom = ButtonDefaults.ContentPadding.calculateBottomPadding(),
)
