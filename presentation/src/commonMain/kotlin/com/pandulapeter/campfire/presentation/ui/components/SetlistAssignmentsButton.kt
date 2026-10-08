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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_choose_setlists
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_setlists_outline
import org.jetbrains.compose.resources.painterResource

/**
 * The way into the setlist assignments sheet, put in front of [SongActions] on every row of the songs screen and on
 * the song details screen, since filing songs into setlists is what the library is mostly visited for, and the details
 * screen offers it the same way whether it was opened from the library or from a setlist. A setlist row has the
 * [setlistAssignmentsAction] in its menu instead, since the song there is already filed and the star would always be
 * full.
 *
 * @param isInSetlist Whether the song is in at least one setlist, which fills the star. Passed in rather than collected
 *   here, since the button is in every row of the song list and one collection per screen answers them all.
 * @param setlistFileName The setlist the song is being read through, whose box the sheet shows but does not let go
 *   of: taking the song out of the setlist it is being read in would pull the screen out from under the reader.
 */
@Composable
internal fun SetlistAssignmentsButton(
    modifier: Modifier = Modifier,
    actions: SongActionHandler,
    song: Song,
    isInSetlist: Boolean,
    setlistFileName: String? = null,
) = IconButton(
    modifier = modifier,
    onClick = { actions.chooseSetlists(song, setlistFileName) },
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
            contentDescription = stringResource(Res.string.songs_choose_setlists),
        )
        Icon(
            modifier = Modifier.graphicsLayer { alpha = filledAlpha },
            painter = painterResource(Res.drawable.ic_setlists),
            contentDescription = null,
        )
    }
}

/** [SetlistAssignmentsButton] as an entry of a menu, for an app bar that has run out of room for the button. */
@Composable
internal fun setlistAssignmentsAction(
    actions: SongActionHandler,
    song: Song,
    isInSetlist: Boolean,
    setlistFileName: String? = null,
) = ActionsMenuItem(
    title = stringResource(Res.string.songs_choose_setlists),
    icon = painterResource(if (isInSetlist) Res.drawable.ic_setlists else Res.drawable.ic_setlists_outline),
    onClick = { actions.chooseSetlists(song, setlistFileName) },
)

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
