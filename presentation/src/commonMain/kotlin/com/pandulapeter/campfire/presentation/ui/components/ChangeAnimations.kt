/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * [AnimatedContent] for a card of a list, composed only once [targetState] first changes: until then [content] is
 * composed plainly, in a [Box] aligned as the transition would align it. A pop back to a list or a tab switch composes
 * every visible card at once, and the transition objects of every card cost that frame a good share of its time for a
 * change that, on nearly every card, never happens while it is on screen.
 *
 * The first change arms the transition in its own frame, starting from the value the content rested on, so it is
 * animated as every later one. The resting composition is movable content, which the transition's slot for that value
 * takes over in the same recomposition rather than composing it afresh: a card's actions hold its overflow menu, whose
 * Select multiple entry closes it and starts the selection mode in one press, and composed again the menu would vanish
 * instead of fading out, its button's ripple cut short with it.
 */
@Composable
internal fun <T> AnimatedContentOnChange(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val gate = remember { ChangeGate(targetState) }
    val currentContent by rememberUpdatedState(content)
    val resting = remember { movableContentOf { value: T -> currentContent(value) } }
    if (!gate.hasChangedTo(targetState)) {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
        ) {
            resting(targetState)
        }
    } else {
        val transitionState = remember { MutableTransitionState(gate.resting) }
        transitionState.targetState = targetState
        rememberTransition(transitionState).AnimatedContent(
            modifier = modifier,
            transitionSpec = transitionSpec,
            contentAlignment = contentAlignment,
        ) { value ->
            if (value == gate.resting) resting(value) else currentContent(value)
        }
    }
}

/**
 * [AnimatedVisibility] composed only once [visible] first changes, for the same reason and in the same way as
 * [AnimatedContentOnChange]: [content] plainly while it is shown and nothing while it is not, until a change arms the
 * transition, which starts from the visibility the content rested at and takes over its composition.
 */
@Composable
internal fun AnimatedVisibilityOnChange(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    val gate = remember { ChangeGate(visible) }
    val currentContent by rememberUpdatedState(content)
    val resting = remember { movableContentOf { currentContent() } }
    if (!gate.hasChangedTo(visible)) {
        if (visible) {
            Box(modifier = modifier) {
                resting()
            }
        }
    } else {
        val visibleState = remember { MutableTransitionState(gate.resting) }
        visibleState.targetState = visible
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = modifier,
            enter = enter,
            exit = exit,
        ) {
            resting()
        }
    }
}
