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

import com.pandulapeter.campfire.presentation.ui.components.controls.PickerChip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.isStandard
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument in the player's hands and its tuning as two [PickerChip]s, each opening its own sheet: the instrument's
 * name, and after it the tuning's ("Drop D", its note in the chord [notation]) for an instrument with more than one, so
 * that the tuning is a choice in sight rather than one at the end of a long list. Two choices rather than one, since
 * diagrams for the wrong instrument or tuning look just as right as the ones for the right one. Emits the chips without
 * a container of its own, so that the row or the flow they are put in lays them out; [chipModifier] goes on both, and
 * the tuning chip fades in and out with the instrument's tunings.
 *
 * @param tuningChipVisibility Where the tuning chip fades in and out, for a caller that measures the chips and so has to
 * know when a leaving chip is no longer laid out; its target is kept by the caller at whether there is a tuning to show.
 */
@Composable
internal fun InstrumentChips(
    chipModifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    height: Dp = 40.dp,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    chevronSize: Dp = 20.dp,
    isEnabled: Boolean = true,
    tuningChipVisibility: MutableTransitionState<Boolean>? = null,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) {
    PickerChip(
        modifier = chipModifier,
        text = instrumentLabel(instrument),
        onClickLabel = stringResource(Res.string.instrument_choose),
        height = height,
        style = style,
        chevronSize = chevronSize,
        isEnabled = isEnabled,
        onClick = onInstrumentClicked,
    )
    val shownTuning = tuning?.takeIf { instrument.tunings.size > 1 }
    // Kept while it fades out, so that it leaves as it was rather than as nothing. Not a state: nothing is redrawn
    // because of it.
    val lastShownTuning = remember { LastTuning() }
    shownTuning?.let { lastShownTuning.value = it }
    AnimatedVisibility(
        visibleState = tuningChipVisibility ?: remember { MutableTransitionState(shownTuning != null) }.apply { targetState = shownTuning != null },
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val lastTuning = lastShownTuning.value ?: return@AnimatedVisibility
        // A leaving chip names a tuning the instrument picked now does not have, so it neither opens the sheet nor is
        // offered as a button; it is not disabled for it, which would dim it in one frame in the middle of its fade.
        val isLeaving = shownTuning == null
        PickerChip(
            modifier = if (isLeaving) chipModifier.clearAndSetSemantics { } else chipModifier,
            // Named in full where it is the standard one, which "Standard" alone does not say out of the sheet's context. Asked
            // of the tuning rather than of [instrument], since a chip fading out is still drawing the tuning of the
            // instrument picked before, and the one picked now may have no tunings at all (the keyboard, Other).
            text = if (lastTuning.isStandard) {
                stringResource(Res.string.instrument_tuning_standard)
            } else {
                tuningLabel(lastTuning, notation)
            },
            onClickLabel = stringResource(Res.string.instrument_tuning_choose),
            height = height,
            style = style,
            chevronSize = chevronSize,
            isEnabled = isEnabled,
            onClick = { if (!isLeaving) onTuningClicked() },
        )
    }
}

/** What the tuning chip of [InstrumentChips] last named. */
private class LastTuning(var value: InstrumentTuning? = null)
