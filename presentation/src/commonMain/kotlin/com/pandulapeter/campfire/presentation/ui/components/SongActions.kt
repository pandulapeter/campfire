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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_rename
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_setlists_outline
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.resources.songs_share_song
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
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
 * a button's, the way the song details' steppers move between the bar and its menu.
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
 */
@Composable
internal fun ActionsMenu(
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    contentDescription: String = stringResource(Res.string.songs_actions),
    items: List<ActionsMenuItem>,
    isExpandable: Boolean = true,
    isDecorative: Boolean = false,
    menuFooter: (@Composable () -> Unit)? = null,
) = BoxWithConstraints(
    modifier = modifier,
) {
    val buttonWidth = actionButtonWidth()
    val slotCount = (maxWidth / buttonWidth).toInt().coerceAtLeast(1)
    val expandableItems = if (isExpandable) items.filterNot { it.isAlwaysInMenu } else emptyList()
    val buttonItems = if (menuFooter == null && expandableItems.size == items.size && items.size <= slotCount) {
        items
    } else {
        // The overflow button stays, so it takes one of the places.
        expandableItems.take(slotCount - 1)
    }
    val menuItems = items.filterNot { it in buttonItems }
    val hasMenu = menuItems.isNotEmpty() || menuFooter != null
    // A menu the room has just emptied, or one a long press asked for with nothing left in it, would otherwise be left
    // open in its state and drop down on its own the next time the row narrows.
    LaunchedEffect(hasMenu, state.isExpanded) {
        if (!hasMenu) state.dismiss()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            // Keyed by what the action is rather than by its place, since an action that comes and goes (Update file
            // name, Share song) moves every one after it along.
            key(item.title) {
                AnimatedVisibility(
                    visible = item in buttonItems,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    ActionButton(
                        modifier = buttonModifier,
                        icon = item.icon,
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
            visible = hasMenu,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            if (isDecorative) {
                ActionButton(
                    icon = painterResource(Res.drawable.ic_more),
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
                            icon = painterResource(Res.drawable.ic_more),
                            contentDescription = contentDescription,
                            size = buttonWidth,
                            onClick = open,
                        )
                    },
                ) { select ->
                    menuItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.title) },
                            leadingIcon = { Icon(painter = item.icon, contentDescription = null) },
                            enabled = item.isEnabled,
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
 * @param isAlwaysInMenu Keeps the action in the menu however much room there is: one that deletes or throws something
 *   away, and one that is rarely wanted (archiving, duplicating, sharing, exporting), which a button would only advertise.
 */
@Immutable
internal class ActionsMenuItem(
    val title: String,
    val icon: Painter,
    val isEnabled: Boolean = true,
    val isAlwaysInMenu: Boolean = false,
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
    isEnabled: Boolean = true,
    isDecorative: Boolean = false,
    size: Dp,
    onClick: () -> Unit = {},
) = if (isDecorative) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
} else {
    IconButton(
        modifier = modifier,
        enabled = isEnabled,
        onClick = onClick,
    ) {
        Icon(painter = icon, contentDescription = contentDescription)
    }
}

/** Material's icon button container, which its touch target is added around. */
private val ACTION_BUTTON_CONTAINER_SIZE = 40.dp

/**
 * Everything that can be done to a song, on a song row or in the song details app bar: the buttons its [ActionsMenu]
 * has the room for and the rest behind its overflow button. The menu is a dropdown on every platform, touch included,
 * since an overflow button is read as the promise of a menu hanging from it - which is what every other overflow
 * button in the app opens, the setlist header's and the editor's among them.
 *
 * The overflow button is there on every platform wherever not every action has a button of its own, because it is the
 * only thing on a row that says the actions exist: the long press that opens the same menu on the songs screen
 * announces itself to nobody, so it is a shortcut for the reader who already knows about it rather than the way in.
 *
 * The setlist assignments sheet is not among the entries: where the song is read as part of the library it has a
 * [SetlistAssignmentsButton] of its own in front of these, and where it is read through a setlist it is not offered
 * at all, since a sheet of every setlist next to a row's own "Remove from setlist" made two ways of leaving the setlist
 * that read as two different things.
 *
 * @param modifier Put on the whole row of buttons, whose largest width is the room the actions may take.
 * @param state Whether the menu is open, hoisted by the songs screen, whose rows also open it from a long press.
 * @param isExpandable False on a song card, which keeps every action in the menu (see [ActionsMenu]).
 * @param isEditAlwaysInMenu Keeps "Edit" in the menu however much room there is, which the song details screen does for
 *   a song read through a setlist: a setlist is what is played from, and the editor is not what it is opened for.
 * @param isDeletable Whether the song can be deleted from here, which it only can where the song is read as part of
 *   the library. A setlist is the list somebody wrote down to play from, and a song reached through one is taken out
 *   of it rather than removed from every setlist and the library at once.
 * @param leadingItems Actions that belong to the row rather than to the song, put before the song's own: moving a row
 *   of a setlist up or down, and taking it out of the setlist.
 * @param menuFooter The song details screen's transposition and text size steppers, see [ActionsMenu].
 */
@Composable
internal fun SongActions(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    viewModel: CampfireViewModel,
    song: Song,
    isExpandable: Boolean,
    isEditAlwaysInMenu: Boolean = false,
    isDeletable: Boolean,
    leadingItems: List<ActionsMenuItem> = emptyList(),
    menuFooter: (@Composable () -> Unit)? = null,
) {
    val filePicker = LocalFilePicker.current
    ActionsMenu(
        modifier = modifier,
        state = state,
        isExpandable = isExpandable,
        menuFooter = menuFooter,
        items = leadingItems + listOfNotNull(
            ActionsMenuItem(
                title = stringResource(Res.string.songs_edit_song),
                icon = painterResource(Res.drawable.ic_edit),
                isAlwaysInMenu = isEditAlwaysInMenu,
                onClick = { viewModel.openEditor(song.fileName) },
            ),
            // Only where it would do something: a file already named after its own metadata, or one with no title to
            // be named after, has nothing to update and the action would be an offer that never comes to anything.
            if (song.canUpdateFileName) {
                ActionsMenuItem(
                    title = stringResource(Res.string.songs_update_file_name),
                    icon = painterResource(Res.drawable.ic_rename),
                    onClick = { viewModel.updateSongFileName(song) },
                )
            } else {
                null
            },
            // Only where sending a file is a different thing from saving one, which on desktop and the web it is not.
            if (filePicker.canShare) {
                ActionsMenuItem(
                    title = stringResource(Res.string.songs_share_song),
                    icon = painterResource(Res.drawable.ic_share),
                    isAlwaysInMenu = true,
                    onClick = { viewModel.shareSong(filePicker, song.fileName) },
                )
            } else {
                null
            },
            ActionsMenuItem(
                title = stringResource(Res.string.songs_export_song),
                icon = painterResource(Res.drawable.ic_export),
                isAlwaysInMenu = true,
                onClick = { viewModel.exportSong(filePicker, song.fileName) },
            ),
            if (isDeletable) {
                ActionsMenuItem(
                    title = stringResource(Res.string.songs_delete_song),
                    icon = painterResource(Res.drawable.ic_delete),
                    isAlwaysInMenu = true,
                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(song)) },
                )
            } else {
                null
            },
        ),
    )
}

/**
 * The way into the setlist assignments sheet, put in front of [SongActions] wherever a song is read as part of
 * the library - and only there: filing songs into setlists is what the library is mostly visited for, while a song
 * read through a setlist is already filed, and leaves it through its row's own menu.
 *
 * @param isInSetlist Whether the song is in at least one setlist, which fills the star. Passed in rather than collected
 *   here, since the button is in every row of the song list and one collection per screen answers them all.
 */
@Composable
internal fun SetlistAssignmentsButton(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    song: Song,
    isInSetlist: Boolean,
) = IconButton(
    modifier = modifier,
    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SetlistPicker(song)) },
) {
    // Both stars are drawn on top of each other and the one being left fades out as the other fades in, the pair turning
    // clockwise by two fifths of a turn meanwhile, whichever way the star is going: a star has five points, so the turn
    // ends on the very outline it started from and the icon settles without a jump. The angle is therefore only ever
    // added to, a change that arrives mid-turn carrying on from wherever the star is towards the next resting angle past
    // the one it was heading for. The fade waits out the lean back and happens while the star swings forwards, so the
    // outline and the fill change places in the middle of the turn rather than before it has started; it is a plain
    // tween, since the overshoot belongs to the turn alone.
    val rotation = remember { Animatable(0f) }
    var rotatedFor by remember { mutableStateOf(isInSetlist) }
    LaunchedEffect(isInSetlist) {
        if (rotatedFor != isInSetlist) {
            rotatedFor = isInSetlist
            rotation.animateTo(
                targetValue = rotation.targetValue + SETLIST_ASSIGNMENTS_ICON_TURN,
                animationSpec = tween(
                    durationMillis = SETLIST_ASSIGNMENTS_ICON_TURN_DURATION,
                    easing = AnticipateOvershootEasing,
                ),
            )
        }
    }
    val filledAlpha by animateFloatAsState(
        targetValue = if (isInSetlist) 1f else 0f,
        animationSpec = tween(
            durationMillis = SETLIST_ASSIGNMENTS_ICON_TURN_DURATION * 2 / 5,
            delayMillis = SETLIST_ASSIGNMENTS_ICON_TURN_DURATION * 3 / 10,
        ),
    )
    Box(
        modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
    ) {
        Icon(
            modifier = Modifier.graphicsLayer { alpha = 1f - filledAlpha },
            painter = painterResource(Res.drawable.ic_setlists_outline),
            contentDescription = stringResource(Res.string.songs_setlist_assignments),
        )
        Icon(
            modifier = Modifier.graphicsLayer { alpha = filledAlpha },
            painter = painterResource(Res.drawable.ic_setlists),
            contentDescription = null,
        )
    }
}

/** How far [SetlistAssignmentsButton]'s star turns between its two states: two points of five, in degrees. */
private const val SETLIST_ASSIGNMENTS_ICON_TURN = 144f

/** The length of that turn in milliseconds: a tween rather than a spring, since the easing is what carries its shape. */
private const val SETLIST_ASSIGNMENTS_ICON_TURN_DURATION = 600

/**
 * Android's `AnticipateOvershootInterpolator` at its default tension: the star first leans back against the turn, then
 * swings past where it stops and settles back onto it. Compose ships no such easing, and a spring can overshoot but never
 * anticipate.
 */
private val AnticipateOvershootEasing = Easing { fraction ->
    val tension = 3f
    if (fraction < 0.5f) {
        val t = fraction * 2f
        0.5f * t * t * ((tension + 1f) * t - tension)
    } else {
        val t = fraction * 2f - 2f
        0.5f * (t * t * ((tension + 1f) * t + tension) + 2f)
    }
}

