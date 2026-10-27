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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_progress
import com.pandulapeter.campfire.presentation.ui.components.controls.pagerSelection

/**
 * A dot for every step shown, the current one stretched into a pill, read out as "Step 2 of 4". The pill follows the
 * pager's [position] (its page plus its offset) frame by frame, one dot shrinking as the next one grows, so that it
 * moves with a swipe or a predictive back rather than catching up once the page has changed; the two always add up to
 * the same width, so the row never changes size under the buttons. Every step has a dot of its own whether or not it
 * is shown, keyed by the step rather than by its place, so that a step an answer takes away shrinks out from between its
 * neighbours, and one it brings back grows in there, rather than the last dot coming and going.
 */
@Composable
internal fun OnboardingPageIndicator(
    modifier: Modifier = Modifier,
    steps: List<OnboardingStep>,
    currentStep: OnboardingStep,
    position: () -> Float,
) {
    val description = stringResource(Res.string.onboarding_progress, steps.count { it < currentStep } + 1, steps.size)
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep.entries.forEach { step ->
            AnimatedVisibility(
                visible = step in steps,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                val index = steps.indexOf(step)
                // Read while laying out and drawing rather than while composing, so a turn moves the pill without
                // recomposing the footer on every frame. A dot on its way out is not selected.
                val selection = { if (index < 0) 0f else pagerSelection(position(), index) }
                // The gap is the dot's own padding rather than the row's spacing, so that it leaves with the dot.
                Box(
                    modifier = Modifier
                        .padding(horizontal = DOT_PADDING)
                        .layout { measurable, _ ->
                            val width = (DOT_SIZE + (PILL_WIDTH - DOT_SIZE) * selection()).roundToPx()
                            val placeable = measurable.measure(Constraints.fixed(width, DOT_SIZE.roundToPx()))
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .drawBehind {
                            drawRoundRect(
                                color = lerp(unselectedColor, selectedColor, selection()),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        },
                )
            }
        }
    }
}

/**
 * The widest the dots of [count] steps can be: every dot shown and one of them the pill, each with its padding. What
 * the footer decides its arrangement from, since the dots' own width changes while a dot grows in or shrinks out.
 */
internal fun onboardingDotsMaxWidth(count: Int) = (DOT_SIZE + DOT_PADDING * 2) * (count - 1) + PILL_WIDTH + DOT_PADDING * 2

private val DOT_SIZE = 8.dp

private val DOT_PADDING = 4.dp

private val PILL_WIDTH = 24.dp
