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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_palette
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * The mark at the top of the onboarding, one for every page rather than one on each: the pages keep only an empty place
 * for it ([ONBOARDING_MARK_SIZE]), and the screen draws it over the pager, at the place of the page shown or between the
 * places of the two pages a turn is between ([frame]), so that the circle stays put while the pages slide under it and
 * only moves up or down where their places differ, its icon shrinking out as the next one grows in. All of it follows
 * the pager's own position, so that a swipe or a predictive back gesture held halfway holds the mark halfway as well.
 */
@Composable
internal fun OnboardingMark(
    modifier: Modifier = Modifier,
    frame: OnboardingMarkFrame,
) = Box(
    modifier = modifier
        .size(ONBOARDING_MARK_SIZE)
        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
) {
    // The icon the turn leaves shrinks away over most of its first half and the next one grows in over most of its
    // second, overlapping a little in the middle, so that the circle is never empty for long and the two icons are never
    // both clearly there.
    MarkIcon(icon = frame.from.markIcon, presence = 1f - frame.fraction / ICON_HANDOVER)
    if (frame.to != frame.from) MarkIcon(icon = frame.to.markIcon, presence = (frame.fraction - 1f + ICON_HANDOVER) / ICON_HANDOVER)
}

@Composable
private fun MarkIcon(
    icon: DrawableResource,
    presence: Float,
) {
    val shown = presence.coerceIn(0f, 1f)
    if (shown > 0f) {
        Icon(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    alpha = shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = 0.6f + 0.4f * shown
                },
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Where the mark is: between the page [from] and the page [to], [fraction] of the way, [from] and [to] being the same
 * step on a page that is not being turned.
 */
internal data class OnboardingMarkFrame(
    val from: OnboardingStep,
    val to: OnboardingStep,
    val fraction: Float,
)

/** The mark's frame at the pager's [position] (its page plus its offset) among [steps], null while there are none. */
internal fun onboardingMarkFrame(steps: List<OnboardingStep>, position: Float): OnboardingMarkFrame? {
    if (steps.isEmpty()) return null
    val clamped = position.coerceIn(0f, steps.lastIndex.toFloat())
    val index = clamped.toInt()
    val fraction = clamped - index
    val from = steps[index]
    val to = steps.getOrNull(index + 1)?.takeIf { fraction > 0f } ?: from
    return OnboardingMarkFrame(from = from, to = to, fraction = if (to == from) 0f else fraction)
}

/** The mark's top at [frame], from the tops of the pages' places for it ([tops]), null until the first is known. */
internal fun onboardingMarkTop(frame: OnboardingMarkFrame, tops: Map<OnboardingStep, Float>): Float? {
    val from = tops[frame.from] ?: tops[frame.to] ?: return null
    val to = tops[frame.to] ?: from
    return lerp(from, to, frame.fraction)
}

/**
 * How far the pages under the mark at [frame] are scrolled, from how far each one is ([scrolled]): what the mark's own
 * top edge fade is as strong as, so that it leaves under the bar with the page it sits on rather than being cut off there.
 */
internal fun onboardingMarkScrolled(frame: OnboardingMarkFrame, scrolled: (OnboardingStep) -> Int) =
    lerp(scrolled(frame.from).toFloat(), scrolled(frame.to).toFloat(), frame.fraction).roundToInt()

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

private val OnboardingStep.markIcon
    get() = when (this) {
        OnboardingStep.WELCOME -> Res.drawable.ic_campfire
        OnboardingStep.LOOK -> Res.drawable.ic_palette
        OnboardingStep.INSTRUMENT -> Res.drawable.ic_tuner
        OnboardingStep.FEATURES -> Res.drawable.ic_tune
        OnboardingStep.NOTATION -> Res.drawable.ic_songs
        OnboardingStep.SYNC -> Res.drawable.ic_cloud
    }

/** The share of a turn each icon takes to leave or arrive in. */
private const val ICON_HANDOVER = 0.6f

/** The size of the mark, and of the empty place a page keeps for it. */
internal val ONBOARDING_MARK_SIZE = 72.dp
