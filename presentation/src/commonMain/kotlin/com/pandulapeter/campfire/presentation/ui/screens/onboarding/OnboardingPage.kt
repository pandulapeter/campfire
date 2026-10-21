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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll

/**
 * One step of the onboarding: a place for the mark, a question and a line saying why it is asked, centered, and the
 * answers under them. The mark itself is the screen's, drawn over the place the page keeps for it ([OnboardingMark]).
 * While the page fits the room the screen's bars leave it, it sits a little above the middle of it, and the room it has
 * to spare goes into wider gaps where there is plenty ([ONBOARDING_ROOMY_MIN_HEIGHT]); it scrolls while it does not fit -
 * a phone on its side, the smallest phone, or a large font - its edges fading into the bars as everywhere else. It is
 * never wider than a comfortable line of text, or than every theme color in one row, so that a tablet or a desktop
 * window shows a column in the middle rather than cards stretched across the screen. While the pages turn, the question
 * and the answers move as two layers ([onboardingHeaderMotion], [onboardingAnswersMotion]).
 *
 * @param markModifier Goes on the empty place kept for the mark, which is how the screen learns where to draw it.
 * @param isCompact Collapses the mark's place away, in a window too short to give a decoration the room the answers need
 *   ([ONBOARDING_MARK_MIN_HEIGHT]).
 */
@Composable
internal fun OnboardingPage(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    title: String,
    message: String,
    isCompact: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().fadingVerticalEdges(scrollState),
    ) {
        val isRoomy = maxHeight >= ONBOARDING_ROOMY_MIN_HEIGHT
        val sideMargin = onboardingSideMargin(maxWidth)
        val gap by animateDpAsState(if (isRoomy) ROOMY_GAP else GAP, tween(ONBOARDING_MARK_RESIZE_MILLIS))
        val headerGap by animateDpAsState(if (isRoomy) ROOMY_HEADER_GAP else HEADER_GAP, tween(ONBOARDING_MARK_RESIZE_MILLIS))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bounceVerticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centered, a page in a tall window reads as sinking toward the bar under it, so the spare room above it is
            // the smaller share. The weights split only what the minimum height leaves, so a page that scrolls has none.
            Spacer(modifier = Modifier.weight(2f))
            Column(
                modifier = Modifier.padding(horizontal = sideMargin).widthIn(max = ONBOARDING_CONTENT_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                OnboardingPageHeader(
                    modifier = Modifier.onboardingLayerMotion(::onboardingHeaderMotion),
                    markModifier = markModifier,
                    title = title,
                    message = message,
                    isCompact = isCompact,
                    gap = headerGap,
                )
                // The answers are a layer of their own, so that a turn moves them apart from the question; spaced as the
                // page's column spaces them, which is what they were laid out by before they had one.
                val footer = LocalOnboardingPageFooter.current
                Column(
                    modifier = Modifier.fillMaxWidth().onboardingLayerMotion(::onboardingAnswersMotion),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    content()
                    footer()
                }
            }
            Spacer(modifier = Modifier.weight(3f))
        }
    }
}

@Composable
private fun OnboardingPageHeader(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    title: String,
    message: String,
    isCompact: Boolean,
    gap: Dp,
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    // Collapses and opens again with the window crossing ONBOARDING_MARK_MIN_HEIGHT, its gap to the title with it, while
    // the screen shrinks and fades the mark drawn over it ([OnboardingMark]), so that neither leaves in one frame.
    AnimatedVisibility(
        visible = !isCompact,
        enter = fadeIn(tween(ONBOARDING_MARK_RESIZE_MILLIS)) + expandVertically(tween(ONBOARDING_MARK_RESIZE_MILLIS), expandFrom = Alignment.Top),
        exit = fadeOut(tween(ONBOARDING_MARK_RESIZE_MILLIS)) + shrinkVertically(tween(ONBOARDING_MARK_RESIZE_MILLIS), shrinkTowards = Alignment.Top),
    ) {
        Spacer(modifier = markModifier.padding(bottom = gap).size(ONBOARDING_MARK_SIZE))
    }
    Text(
        modifier = Modifier.semantics { heading() },
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(gap))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * What a page shows under its answers, whichever step it is: the screen gives the last page the first run's offer to open
 * a copied link ([OnboardingClipboardLinkCard]), which belongs to no question of its own, and every other page nothing.
 */
internal val LocalOnboardingPageFooter = compositionLocalOf<@Composable () -> Unit> { {} }

private val GAP = 16.dp
private val ROOMY_GAP = 24.dp
private val HEADER_GAP = 12.dp
private val ROOMY_HEADER_GAP = 16.dp
