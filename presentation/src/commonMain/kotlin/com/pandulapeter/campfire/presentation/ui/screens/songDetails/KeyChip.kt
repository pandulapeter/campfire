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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_capo_short
import com.pandulapeter.campfire.presentation.resources.song_details_dock_key
import com.pandulapeter.campfire.presentation.resources.song_details_dock_key_short
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_playing
import com.pandulapeter.campfire.presentation.resources.song_details_dock_shapes_short
import com.pandulapeter.campfire.presentation.resources.song_details_dock_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.resources.song_details_shapes_of
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.ControlPillFrame
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockControlPill
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The app bar's key chip: one outlined control with no fill ([DockControlPill], [ControlPillFrame.OUTLINED]: a fill made
 * it read as one of the song's section headers, which are filled pills), a rounded rectangle rather than a pill so that
 * its two lines are not cut by round ends. Small muted words name the key, before or after it as the language orders
 * them ("Key of G", "Hn. G"; [keyLabelOf]), so that a key alone still reads as the control it is (shortened where a
 * language's words are long, read in full). The key the page's chords are in is in the colour chords are written in, a
 * moved song's semitones muted and smaller after the words in parentheses ("Key of G# (+1)", which bare would read as a step
 * up from G#), and the capo wherever it is not zero on a second, smaller line under it ("Capo 2", shortened where a
 * language's word for it is long), so that a capo is never out of sight. Two short lines rather than one long one, so
 * that the chip takes as little of the title's room as it can: a phone's bar holds the title, the chip, the star, and the
 * overflow button side by side. As tall as a stepper ([STEPPER_HEIGHT]), more at a large system text size. The whole of
 * it opens the Playing sheet ([onClick]), none in read only mode, whose chip only reads and so drops its outline too,
 * fading it. A song with no key that the chip still opens the sheet for reads "Key" alone, muted. Where the key names
 * the shapes fingered above a capo ([KeyChipText.namesShapes]) the words name the shapes instead ("Em shapes"), since the title's
 * subtitle then names the key that sounds and two keys for one song would leave the reader to guess which is which. A
 * new key or label crossfades, and the capo's line grows in and shrinks away.
 */
@Composable
internal fun KeyChip(
    modifier: Modifier = Modifier,
    text: KeyChipText,
    onClick: (() -> Unit)?,
) {
    val keyLabel = stringResource(Res.string.song_details_dock_key)
    // Kept while it shrinks away, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    text.capo?.let { lastCapo.value = it }
    DockControlPill(
        modifier = modifier.heightIn(min = STEPPER_HEIGHT),
        shape = RoundedCornerShape(KEY_CHIP_CORNER),
        frame = if (onClick != null) ControlPillFrame.OUTLINED else ControlPillFrame.NONE,
        onClickLabel = stringResource(Res.string.song_details_dock_open_playing),
        onClick = onClick,
    ) {
        // One description for the whole pill: the clickable merges its children, and a description of the key alone
        // would hide the transposition and the capo from a screen reader, which is read in full rather than shortened.
        val description = listOfNotNull(
            text.key?.let { textResource(if (text.namesShapes) Res.string.song_details_shapes_of else Res.string.song_details_key_change, it) } ?: keyLabel.takeIf { text.keyLine == KeyChipLine.LABEL_ALONE },
            text.transposition?.let { stringResource(Res.string.song_details_dock_transposition, it) },
            stringResource(Res.string.song_details_capo, lastCapo.value).takeIf { text.capo != null },
        ).joinToString(", ")
        Column(
            modifier = Modifier
                .clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = KEY_CHIP_PADDING),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val keyLine = text.keyLine
            when (keyLine) {
                KeyChipLine.LABELLED_KEY -> KeyLine(
                    // The template as it is written, its words placed around the key by the language.
                    label = keyLabelOf(textResource(if (text.namesShapes) Res.string.song_details_dock_shapes_short else Res.string.song_details_dock_key_short)),
                    key = text.key.orEmpty(),
                    transposition = text.transposition,
                )
                // A song whose key is not known is still named for what the chip opens, rather than "Playing", which
                // read as a state.
                KeyChipLine.LABEL_ALONE -> Text(
                    text = keyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                KeyChipLine.NONE -> Unit
            }
            AnimatedVisibility(
                visible = text.capo != null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Text(
                    text = stringResource(Res.string.song_details_capo_short, lastCapo.value),
                    // Alone in the chip (read only mode's song with a capo and no key) it is the chip's only line.
                    style = if (keyLine == KeyChipLine.NONE) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The [key] [KeyChip] names, its small muted [label]'s words before or after it as the language orders them ("Key of G",
 * "G shapes"), and a moved song's semitones ([transposition]) small after them, in parentheses. Each part crossfades on
 * its own, so that a word that stays stands still while a capo put on or taken off turns "Key of" into "shapes".
 */
@Composable
private fun KeyLine(
    label: KeyLabel,
    key: String,
    transposition: String?,
) = Row(
    // The words are centred on the key rather than set on its baseline: on one baseline the small label sat low, reading
    // as a subscript of the key rather than as its caption.
    verticalAlignment = Alignment.CenterVertically,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    CrossfadingText(text = label.before, style = labelStyle, color = labelColor, end = if (label.isBeforeSpaced) KEY_LABEL_GAP else 0.dp)
    CrossfadingText(text = key, style = MaterialTheme.typography.labelLarge, color = LocalSecondAccentColor.current)
    CrossfadingText(text = label.after, style = labelStyle, color = labelColor, start = if (label.isAfterSpaced) KEY_LABEL_GAP else 0.dp)
    // Isolated, or a sentence read from the right would draw "(+2)" as "(2+)".
    CrossfadingText(text = transposition?.let { "($it)".isolatedLeftToRight() }.orEmpty(), style = labelStyle, color = labelColor, start = TRANSPOSITION_GAP)
}

/**
 * One part of [KeyLine]: [text] crossfading into its next value and growing or shrinking to it, nothing where it is
 * empty, its gap from its neighbours ([start], [end]) going with it.
 */
@Composable
private fun CrossfadingText(
    text: String,
    style: TextStyle,
    color: Color,
    start: Dp = 0.dp,
    end: Dp = 0.dp,
) = AnimatedContent(
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.Center,
) { shownText ->
    if (shownText.isNotEmpty()) {
        Text(
            modifier = Modifier.padding(start = start, end = end),
            text = shownText,
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

/** The capo [KeyChip] last named. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0)

private val KEY_CHIP_PADDING = 8.dp
private val KEY_LABEL_GAP = 3.dp
private val TRANSPOSITION_GAP = 2.dp

/** Moderate corners: a pill's round ends would cut into the capo's line under the key. */
private val KEY_CHIP_CORNER = 12.dp
