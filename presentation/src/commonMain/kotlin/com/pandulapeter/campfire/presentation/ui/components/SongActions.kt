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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
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
import com.pandulapeter.campfire.presentation.resources.share
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_delete_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.resources.songs_setlist_assignments
import com.pandulapeter.campfire.presentation.resources.songs_update_file_name
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import org.jetbrains.compose.resources.painterResource

/**
 * The overflow button of a list row and the menu it opens, filled by [content] with one [ActionsMenuItem] per
 * action. [content] is handed the way to choose an entry: every entry acts through it, which closes the menu before
 * the action and ignores a second choice made while the menu is on its way out.
 *
 * Separate from [SongActionsButton] because not every row that wants a menu has a song behind it: a setlist entry
 * whose file has gone missing still has the one action of being taken out of the setlist.
 *
 * [modifier] goes on the button rather than on the menu, which hangs from wherever the button ends up.
 *
 * @param state Whether the menu is open, hoisted by a row that opens the same menu from a long press as well.
 */
@Composable
internal fun ActionsMenu(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    contentDescription: String = stringResource(Res.string.songs_actions),
    content: @Composable (select: (action: () -> Unit) -> Unit) -> Unit,
) = OverflowMenu(
    state = state,
    button = { open ->
        IconButton(
            modifier = modifier,
            onClick = open,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more),
                contentDescription = contentDescription,
            )
        }
    },
    content = content,
)

/** One entry of an [ActionsMenu]. */
@Composable
internal fun ActionsMenuItem(
    title: String,
    icon: Painter,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) = DropdownMenuItem(
    text = { Text(title) },
    leadingIcon = { Icon(painter = icon, contentDescription = null) },
    enabled = isEnabled,
    onClick = onClick,
)

/**
 * The overflow button of a song row, or of the song details app bar, and the dropdown menu of everything that can be
 * done to the song. A dropdown on every platform, touch included, since an overflow button is read as the promise of
 * a menu hanging from it - which is what every other overflow button in the app opens, the setlist header's and the
 * editor's among them.
 *
 * The button is there on every platform, because it is the only thing on a row that says the actions exist: the long
 * press that opens the same menu on the songs screen announces itself to nobody, so it is a shortcut for the reader
 * who already knows about it rather than the way in.
 *
 * The setlist assignments sheet is not among the entries: where the song is read as part of the library it has a
 * [SetlistAssignmentsButton] of its own in front of the menu, and where it is read through a setlist it is not offered
 * at all, since a sheet of every setlist next to a row's own "Remove from setlist" made two ways of leaving the setlist
 * that read as two different things.
 *
 * @param state Whether the menu is open, hoisted by the songs screen, whose rows also open it from a long press.
 * @param isDeletable Whether the menu offers to delete the song, which it only does where the song is read as part of
 *   the library. A setlist is the list somebody wrote down to play from, and a song reached through one is taken out
 *   of it rather than removed from every setlist and the library at once.
 * @param leadingItems Entries that belong to the row rather than to the song, put before the song's own: moving a row
 *   of a setlist up or down, and taking it out of the setlist.
 */
@Composable
internal fun SongActionsButton(
    modifier: Modifier = Modifier,
    state: OverflowMenuState = rememberOverflowMenuState(),
    viewModel: CampfireViewModel,
    song: Song,
    isDeletable: Boolean,
    leadingItems: @Composable (select: (action: () -> Unit) -> Unit) -> Unit = {},
) {
    val filePicker = LocalFilePicker.current
    ActionsMenu(
        modifier = modifier,
        state = state,
    ) { select ->
        leadingItems(select)
        // Each entry acts through `select`, which closes the menu before it acts - so that it is gone by the time the
        // dialog or the picker it opens is on the screen - and only once.
        ActionsMenuItem(
            title = stringResource(Res.string.songs_edit_song),
            icon = painterResource(Res.drawable.ic_edit),
            onClick = { select { viewModel.openEditor(song.fileName) } },
        )
        // Only where it would do something: a file already named after its own metadata, or one with no title to be
        // named after, has nothing to update and the entry would be an offer that never comes to anything.
        if (song.canUpdateFileName) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_update_file_name),
                icon = painterResource(Res.drawable.ic_rename),
                onClick = { select { viewModel.updateSongFileName(song) } },
            )
        }
        ActionsMenuItem(
            title = stringResource(Res.string.songs_export_song),
            icon = painterResource(Res.drawable.ic_export),
            onClick = { select { viewModel.exportSong(filePicker, song.fileName) } },
        )
        // Only where sending a file is a different thing from saving one, which on desktop and the web it is not.
        if (filePicker.canShare) {
            ActionsMenuItem(
                title = stringResource(Res.string.share),
                icon = painterResource(Res.drawable.ic_share),
                onClick = { select { viewModel.shareSong(filePicker, song.fileName) } },
            )
        }
        if (isDeletable) {
            ActionsMenuItem(
                title = stringResource(Res.string.songs_delete_song),
                icon = painterResource(Res.drawable.ic_delete),
                onClick = { select { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSong(song)) } },
            )
        }
    }
}

/**
 * The way into the setlist assignments sheet, put in front of [SongActionsButton] wherever a song is read as part of
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

