/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_capo_shapes
import com.pandulapeter.campfire.presentation.resources.song_details_dock_key
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_playing
import com.pandulapeter.campfire.presentation.resources.song_details_dock_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The key chip that starts the dock: one tonal pill ([DockControlPill], the dock's way of saying "this opens a sheet",
 * set apart from the outlined chord and scale chips that are the song's) naming the key the page's chords are in, in the
 * colour chords are written in, a moved song's semitones after it in parentheses ("Bm (+2)"), and after a stepper's
 * divider the capo wherever it is not zero, so that a capo
 * is never out of sight. The whole pill opens the Playing sheet ([onClick]), none in read only mode, whose chip only
 * reads. A song with no key that the chip still opens the sheet for reads "Key", muted. A new key crossfades, and
 * the capo slides in and out with its divider.
 *
 * [isTall] where it stands alone at the start of the diagram row, as tall as the tempo chip at its end: its text keeps
 * its size, the capo on a line of its own under the key, centred, naming the key of the shapes fingered above it where
 * the key names what sounds ("Capo 2 (Am shapes)"), and growing in and shrinking away as the capo comes and goes.
 * [isOutlined] false for the app bar's (`AppBarPlayingTools`), which stands beside the text size stepper, not on the dock.
 */
@Composable
internal fun DockKeyChip(
    modifier: Modifier = Modifier,
    dockKey: DockKey,
    height: Dp,
    onClick: (() -> Unit)?,
    isTall: Boolean = false,
    isOutlined: Boolean = true,
) {
    // A song whose key is not known is still named for what the chip opens with "Key", muted, rather than "Playing",
    // which beside the tempo chip's play and stop read as a state.
    val keyLabel = stringResource(Res.string.song_details_dock_key)
    // Kept while it slides out, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    dockKey.capo?.let {
        lastCapo.value = it
        lastCapo.shapesKey = dockKey.shapesKey?.takeIf { isTall }
    }
    DockControlPill(
        modifier = modifier.height(height),
        onClickLabel = stringResource(Res.string.song_details_dock_open_playing),
        onClick = onClick,
        isOutlined = isOutlined,
    ) {
        val key = dockKey.key ?: keyLabel.takeIf { onClick != null }
        val capoLine = capoLineOf(lastCapo)
        // One description for the whole pill: the clickable merges its children, and a description of the key alone
        // would hide the transposition and the capo from a screen reader.
        val description = listOfNotNull(
            dockKey.key?.let { textResource(Res.string.song_details_key_change, it) } ?: key,
            dockKey.transposition?.let { stringResource(Res.string.song_details_dock_transposition, it) },
            capoLine.takeIf { dockKey.capo != null },
        ).joinToString(", ")
        if (isTall) {
            Column(
                modifier = Modifier
                    .clearAndSetSemantics { contentDescription = description }
                    .padding(horizontal = KEY_CHIP_PADDING),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (key != null) KeyLine(key = key, dockKey = dockKey)
                AnimatedVisibility(
                    visible = dockKey.capo != null,
                    enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                    exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center),
                ) {
                    Text(text = capoLine, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        } else {
            // Centred, for where it is stretched to the width of the tuner pill under it (DockKeyAndTuner).
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (key != null) KeyLine(modifier = Modifier.padding(horizontal = KEY_CHIP_PADDING), key = key, dockKey = dockKey)
                AnimatedVisibility(
                    modifier = Modifier.fillMaxHeight(),
                    visible = dockKey.capo != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (key != null) StepperDivider()
                        Text(
                            modifier = Modifier.padding(horizontal = KEY_CHIP_PADDING),
                            text = capoLine,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The key [DockKeyChip] names, muted where it is only the chip's label, and a moved song's semitones after it. */
@Composable
private fun KeyLine(
    modifier: Modifier = Modifier,
    key: String,
    dockKey: DockKey,
) = AnimatedContent(
    modifier = modifier,
    targetState = key to dockKey.transposition,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
) { (shownKey, shownTransposition) ->
    Row(horizontalArrangement = Arrangement.spacedBy(TRANSPOSITION_GAP), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = shownKey,
            style = MaterialTheme.typography.labelLarge,
            color = if (dockKey.key == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalSecondAccentColor.current,
            maxLines = 1,
        )
        if (shownTransposition != null) {
            Text(
                // Isolated, or a sentence read from the right would draw "(+2)" as "(2+)".
                text = "($shownTransposition)".isolatedLeftToRight(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** What [DockKeyChip] says of the capo it last named: "Capo 2", or "Capo 2 (Am shapes)" with the key of its shapes. */
@Composable
private fun capoLineOf(lastCapo: LastCapo): String {
    val capo = stringResource(Res.string.song_details_capo, lastCapo.value)
    return lastCapo.shapesKey?.let { textResource(Res.string.song_details_capo_shapes, capo, it) } ?: capo
}

/** The capo [DockKeyChip] last named, and the key of its shapes. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0, var shapesKey: String? = null)

private val KEY_CHIP_PADDING = 12.dp
private val TRANSPOSITION_GAP = 4.dp
