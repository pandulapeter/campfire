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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.scene.Scene
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.math.roundToInt

/**
 * The screens are a deck of cards. Pushing one deals it over the top of the deck: it slides in from the end while
 * the screen it lands on gives way in the same direction over a small fraction of the distance. Popping takes the
 * top card off again: it slides back out and the screen underneath returns from that short offset. The screen
 * underneath moves as one piece, its navigation chrome included, so it keeps whatever it had on screen (its scroll
 * position, the caret in its search field) in the same place relative to itself across the whole transition.
 *
 * Switching between top level destinations is not a deal but a swap of the bottom card, so those fade through in
 * place, next to a navigation bar and a navigation rail alike: nothing about two tabs puts one of them in any
 * direction of the other.
 *
 * Whether a transition is a push or a pop is decided here from the depth of the scenes instead of relying on
 * Navigation 3's own detection: when a back stack change interrupts a running transition, Navigation 3 records the
 * already updated back stack as the transition's starting point and animates a pop with the push spec. That leaves
 * the outgoing screen invisible but still covering (and swallowing clicks on) the screen underneath until the
 * animation ends. The same specs are used on every platform (the desktop default would be no animation at all).
 *
 * The decision is also handed to [scrim], which darkens the screen underneath for as long as a card covers any of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.navigationTransition(
    motionScheme: MotionScheme,
    scrim: NavigationScrim,
): ContentTransform {
    val from = CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey)
    val to = CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey)
    scrim.isPredictiveBack = false
    scrim.motion = when {
        from != null && to != null -> DeckMotion.None
        targetState.zIndex < initialState.zIndex -> DeckMotion.Pop
        else -> DeckMotion.Push
    }
    return when (scrim.motion) {
        DeckMotion.None -> tabTransition()
        DeckMotion.Pop, DeckMotion.PredictivePop -> popTransition(motionScheme)
        DeckMotion.Push -> pushTransition(motionScheme)
    }
}

/** What the back stack changes with while the launch screen still covers the app: nothing moves, nothing is darkened. */
internal fun instantTransition(scrim: NavigationScrim): ContentTransform {
    scrim.isPredictiveBack = false
    scrim.motion = DeckMotion.None
    return ContentTransform(EnterTransition.None, ExitTransition.None)
}

/**
 * The card being dealt slides in over the deck. The screen underneath follows in the same direction over a much
 * shorter distance. [ExitTransition.KeepUntilTransitionsFinished] keeps it drawn until the card has landed.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.pushTransition(
    motionScheme: MotionScheme,
): ContentTransform {
    val direction = AnimatedContentTransitionScope.SlideDirection.Left
    val spec = motionScheme.slideSpec()
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards = direction, animationSpec = spec),
        initialContentExit = slideOutOfContainer(
            towards = direction,
            animationSpec = spec,
            targetOffset = ::backgroundSlideOffset,
        ) + ExitTransition.KeepUntilTransitionsFinished,
        targetContentZIndex = targetState.zIndex,
    )
}

/**
 * The top card slides away while the screen underneath follows it from a shorter offset. The z indices keep the
 * card that is leaving above the screen it reveals.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.popTransition(
    motionScheme: MotionScheme,
): ContentTransform {
    val direction = AnimatedContentTransitionScope.SlideDirection.Right
    val spec = motionScheme.slideSpec()
    return ContentTransform(
        targetContentEnter = slideIntoContainer(
            towards = direction,
            animationSpec = spec,
            initialOffset = ::backgroundSlideOffset,
        ),
        initialContentExit = slideOutOfContainer(towards = direction, animationSpec = spec),
        targetContentZIndex = targetState.zIndex,
    )
}

/** The underlying screen travels a small fraction of the card's full slide, using the same animation progress. */
internal fun backgroundSlideOffset(fullSlideOffset: Int) = (fullSlideOffset * BACKGROUND_SLIDE_FRACTION).roundToInt()

/**
 * The theme's default spatial spring, made to settle once the card is within a pixel of where it lands. The theme's
 * spring carries no visibility threshold, so on an [IntOffset] it runs on to a hundredth of a pixel: a screen that
 * has not moved a whole pixel for the last third of a second is still counted as moving, and [ScreenSurface] takes
 * no taps until it is not. An offset is drawn in whole pixels, so nothing past this one is ever seen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MotionScheme.slideSpec() = when (val spec = defaultSpatialSpec<IntOffset>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = IntOffset.VisibilityThreshold)
    else -> spec
}

/**
 * [slideSpec] for a slide animated as the fraction of the width it has covered rather than as an offset, which keeps
 * the two on the same curve. It settles within a thousandth of the width, a pixel or less on any window it is seen in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun MotionScheme.slideFractionSpec() = when (val spec = defaultSpatialSpec<Float>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = SLIDE_FRACTION_THRESHOLD)
    else -> spec
}

/**
 * The pop driven by the predictive back gesture (Android) or the edge swipe (iOS): the same uncovering as
 * [popTransition], except that both screens follow the finger with linear specs. Their direction is fixed to the
 * horizontal pop, regardless of which edge the gesture started from.
 *
 * Going back from Setlists or Settings to Songs is a swap of tabs rather than a card being taken off, so it cross
 * fades in place, for the reasons [navigationTransition] gives. It is a cross fade rather than [tabTransition]'s fade
 * through, since the gesture can be held anywhere along its way, and a fade through would show neither screen for
 * the middle of it.
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.predictivePopTransition(scrim: NavigationScrim): ContentTransform {
    scrim.isPredictiveBack = true
    if (CampfireDestination.TopLevel.fromContentKey(initialState.entries.lastOrNull()?.contentKey) != null &&
        CampfireDestination.TopLevel.fromContentKey(targetState.entries.lastOrNull()?.contentKey) != null
    ) {
        scrim.motion = DeckMotion.None
        val spec = tween<Float>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
        return ContentTransform(
            targetContentEnter = fadeIn(spec),
            initialContentExit = fadeOut(spec),
            targetContentZIndex = targetState.zIndex,
        )
    }
    scrim.motion = DeckMotion.PredictivePop
    val towards = AnimatedContentTransitionScope.SlideDirection.Right
    val spec = tween<IntOffset>(PREDICTIVE_BACK_DURATION, easing = LinearEasing)
    return ContentTransform(
        targetContentEnter = slideIntoContainer(towards, spec, initialOffset = ::backgroundSlideOffset),
        initialContentExit = slideOutOfContainer(towards, spec),
        targetContentZIndex = targetState.zIndex,
    )
}

/**
 * Material's fade through: the screen being left fades out first and only then does the one being opened fade in,
 * rather than the two cross fading at the same time. Two lists half drawn over each other read as a smear of
 * overlapping text for the middle of a cross fade, whereas this passes through nothing but the background both
 * screens are painted on.
 */
private fun AnimatedContentTransitionScope<Scene<CampfireDestination>>.tabTransition() = ContentTransform(
    targetContentEnter = fadeIn(tween(TAB_FADE_IN_DURATION, delayMillis = TAB_FADE_OUT_DURATION, easing = LinearOutSlowInEasing)),
    initialContentExit = fadeOut(tween(TAB_FADE_OUT_DURATION, easing = FastOutLinearInEasing)),
    targetContentZIndex = targetState.zIndex,
)

/**
 * Deeper screens are drawn above shallower ones, so that a pushed screen covers its parent and a popped screen
 * slides away on top of the screen it reveals.
 */
private val Scene<CampfireDestination>.zIndex: Float
    get() = previousEntries.size.toFloat()

private const val TAB_FADE_OUT_DURATION = 90
private const val TAB_FADE_IN_DURATION = 210
internal const val PREDICTIVE_BACK_DURATION = 350
private const val BACKGROUND_SLIDE_FRACTION = 0.12f

private const val SLIDE_FRACTION_THRESHOLD = 0.001f
