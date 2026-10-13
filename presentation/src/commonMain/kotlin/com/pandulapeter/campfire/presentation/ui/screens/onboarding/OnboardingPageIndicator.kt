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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_progress

/**
 * A dot for every step shown, the current one stretched into a pill, read out as "Step 2 of 4". Every step has a dot of
 * its own whether or not it is shown, keyed by the step rather than by its place, so that a step an answer takes away
 * shrinks out from between its neighbours, and one it brings back grows in there, rather than the last dot coming and
 * going and the pill jumping to another one.
 */
@Composable
internal fun OnboardingPageIndicator(
    modifier: Modifier = Modifier,
    steps: List<OnboardingStep>,
    currentStep: OnboardingStep,
) {
    val description = stringResource(Res.string.onboarding_progress, steps.count { it < currentStep } + 1, steps.size)
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
                val isCurrent = step == currentStep
                val width by animateDpAsState(if (isCurrent) 24.dp else 8.dp)
                val color by animateColorAsState(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                // The gap is the dot's own padding rather than the row's spacing, so that it leaves with the dot.
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .background(color, CircleShape),
                )
            }
        }
    }
}
