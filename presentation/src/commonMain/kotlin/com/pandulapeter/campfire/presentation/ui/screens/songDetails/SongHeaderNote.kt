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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_dot
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

/**
 * One of the things the app bar says about how the song on screen is played, after the artist and separated from it by
 * the dot a song card uses: the key it sounds in, in the accent color that color is kept for, and the tempo it is
 * played at. Crossfaded where it stands and the line closing up around it, since both change under the reader — a
 * transposition, a capo or a tempo stepped in the song's own first section, a preference synced in from another device
 * — and since the chords switched off take the key away altogether.
 *
 * A 16dp dot rather than the cards' 24dp one: the two lines of the title are exactly as tall as the cover beside them,
 * and a 24dp box on the lower one grows the bar and with it the room the lyrics are laid out in.
 *
 * @param text Null leaves the place empty, which is what a song that names no key or tempo gets.
 * @param description What it is read out as, where the text alone says nothing: a key is two letters, while a tempo
 *   already reads as a tempo.
 * @param hasPrecedingContent Whether anything stands in front of it for the dot to separate it from.
 * @param isPending Whether it names a change the click is still waiting to make, which it says by pulsing for as long
 *   as it waits.
 * @param onClick What a tap on it does, where anything: its own target inside the title's, which opens the sheet of
 *   what the song is.
 */
@Composable
internal fun SongHeaderNote(
    text: String?,
    isEmphasized: Boolean,
    hasPrecedingContent: Boolean,
    description: String? = null,
    isPending: Boolean = false,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) = AnimatedContent(
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { currentText ->
    if (currentText != null) {
        Row(
            modifier = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPrecedingContent) {
                Icon(
                    modifier = Modifier.size(APP_BAR_NOTE_DOT_SIZE),
                    painter = painterResource(Res.drawable.ic_dot),
                    contentDescription = null,
                )
            }
            val pendingAlpha = if (isPending) pendingPulseAlpha() else 1f
            Text(
                modifier = (if (description == null) Modifier else Modifier.semantics { contentDescription = description })
                    .graphicsLayer { alpha = pendingAlpha },
                text = currentText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isEmphasized) LocalSecondAccentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A pulse between full and half strength, for as long as the note it is read by is composed. */
@Composable
private fun pendingPulseAlpha(): Float {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = PENDING_MIN_ALPHA,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = PENDING_PULSE_MILLIS), repeatMode = RepeatMode.Reverse),
    )
    return alpha
}

private const val PENDING_MIN_ALPHA = 0.5f
private const val PENDING_PULSE_MILLIS = 600

/** The dot that separates the key and the tempo from the artist, see [SongHeaderNote]. */
private val APP_BAR_NOTE_DOT_SIZE = 16.dp
