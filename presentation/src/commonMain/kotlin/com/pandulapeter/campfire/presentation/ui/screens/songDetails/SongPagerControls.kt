/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.songView.grid.pageCount
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.song_details_next_song
import com.pandulapeter.campfire.presentation.resources.song_details_previous_song
import com.pandulapeter.campfire.presentation.resources.song_details_song_position
import com.pandulapeter.campfire.presentation.resources.song_details_step_again
import com.pandulapeter.campfire.presentation.resources.song_details_up_next
import org.jetbrains.compose.resources.painterResource

/**
 * The bar under the lyrics that steps through the songs of a setlist without swiping. Between the two buttons it
 * names the setlist being played and how far along it the current song is, or, while a step past the end of the song
 * waits for the press that confirms it ([NextSongConfirmation]), the song that press goes to: the bar is there in every
 * setlist and covers nothing of the song, and it turns the secondary container colour so the change is seen from a
 * stand. Selecting it then goes on as that press would.
 *
 * @param setlistSlots Where each page sits in the setlist, which is what the label numbers it by; null where the
 *   pages are numbered by themselves.
 * @param currentPage The song on screen, which is what the label names.
 * @param targetPage The song the pager is on its way to, which is what decides whether there is anywhere left to step.
 * @param nextSongTitle The title of the next song while a step to it waits for its confirmation, else null.
 */
@Composable
internal fun SongPagerControls(
    setlistTitle: String?,
    setlistSlots: SetlistSlots?,
    currentPage: Int,
    targetPage: Int,
    pageCount: Int,
    contentPadding: PaddingValues,
    nextSongTitle: String?,
    onStep: (Int) -> Unit,
    onConfirmNextSong: () -> Unit,
) = Surface(
    color = animateColorAsState(
        if (nextSongTitle == null) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
    ).value,
) {
    val layoutDirection = LocalLayoutDirection.current
    // The arrow keys go to the song lying their way in the pager, so in a right-to-left layout Right is the previous one.
    val isLtr = layoutDirection == LayoutDirection.Ltr
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 4.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                bottom = contentPadding.calculateBottomPadding(),
            )
            .height(PAGER_CONTROLS_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CampfireIconButton(
            icon = painterResource(Res.drawable.ic_previous),
            label = stringResource(Res.string.song_details_previous_song),
            shortcut = if (isLtr) KeyShortcut.SONG_SWITCH_LEFT else KeyShortcut.SONG_SWITCH_RIGHT,
            enabled = targetPage > 0,
            onClick = { onStep(-1) },
        )
        AnimatedContent(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            targetState = nextSongTitle,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.Center,
        ) { title ->
            if (title == null) {
                SetlistPosition(
                    setlistTitle = setlistTitle,
                    setlistSlots = setlistSlots,
                    currentPage = currentPage,
                    pageCount = pageCount,
                )
            } else {
                NextSongPrompt(
                    title = title,
                    onClick = onConfirmNextSong,
                )
            }
        }
        CampfireIconButton(
            icon = painterResource(Res.drawable.ic_next),
            label = stringResource(Res.string.song_details_next_song),
            shortcut = if (isLtr) KeyShortcut.SONG_SWITCH_RIGHT else KeyShortcut.SONG_SWITCH_LEFT,
            enabled = targetPage < pageCount - 1,
            onClick = { onStep(1) },
        )
    }
}

/** The setlist's name and the current song's place in it. Only the name gives way: the position is short and always worth showing in full. */
@Composable
private fun SetlistPosition(
    setlistTitle: String?,
    setlistSlots: SetlistSlots?,
    currentPage: Int,
    pageCount: Int,
) = Row(
    modifier = Modifier.fillMaxSize(),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
) {
    if (setlistTitle != null) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = setlistTitle,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Separator(color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedContent(
        targetState = currentPage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { page ->
        val position = setlistSlots?.slotByPage?.getOrNull(page)
        Text(
            text = if (position != null) {
                stringResource(Res.string.song_details_song_position, position + 1, setlistSlots.entryCount)
            } else {
                stringResource(Res.string.song_details_song_position, page + 1, pageCount)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The song the next step goes to, and that it takes one more. Only the title gives way, since the hint is what the
 * prompt is for. A polite live region, as it appears on its own.
 */
@Composable
private fun NextSongPrompt(
    title: String,
    onClick: () -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxSize()
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.weight(1f, fill = false),
        text = textResource(Res.string.song_details_up_next, title),
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Separator(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = HINT_ALPHA))
    Text(
        text = stringResource(Res.string.song_details_step_again),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = HINT_ALPHA),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun Separator(color: Color) = Text(
    modifier = Modifier.padding(horizontal = 8.dp),
    text = LABEL_SEPARATOR,
    style = MaterialTheme.typography.labelLarge,
    color = color,
)

private const val LABEL_SEPARATOR = "·"

/** The hint is quieter than the title it follows, the way onSurfaceVariant is beside onSurface, which the container has no variant of. */
private const val HINT_ALPHA = 0.8f

private val PAGER_CONTROLS_HEIGHT = 48.dp
