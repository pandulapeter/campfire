/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument in the player's hands and its tuning as one tonal pill of two halves split by a stepper's divider, each
 * opening its own sheet: the instrument's name, and after it the tuning's ("Drop D", its note in the chord [notation])
 * for an instrument with more than one, so that the tuning is a choice in sight rather than one at the end of a long
 * list. Two choices rather than one, since diagrams for the wrong instrument or tuning look just as right as the ones for
 * the right one. It is exactly as tall as a song's playing controls set in [style] are (`songControlHeight`), so that on a
 * song's page it lines up with the transposition stepper under it, and keeps to one line: the tuning, the longer of the
 * two names, is cut short where the line is too narrow for both. The tuning half fades in and out with the instrument's
 * tunings and the pill resizes with it.
 *
 * @param tuningVisibility Where the tuning half fades in and out, for a caller that measures the pill and so has to know
 * when a leaving half is no longer laid out; its target is kept by the caller at whether there is a tuning to show.
 */
@Composable
internal fun InstrumentPill(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    isEnabled: Boolean = true,
    tuningVisibility: MutableTransitionState<Boolean>? = null,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = Surface(
    // Dimmed the way the list items of Settings are while their section is switched off.
    modifier = modifier.height(songControlHeight(style)).alpha(if (isEnabled) 1f else 0.5f),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    // Only the tuning half coming and going and a name changing animate the pill's width, each by itself. Animating every
    // change of the row's size instead would have the pill trail behind its text while a pinch scales a song's page.
    Row(
        modifier = Modifier.padding(horizontal = INSTRUMENT_PILL_EDGE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentPillHalf(
            text = instrumentLabel(instrument),
            onClickLabel = stringResource(Res.string.instrument_choose),
            style = style,
            isEnabled = isEnabled,
            onClick = onInstrumentClicked,
        )
        val shownTuning = tuning?.takeIf { instrument.tunings.size > 1 }
        // Kept while it fades out, so that it leaves as it was rather than as nothing. Not a state: nothing is redrawn
        // because of it.
        val lastShownTuning = remember { LastTuning() }
        shownTuning?.let { lastShownTuning.value = it }
        AnimatedVisibility(
            modifier = Modifier.weight(1f, fill = false),
            visibleState = tuningVisibility ?: remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null },
            enter = fadeIn() + expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = fadeOut() + shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            val lastTuning = lastShownTuning.value ?: return@AnimatedVisibility
            // A leaving half names a tuning the instrument picked now does not have, so it neither opens the sheet nor is
            // offered as a button; it is not disabled for it, which would dim it in one frame in the middle of its fade.
            val isLeaving = shownTuning == null
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperDivider()
                InstrumentPillHalf(
                    modifier = if (isLeaving) Modifier.clearAndSetSemantics { } else Modifier,
                    // Named in full where it is the standard one, which "Standard" alone does not say out of the sheet's
                    // context. Asked of the tuning rather than of [instrument], since a half fading out is still drawing
                    // the tuning of the instrument picked before, and the one picked now may have no tunings at all (the
                    // keyboard, Other).
                    text = if (lastTuning.isStandard) {
                        stringResource(Res.string.instrument_tuning_standard)
                    } else {
                        tuningLabel(lastTuning, notation)
                    },
                    onClickLabel = stringResource(Res.string.instrument_tuning_choose),
                    style = style,
                    isEnabled = isEnabled,
                    onClick = { if (!isLeaving) onTuningClicked() },
                )
            }
        }
    }
}

/** One half of an [InstrumentPill]: the whole height of the pill is its touch target, its name crossfading as it changes. */
@Composable
private fun InstrumentPillHalf(
    modifier: Modifier = Modifier,
    text: String,
    onClickLabel: String,
    style: TextStyle,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = Box(
    modifier = modifier
        .fillMaxHeight()
        .clickable(enabled = isEnabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .padding(horizontal = INSTRUMENT_PILL_HALF_PADDING),
    contentAlignment = Alignment.Center,
) {
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
    ) { shownText ->
        Text(
            text = shownText,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the tuning half of [InstrumentPill] last named. */
private class LastTuning(var value: InstrumentTuning? = null)

/** Between the pill's rounded ends and the halves, whose own touch targets would otherwise run into the curve. */
private val INSTRUMENT_PILL_EDGE_PADDING = 4.dp

/** On either side of each name in an [InstrumentPill]. */
private val INSTRUMENT_PILL_HALF_PADDING = 12.dp
