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

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.hint
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Names the action of a control drawn as an icon alone: a tooltip over [content], shown once a mouse has rested on it
 * for [HOVER_DELAY_MILLIS] and, on a touch screen, on a long press, which then swallows the click it would otherwise end
 * in. Nothing at all while the user has switched tooltips off ([LocalAreTooltipsEnabled]).
 *
 * [label] is the string the control already gives screen readers as its content description, so the two never say
 * different things. A toggle's label names what pressing it does, not the state it is in. [shortcut]'s keys follow it,
 * quieter, where there is a keyboard to press them on ([hint]); a control that names itself with its own text (Save)
 * passes no label, and then has a tooltip only where the keys are shown.
 *
 * Material's own input handling is off, and the two gestures are this box's instead, for three reasons. `TooltipBox`
 * puts its modifier on a box inside a bare one, so a `weight` or an `align` given to it never reaches the parent; here
 * [modifier] is the outer box's, and a control that has to fill a weighted share fills it ([content] is measured
 * without the parent's minimum, as in any `Box`). Material shows a tooltip, for good, whenever the control inside takes
 * the keyboard focus, eating the Escape that would dismiss it: the app moves the focus itself (a screen taking it as it
 * opens, a sheet's first field), and Escape is the desktop's Back. And it shows one the moment a pointer enters, so a
 * pointer only passing over a row of buttons flickers a tooltip for each.
 *
 * @param isEnabled Off where the control's own text names it for the moment (a button that shows its icon alone only
 *   where it is short of room), which keeps the anchor in place so that the control is not composed afresh as it turns
 *   on and off.
 * @param isShownOnLongPress Off for a control whose long press is already something else (a stepper repeating while it
 *   is held), which then shows its tooltip on hover alone.
 */
@Composable
internal fun CampfireTooltip(
    modifier: Modifier = Modifier,
    label: String?,
    shortcut: KeyShortcut? = null,
    isEnabled: Boolean = true,
    isShownOnLongPress: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState()
    val hint = shortcut?.hint()
    Box(
        modifier = when {
            !isEnabled || !LocalAreTooltipsEnabled.current || (label == null && hint == null) -> modifier
            isShownOnLongPress -> modifier.showsOnLongPress(state).showsOnHover(state)
            else -> modifier.showsOnHover(state)
        },
    ) {
        TooltipBox(
            positionProvider = rememberTooltipPositionProvider(),
            tooltip = { TooltipBubble(label = label, hint = hint) },
            state = state,
            enableUserInput = false,
            content = content,
        )
    }
}

/**
 * Drawn as the app's own pills are rather than as Material's dark slab, which reads as a system's overlay rather than
 * part of the screen: the tonal surface the steppers and the app bar's pill stand on, fully rounded, lifted by the
 * section headers' shadow so that it holds up over a surface of the same tone.
 */
@Composable
private fun TooltipBubble(label: String?, hint: String?) = Surface(
    // The popup is only as large as what it holds, so the shadow is given room inside it.
    modifier = Modifier.padding(TOOLTIP_ELEVATION * 2),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
    shadowElevation = TOOLTIP_ELEVATION,
) {
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        modifier = Modifier
            .widthIn(max = TOOLTIP_MAX_WIDTH)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        text = buildAnnotatedString {
            label?.let(::append)
            if (hint != null) {
                if (label != null) append(HINT_SEPARATOR)
                // Keys are written left to right in every language, Hebrew's menus included.
                withStyle(SpanStyle(color = hintColor)) { append(hint.isolatedLeftToRight()) }
            }
        },
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Shows the tooltip once a mouse has rested on the anchor for [HOVER_DELAY_MILLIS], or at once while another one has
 * only just been shown ([TooltipWarmth]); a press hides it until the pointer leaves and comes back. Seen on the main
 * pass and never consumed, so the control under it hovers and clicks as it would without a tooltip.
 */
private fun Modifier.showsOnHover(state: TooltipState) = pointerInput(state) {
    coroutineScope {
        var pending: Job? = null
        fun hide() {
            pending?.cancel()
            pending = null
            if (state.isVisible) launch { TooltipWarmth.coolDown() }
            state.dismiss()
        }
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.firstOrNull()?.type != PointerType.Mouse) continue
                when (event.type) {
                    PointerEventType.Enter -> {
                        pending?.cancel()
                        pending = launch {
                            if (!TooltipWarmth.isWarm) delay(HOVER_DELAY_MILLIS)
                            state.show(MutatePriority.UserInput)
                        }
                    }
                    PointerEventType.Exit, PointerEventType.Press -> hide()
                }
            }
        }
    }
}

/**
 * Shows the tooltip once a finger or a stylus has held the anchor for a long press, and keeps it a moment after the
 * release, which is consumed on the initial pass so that the control under it is not pressed as well.
 */
private fun Modifier.showsOnLongPress(state: TooltipState) = pointerInput(state) {
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch && down.type != PointerType.Stylus) return@awaitEachGesture
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
            } catch (_: PointerEventTimeoutCancellationException) {
                val showing = launch { state.show(MutatePriority.UserInput) }
                waitForUpOrCancellation(PointerEventPass.Initial)?.consume()
                launch {
                    delay(TOUCH_TOOLTIP_LINGER_MILLIS)
                    showing.cancel()
                }
            }
        }
    }
}

/**
 * Whether a hovered tooltip has only just gone, so that a pointer moving straight on to the next button of a row is
 * answered at once, as a desktop's toolbars do, rather than waiting out the delay at every one. Warm only for
 * [WARM_WINDOW_MILLIS] after a tooltip goes: a pointer that rests anywhere else for longer starts cold again.
 *
 * Timed by a coroutine on the UI's own clock rather than by reading a time source, which behaves alike on every platform;
 * the cooling is the hiding anchor's, and an anchor that leaves the screen before it ends leaves it cold. Only ever
 * touched on the main thread, by the pointer handlers.
 */
private object TooltipWarmth {

    var isWarm = false
        private set

    /** Which cooling is the latest, so that an earlier one ending does not cool a later one's window. */
    private var generation = 0

    suspend fun coolDown() {
        val cooling = ++generation
        isWarm = true
        try {
            delay(WARM_WINDOW_MILLIS)
        } finally {
            if (generation == cooling) isWarm = false
        }
    }
}

/**
 * How long a mouse rests on a control before it names itself: long enough that a pointer only passing over a row of
 * buttons shows nothing, short enough that a pointer stopped to wonder is answered before it moves on. The usual
 * desktop and web value.
 */
private const val HOVER_DELAY_MILLIS = 500L

/**
 * How soon after one tooltip goes the next is shown without [HOVER_DELAY_MILLIS]: long enough for a pointer to cross
 * from one button to its neighbour, too short to carry over a pause. Radix's default, and Windows' reshow is shorter.
 */
private const val WARM_WINDOW_MILLIS = 300L

/** How long a tooltip shown by a long press stays after the finger lifts, long enough to read a short label. */
private const val TOUCH_TOOLTIP_LINGER_MILLIS = 1500L

private const val HINT_SEPARATOR = "  "

private val TOOLTIP_ELEVATION = 2.dp
private val TOOLTIP_MAX_WIDTH = 240.dp
