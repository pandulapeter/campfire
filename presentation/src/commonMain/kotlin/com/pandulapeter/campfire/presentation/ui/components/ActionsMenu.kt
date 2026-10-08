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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.songs_actions
import org.jetbrains.compose.resources.painterResource

/**
 * The actions of a row or an app bar: as many of [items] as the width it is given has room for, each a button of its
 * own, and the rest behind an overflow button that opens them as a dropdown. The items move out of the menu one at a
 * time, in the menu's own order, so a row that grows reads the same entries left to right that its menu lists top to
 * bottom, and once every item has a button there is no overflow button left at all. The overflow button only ever
 * stands at the end, and it takes one of the buttons' places while it is there.
 *
 * Not every action is let out, however much room there is ([ActionsMenuItem.isAlwaysInMenu]): the ones that destroy
 * something, the ones worth a second thought and the ones that are rarely wanted stay behind the tap, since a bar of
 * every button the room allows is a busier screen than one of the few that are reached for, and a button is an
 * invitation. Wherever any of them is among the items the overflow button is there for good.
 *
 * How much room that is belongs to the caller, as the largest width it hands this down - a card keeping its title
 * readable, a section header measured at its narrowest pinned width, the song details app bar keeping the title - so
 * that the decision follows the width of the list or the window rather than the length of one title, and every row of
 * a list offers the same number of buttons. The buttons come and go by expanding and shrinking as that width crosses
 * a button's, the way the song details' steppers move between the bar and its menu. They reach into each other by
 * [ACTION_BUTTON_OVERLAP] and the row pads its ends by half of that, so a menu of one button is as wide as the button,
 * and whatever stands next to the row overlaps its first button by trimming the row's start (see [overlappingAction]).
 *
 * Separate from [SongActions] because not every row that wants these has a song behind it: a setlist entry whose file
 * has gone missing still has the one action of being taken out of the setlist, and the editor has its revert.
 *
 * @param buttonModifier Put on every button, which a section header uses to keep them from taking the focus.
 * @param state Whether the menu is open, hoisted by a row that opens the same menu from a long press as well. A long
 *   press on a row that has room for every button opens nothing, since there is no menu left to open.
 * @param isExpandable False keeps every item in the menu however much room there is, which is what a song card does:
 *   a list of cards each ending in a row of buttons is a wall of icons, so a card keeps its menu and only the button
 *   in front of it.
 * @param isDecorative Draws the icons alone, for the copy of a section header that is only ever seen being pushed away
 *   and takes no touches.
 * @param menuFooter Drawn at the end of the menu, after its entries: controls that are adjusted rather than chosen, so
 *   they leave the menu open, and that have no button of their own however much room there is - which keeps the
 *   overflow button there for good.
 * @param icon The overflow button's icon: the dots, unless the menu stands next to another one and has to say which of
 *   the two it is, as the song details screen's editing menu does.
 */
@Composable
internal fun ActionsMenu(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    contentDescription: String = stringResource(Res.string.songs_actions),
    icon: Painter = painterResource(Res.drawable.ic_more),
    items: List<ActionsMenuItem>,
    isExpandable: Boolean = true,
    isDecorative: Boolean = false,
    menuFooter: (@Composable () -> Unit)? = null,
) = BoxWithConstraints(
    modifier = modifier,
) {
    val buttonWidth = actionButtonWidth()
    val slotCount = ((maxWidth - ACTION_BUTTON_OVERLAP) / (buttonWidth - ACTION_BUTTON_OVERLAP)).toInt().coerceAtLeast(1)
    val visibleItems = items.filter { it.isVisible }
    val expandableItems = if (isExpandable) visibleItems.filterNot { it.isAlwaysInMenu } else emptyList()
    val buttonItems = if (menuFooter == null && expandableItems.size == visibleItems.size && visibleItems.size <= slotCount) {
        visibleItems
    } else {
        // The overflow button stays, so it takes one of the places.
        expandableItems.take(slotCount - 1)
    }
    val menuItems = visibleItems.filterNot { it in buttonItems }
    val hasMenu = menuItems.isNotEmpty() || menuFooter != null
    // A menu the room has just emptied, or one a long press asked for with nothing left in it, would otherwise be left
    // open in its state and drop down on its own the next time the row narrows.
    LaunchedEffect(hasMenu, state.isExpanded) {
        if (!hasMenu) state.dismiss()
    }
    Row(
        modifier = Modifier.padding(horizontal = ACTION_BUTTON_OVERLAP / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            // Keyed by what the action is rather than by its place, since an action that comes and goes (Update file
            // name, Set cover art) moves every one after it along.
            key(item.key) {
                AnimatedVisibility(
                    modifier = Modifier.overlappingAction(),
                    visible = item in buttonItems,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    ActionButton(
                        modifier = buttonModifier,
                        icon = item.icon,
                        animateIconChange = item.animateIconChange,
                        contentDescription = item.title,
                        isEnabled = item.isEnabled,
                        isDecorative = isDecorative,
                        size = buttonWidth,
                        onClick = item.onClick,
                    )
                }
            }
        }
        AnimatedVisibility(
            modifier = Modifier.overlappingAction(),
            visible = hasMenu,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            if (isDecorative) {
                ActionButton(
                    icon = icon,
                    contentDescription = null,
                    isDecorative = true,
                    size = buttonWidth,
                )
            } else {
                OverflowMenu(
                    state = state,
                    button = { open ->
                        ActionButton(
                            modifier = buttonModifier,
                            icon = icon,
                            contentDescription = contentDescription,
                            size = buttonWidth,
                            onClick = open,
                        )
                    },
                ) { select ->
                    menuItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.title) },
                            leadingIcon = { ActionIcon(icon = item.icon, contentDescription = null, animateIconChange = item.animateIconChange) },
                            enabled = item.isEnabledInMenu?.invoke() ?: item.isEnabled,
                            onClick = { select(item.onClick) },
                        )
                    }
                    menuFooter?.invoke()
                }
            }
        }
    }
}

/**
 * One action of an [ActionsMenu]: a button of its own wherever there is the room for it, and an entry of the menu
 * wherever there is not, so its [title] is both the entry's text and the button's content description.
 *
 * @param key Stable identity for a toggle whose title changes along with its state.
 * @param animateIconChange Crossfades icon changes while preserving the button's position.
 * @param isAlwaysInMenu Keeps the action in the menu however much room there is: one that deletes or throws something
 *   away, and one that is rarely wanted (archiving, duplicating, exporting), which a button would only advertise.
 * @param isEnabledInMenu Read only while the menu is open, in place of [isEnabled], for an item whose answer is expensive
 *   to work out: the open menu is all that subscribes to what it reads, so nothing works it out while the menu is
 *   closed (the editor's Prettify, which would otherwise prettify the whole song on every keystroke). A button never
 *   reads it, so an item that sets it is [isAlwaysInMenu] as well.
 */
@Immutable
internal class ActionsMenuItem(
    val title: String,
    val icon: Painter,
    val isEnabled: Boolean = true,
    val isAlwaysInMenu: Boolean = false,
    val isVisible: Boolean = true,
    val key: String = title,
    val animateIconChange: Boolean = false,
    val isEnabledInMenu: (() -> Boolean)? = null,
    val onClick: () -> Unit,
)

/**
 * How wide every button of an [ActionsMenu] is laid out: an icon button's touch target, which is what the room is
 * counted in. Where the touch target is not enforced the button is its container alone.
 */
@Composable
private fun actionButtonWidth(): Dp = maxOf(LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp }, ACTION_BUTTON_CONTAINER_SIZE)

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: Painter,
    contentDescription: String?,
    animateIconChange: Boolean = false,
    isEnabled: Boolean = true,
    isDecorative: Boolean = false,
    size: Dp,
    onClick: () -> Unit = {},
) = if (isDecorative) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        ActionIcon(icon = icon, contentDescription = null, animateIconChange = animateIconChange, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
} else {
    IconButton(
        modifier = modifier,
        enabled = isEnabled,
        onClick = onClick,
    ) {
        ActionIcon(icon = icon, contentDescription = contentDescription, animateIconChange = animateIconChange)
    }
}

/** Keep a toggle's icon transition inside its stable button, including the decorative sticky-header copy. */
@Composable
private fun ActionIcon(
    icon: Painter,
    contentDescription: String?,
    animateIconChange: Boolean,
    tint: Color = LocalContentColor.current,
) {
    if (animateIconChange) {
        Crossfade(targetState = icon) { painter ->
            Icon(painter = painter, contentDescription = contentDescription, tint = tint)
        }
    } else {
        Icon(painter = icon, contentDescription = contentDescription, tint = tint)
    }
}

/** Material's icon button container, which its touch target is added around. */
private val ACTION_BUTTON_CONTAINER_SIZE = 40.dp
