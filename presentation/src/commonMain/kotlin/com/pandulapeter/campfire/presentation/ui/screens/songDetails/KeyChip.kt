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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_music_note
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_capo_short
import com.pandulapeter.campfire.presentation.resources.song_details_dock_key
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_playing
import com.pandulapeter.campfire.presentation.resources.song_details_dock_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockControlPill
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

/**
 * The app bar's key chip: one outlined tonal control ([DockControlPill], the player's controls' way of saying "this opens
 * a sheet", as the dock's tempo chip is), a rounded rectangle rather than a pill so that its two lines are not cut by
 * round ends. A note mark leads it in every state, as the metronome mark leads the tempo chip, so that a key alone still
 * reads as the control it is. Then the key the page's chords are in, in the colour chords are written in, a moved song's
 * semitones muted and smaller after it in parentheses ("G# (+1)", which bare would read as a step up from G#), and the
 * capo wherever it is not zero on a second, smaller line under it ("Capo 2", shortened where a language's word for it is
 * long), so that a capo is never out of sight. Two short lines rather than one long one, so that the chip takes as little
 * of the title's room as it can: a phone's bar holds the title, the chip, the star, and the overflow button side by
 * side. As tall as a stepper ([STEPPER_HEIGHT]), more at a large system text size. The whole of it opens the Playing
 * sheet ([onClick]), none in read only mode, whose chip only reads and so drops its fill and outline. A song with no key that the chip still opens the
 * sheet for reads "Key", muted. A new key crossfades, and the capo's line grows in and shrinks away.
 */
@Composable
internal fun KeyChip(
    modifier: Modifier = Modifier,
    text: KeyChipText,
    onClick: (() -> Unit)?,
) {
    // A song whose key is not known is still named for what the chip opens with "Key", muted, rather than "Playing",
    // which read as a state.
    val keyLabel = stringResource(Res.string.song_details_dock_key)
    // Kept while it shrinks away, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    text.capo?.let { lastCapo.value = it }
    DockControlPill(
        modifier = modifier.heightIn(min = STEPPER_HEIGHT),
        shape = RoundedCornerShape(KEY_CHIP_CORNER),
        onClickLabel = stringResource(Res.string.song_details_dock_open_playing),
        onClick = onClick,
        isFramed = onClick != null,
    ) {
        val key = text.key ?: keyLabel.takeIf { onClick != null }
        // One description for the whole pill: the clickable merges its children, and a description of the key alone
        // would hide the transposition and the capo from a screen reader, which is read in full rather than shortened.
        val description = listOfNotNull(
            text.key?.let { textResource(Res.string.song_details_key_change, it) } ?: key,
            text.transposition?.let { stringResource(Res.string.song_details_dock_transposition, it) },
            stringResource(Res.string.song_details_capo, lastCapo.value).takeIf { text.capo != null },
        ).joinToString(", ")
        Row(
            modifier = Modifier
                .clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = KEY_CHIP_PADDING),
            horizontalArrangement = Arrangement.spacedBy(KEY_CHIP_ICON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(KEY_CHIP_ICON_SIZE),
                painter = painterResource(Res.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (key != null) KeyLine(key = key, text = text)
                AnimatedVisibility(
                    visible = text.capo != null,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Text(
                        text = stringResource(Res.string.song_details_capo_short, lastCapo.value),
                        // Alone in the chip (read only mode's song with a capo and no key) it is the chip's only line.
                        style = if (key == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The key [KeyChip] names, muted where it is only the chip's label, and a moved song's semitones small after it, in parentheses. */
@Composable
private fun KeyLine(
    key: String,
    text: KeyChipText,
) = AnimatedContent(
    targetState = key to text.transposition,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
) { (shownKey, shownTransposition) ->
    Row(horizontalArrangement = Arrangement.spacedBy(TRANSPOSITION_GAP)) {
        Text(
            modifier = Modifier.alignByBaseline(),
            text = shownKey,
            style = MaterialTheme.typography.labelLarge,
            color = if (text.key == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalSecondAccentColor.current,
            maxLines = 1,
        )
        if (shownTransposition != null) {
            Text(
                modifier = Modifier.alignByBaseline(),
                // Isolated, or a sentence read from the right would draw "(+2)" as "(2+)".
                text = "($shownTransposition)".isolatedLeftToRight(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The capo [KeyChip] last named. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0)

private val KEY_CHIP_PADDING = 8.dp
private val KEY_CHIP_ICON_SIZE = 16.dp
private val KEY_CHIP_ICON_GAP = 4.dp
private val TRANSPOSITION_GAP = 2.dp

/** Moderate corners: a pill's round ends would cut into the capo's line under the key. */
private val KEY_CHIP_CORNER = 12.dp
