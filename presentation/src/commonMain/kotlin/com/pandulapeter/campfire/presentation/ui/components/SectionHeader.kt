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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.roundToInt

/**
 * A sticky section row: the section's name, and a setlist's menu, with nothing behind them and no divider under them.
 * The cards are what makes room for a header rather than a background of its own: they are faded out entirely under
 * the row of the one pinned at the top of the list and fade back in below it ([ListTopFade]), so the name is always
 * read against the screen's background, and it and the app bar's pill of buttons next to it are the only things at
 * the top of the list. Only the header's content takes a touch, laid out as a pill with no press drawn in it but the
 * name dimmed while it is held, and of the pill only the part up to its action, whose buttons take their own; the rest
 * of the row leaves it to whatever is under it.
 *
 * The list screens have no title in their app bar, and the header pinned at the top of the list is what stands in its
 * place, under the bar's buttons: so the row is as tall as that bar ([LIST_APP_BAR_HEIGHT]) and the pill as tall as the
 * bar's pill, its text on one line in the style of the settings screen's tab labels. The pill is as wide as the cards,
 * and narrows only to make room for the bar's buttons ([AppBarOverlap.reach]) as the header comes into the pinned place
 * ([SectionHeaderState.pinnedFraction], eased), so a pinned header never runs under them - its text cut off sooner,
 * and a setlist's menu, which ends the pill, carried up to the buttons. It widens again along the same eased curve as
 * an opening search moves the list down out from under the buttons ([AppBarOverlap.coverage]), and narrows along it
 * as a closing one brings the list back up.
 *
 * The row extends through the grid's end padding, beneath the fast scroller, so that the reach and the pill's end are
 * measured from the same edge. A decorative copy can be drawn outside the grid while the row is pushed up
 * ([pushedDistancePx]); its content lags behind the row there and fades with [contentOpacity].
 *
 * Everything that follows the scroll position is handed over as a function and read only while the row is laid out
 * ([state]) or drawn ([opacity], [contentOpacity], [pushedDistancePx]), so that a scroll moving it recomposes nothing.
 *
 * @param backgroundColor Optional backing for transitions in which cards' placement and fades can settle separately.
 * @param subtitle A second line under the name, in the type of the text under the header rather than the header's own:
 *   a setlist's countdown, which has to stay in sight wherever in the setlist the reader is. The pill is as tall as the
 *   bar's, which holds both lines, so a subtitle moves nothing around the header.
 * @param action The buttons at the end of the pill, a setlist's [SetlistActions]: handed the modifier that decides how
 *   much of the pill they may take, and the one that keeps each of them from taking the focus ([unfocusable]). The room
 *   is counted at the width the pill narrows to once pinned, whatever it is now, so that a header does not trade its
 *   buttons for a menu as it is scrolled into the bar's place, and the name keeps [SECTION_HEADER_MIN_TEXT_WIDTH].
 * @param appBarOverlap How far in from the row's end edge the app bar's buttons reach while this row is pinned under
 *   them, and how much of the pinned place they still cover, read while the row is laid out so that the bar filling in
 *   and emptying moves the header without recomposing the list around it.
 */
@Composable
internal fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String,
    subtitle: String? = null,
    state: () -> SectionHeaderState,
    endPadding: Dp,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    onClick: (() -> Unit)?,
    action: (@Composable (modifier: Modifier, buttonModifier: Modifier) -> Unit)? = null,
    opacity: () -> Float = { 1f },
    contentOpacity: () -> Float = { 1f },
    pushedDistancePx: () -> Int = { 0 },
    appBarOverlap: () -> AppBarOverlap,
    backgroundColor: Color = Color.Transparent,
) = Box(
    modifier = modifier
        .extendIntoEndPadding(endPadding)
        .graphicsLayer {
            alpha = opacity()
            clip = false
        }
        .fillMaxWidth()
        .background(backgroundColor)
        .defaultMinSize(minHeight = LIST_APP_BAR_HEIGHT),
    contentAlignment = Alignment.CenterStart,
) {
    val hasAction = action != null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val nameAlpha by animateFloatAsState(if (isPressed) PRESSED_HEADING_ALPHA else 1f)
    val pillModifier = Modifier
        .padding(start = SONG_CARD_OUTER_PADDING)
        .layout { measurable, constraints ->
            val width = (constraints.maxWidth - sectionHeaderEndInsets(state(), appBarOverlap(), endPadding).current).coerceAtLeast(0)
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .graphicsLayer {
            alpha = contentOpacity()
            // The outgoing pill lags the row slightly, then disappears before reaching the bar's controls.
            translationY = pushedDistancePx() * SECTION_HEADER_CONTENT_PARALLAX_FRACTION
        }
    val pillContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = SECTION_HEADER_PILL_HEIGHT)
                .padding(end = if (hasAction) SECTION_HEADER_PILL_ACTION_END_PADDING else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The name is the touch target, filling the pill up to the action, which keeps it at the end of the row
            // however short the name is. The target ends where the action begins, with the gap between the two its
            // own, so a pointer only gets the hand where a click scrolls to the section rather than also between and
            // around the action's buttons, and only the name answers the press, the way the song details screen's
            // title does: the action's buttons take their own touches and ripple for them.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SECTION_HEADER_PILL_HEIGHT)
                    .then(
                        if (onClick == null) {
                            Modifier
                        } else {
                            Modifier
                                .unfocusable()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
                        },
                    )
                    .graphicsLayer { alpha = nameAlpha }
                    .padding(start = songCardContentPadding, end = if (hasAction) SECTION_HEADER_TEXT_GAP else songCardContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Keep the painter through its exit animation when a setlist is archived from this row's own menu.
                var lastIcon by remember { mutableStateOf(icon) }
                icon?.let { lastIcon = it }
                AnimatedVisibility(
                    visible = icon != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    lastIcon?.let { painter ->
                        Icon(
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            painter = painter,
                            contentDescription = iconContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = text,
                        // The settings screen's tab labels, the other thing that stands at the top of a top level screen.
                        style = MaterialTheme.typography.titleSmall,
                        // The palette's second accent, which is the primary color in every palette but the app's own: the
                        // headers the list is filed under stand apart from the titles of everything around the list.
                        color = LocalSecondAccentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            action?.invoke(
                Modifier.layout { measurable, constraints ->
                    // What the pill is narrower by once pinned is taken off what it holds now; both insets are
                    // rounded the way the pill rounds its own, so the room does not flicker by a pixel as it narrows.
                    val insets = sectionHeaderEndInsets(state(), appBarOverlap(), endPadding)
                    val maxWidth = (constraints.maxWidth + insets.current - insets.pinned - (SECTION_HEADER_MIN_TEXT_WIDTH + SECTION_HEADER_TEXT_GAP).roundToPx())
                        .coerceAtLeast(SECTION_HEADER_PILL_HEIGHT.roundToPx())
                        .coerceAtMost(constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = maxWidth))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                },
                Modifier.unfocusable(),
            )
        }
    }
    // The pill's width is handed to its content as a minimum, or the row would wrap the name and pull a setlist's menu
    // in next to it, and the touch would end with the name. Nothing is drawn behind the press: the header has nothing
    // behind it to draw one in, and a ripple across the width of the list would promise more than a scroll to the
    // section, so the name is dimmed instead.
    Box(
        modifier = pillModifier,
        propagateMinConstraints = true,
    ) { pillContent() }
}

/**
 * How far a [SectionHeader]'s pill ends from the end of its row, in whole pixels: [current]ly, and once pinned under
 * the app bar's buttons.
 */
private class SectionHeaderEndInsets(
    val current: Int,
    val pinned: Int,
)

private fun Density.sectionHeaderEndInsets(
    state: SectionHeaderState,
    overlap: AppBarOverlap,
    endPadding: Dp,
): SectionHeaderEndInsets {
    // The cards end at the scroller's column, and a pinned header's action ends where the bar's pill begins: its touch
    // target keeps the icon off the pill by as much as two neighboring buttons keep their icons apart, and the header's
    // own pill, which draws nothing, reaches that little way in under the bar.
    val cardsEndInset = (endPadding + SONG_CARD_OUTER_PADDING).toPx()
    val pinnedEndInset = maxOf(cardsEndInset, (overlap.reach - SECTION_HEADER_PILL_ACTION_END_PADDING).toPx())
    // Eased rather than linear, since the fraction is the scroll position itself: the header gives way gently as it
    // starts coming into the bar's place and settles into the room it has left the same way. The list moving down
    // under an opening search takes a pinned header out of that place, so the same curve is run on how much of it the
    // buttons still cover, and a pinned one widens and narrows along the path it narrowed along as it was scrolled up.
    val coveredFraction = FastOutSlowInEasing.transform(state.pinnedFraction) * FastOutSlowInEasing.transform(overlap.coverage)
    return SectionHeaderEndInsets(
        current = (cardsEndInset + (pinnedEndInset - cardsEndInset) * coveredFraction).roundToInt(),
        pinned = pinnedEndInset.roundToInt(),
    )
}

/**
 * Keeps a [SectionHeader]'s pill and its actions from taking the focus, which a click hands them on the desktop and the
 * web: a lazy grid keeps its focused item composed and placed after it has scrolled away, and a header held that way
 * upsets how the grid pins the ones after it, which are then drawn nowhere once they reach the top. The pill only
 * scrolls to its section and the action opens a menu, neither of which the keyboard needs a stop in the list for.
 */
private fun Modifier.unfocusable() = focusProperties { canFocus = false }

/** Paint the header into the grid's end padding without changing the width the grid assigns its item. */
private fun Modifier.extendIntoEndPadding(endPadding: Dp) = layout { measurable, constraints ->
    val extraWidth = endPadding.roundToPx()
    val width = constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = width + extraWidth, maxWidth = width + extraWidth))
    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * The room a [SectionHeader] leaves between its text and the bottom of its row, where an item under it starts: the text
 * is centered in a row at least [LIST_APP_BAR_HEIGHT] tall, so the room is whatever its lines leave of that, at the
 * text size the system asks for.
 */
@Composable
internal fun sectionHeaderBottomGap(hasSubtitle: Boolean): Dp {
    val typography = MaterialTheme.typography
    val textHeight = with(LocalDensity.current) {
        typography.titleSmall.lineHeight.toDp() + if (hasSubtitle) typography.bodyMedium.lineHeight.toDp() else 0.dp
    }
    return ((LIST_APP_BAR_HEIGHT - textHeight) / 2).coerceAtLeast(0.dp)
}

/**
 * The most lines the text of a pinned [SectionHeader] runs to before it is cut short.
 */
/** The least of a setlist's name a [SectionHeader] keeps next to its actions before one of them goes into the menu. */
private val SECTION_HEADER_MIN_TEXT_WIDTH = 160.dp

/** The room a section header's text leaves before its action. */
private val SECTION_HEADER_TEXT_GAP = 4.dp

/** As tall as the pill behind the app bar's buttons, which it stands next to once pinned. */
private val SECTION_HEADER_PILL_HEIGHT = 48.dp

/**
 * What the pill leaves after an action at its end. The pill ends at the cards' edge and a card's overflow button stops
 * where [LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT] moves it from [LIST_ITEM_KEYLINE], so the two menus stand on one keyline.
 */
private val SECTION_HEADER_PILL_ACTION_END_PADDING = LIST_ITEM_KEYLINE - LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT

private const val SECTION_HEADER_CONTENT_PARALLAX_FRACTION = 0.5f
