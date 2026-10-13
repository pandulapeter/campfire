/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_message
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_strings
import com.pandulapeter.campfire.presentation.resources.onboarding_instrument_title
import com.pandulapeter.campfire.presentation.ui.components.InstrumentChipRow
import com.pandulapeter.campfire.presentation.ui.instrument.tuningStringsLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * "What do you play?", the one instrument the chord diagrams and the tuner are set up for ([UserPreferences.instrument])
 * and its tuning, picked through the same two chips and sheets the song details screen, the Tuner, the Chords tab, and
 * Settings offer, so that the onboarding teaches the control the player will meet again rather than one of its own. It
 * is asked before the features, since the next page offers the chord diagrams only for an instrument they are drawn for.
 * The chips show what the app is set to now, a fresh installation's six-string guitar included.
 *
 * Under them the answer is shown on what it changes: a few chords drawn for it in [chordTuning], where it has diagrams,
 * and the open strings the tuner will listen for, where it has a tuning, crossfading as either is picked again.
 */
@Composable
internal fun InstrumentStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    chordTuning: ChordTuning?,
    notation: ChordNotation,
    isCompact: Boolean,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_instrument_title),
    message = stringResource(Res.string.onboarding_instrument_message),
    isCompact = isCompact,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Centered, so its width is animated: the tuning chip only fades, and the instrument's would otherwise jump
        // sideways as it comes and goes.
        InstrumentChipRow(
            modifier = Modifier.animateContentSize(),
            instrument = instrument,
            tuning = tuning,
            notation = notation,
            onInstrumentClicked = onInstrumentClicked,
            onTuningClicked = onTuningClicked,
        )
    }
    InstrumentPreview(
        chordTuning = chordTuning,
        tuning = tuning,
        notation = notation,
    )
}

/**
 * The chords and the open strings under the chips, as wide as the page whatever it holds, so that a change only animates
 * its height.
 */
@Composable
private fun InstrumentPreview(
    chordTuning: ChordTuning?,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
) = AnimatedContent(
    modifier = Modifier.fillMaxWidth(),
    targetState = InstrumentPreviewState(chordTuning = chordTuning, tuning = tuning),
    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    contentAlignment = Alignment.TopCenter,
) { state ->
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.chordTuning?.let { OnboardingChordPreview(tuning = it) }
        state.tuning?.let {
            Text(
                text = stringResource(Res.string.onboarding_instrument_strings, tuningStringsLabel(it, notation)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class InstrumentPreviewState(
    val chordTuning: ChordTuning?,
    val tuning: InstrumentTuning?,
)
