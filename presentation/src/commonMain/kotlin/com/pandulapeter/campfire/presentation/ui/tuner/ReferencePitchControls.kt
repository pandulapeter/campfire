/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.tuner_play_reference
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_decrease
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_increase
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_reset
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_value
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperEntry
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.NEW_BUTTON_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeContent
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeElement
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.rememberSharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChrome
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import com.pandulapeter.campfire.tuner.api.Pitch
import org.jetbrains.compose.resources.painterResource

/**
 * The frequency of A4 everything is tuned from, stepped by a hertz or typed the way the tempo is (and reset to 440 from
 * the focused value the same way), and a toggle that plays that A until it is tapped again: the pill and the filled
 * circle of the list screens' bar, where they stand on the Tuner tab (see `ToolInstrumentSetting`), under the instrument
 * where the line is too narrow for both, the pill as tall as the instrument's beside it, as on a song's page, and under the strings of song details' tuner sheet. There is no label to read; a
 * screen reader hears the note with the frequency.
 *
 * @param isTabChromePart Whether the circle is the Tuner tab's, which a tab swap carries over as the New button.
 */
@Composable
internal fun ReferencePitchControls(
    modifier: Modifier = Modifier,
    referencePitch: Int,
    notation: ChordNotation,
    isReferenceTonePlaying: Boolean,
    onReferencePitchChanged: (Int) -> Unit,
    onToggleReferenceTone: () -> Unit,
    isTabChromePart: Boolean = false,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(NEW_ITEM_BUTTON_GAP),
) {
    Stepper(
        // As tall as the instrument pill at the start of the line, as a song's steppers are as tall as its pill.
        height = songControlHeight(MaterialTheme.typography.titleSmall),
        value = stringResource(Res.string.tuner_reference_pitch_value, referencePitch),
        valueDescription = stringResource(Res.string.tuner_reference_pitch, noteNameWithOctave(REFERENCE_NOTE, notation), referencePitch),
        widestValues = Pitch.REFERENCE_PITCH_RANGE.let { listOf(it.first, it.last) }.map { stringResource(Res.string.tuner_reference_pitch_value, it) },
        isDefault = referencePitch == Pitch.DEFAULT_REFERENCE_PITCH,
        decreaseIcon = painterResource(Res.drawable.ic_subtract),
        decreaseLabel = stringResource(Res.string.tuner_reference_pitch_decrease),
        canDecrease = referencePitch > Pitch.REFERENCE_PITCH_RANGE.first,
        onDecrease = { onReferencePitchChanged(referencePitch - 1) },
        increaseIcon = painterResource(Res.drawable.ic_add),
        increaseLabel = stringResource(Res.string.tuner_reference_pitch_increase),
        canIncrease = referencePitch < Pitch.REFERENCE_PITCH_RANGE.last,
        onIncrease = { onReferencePitchChanged(referencePitch + 1) },
        resetLabel = stringResource(Res.string.tuner_reference_pitch_reset),
        onReset = { onReferencePitchChanged(Pitch.DEFAULT_REFERENCE_PITCH) },
        repeatsOnHold = true,
        entry = StepperEntry(number = referencePitch, range = Pitch.REFERENCE_PITCH_RANGE, onEntered = onReferencePitchChanged),
    )
    val description = stringResource(Res.string.tuner_play_reference, noteName(REFERENCE_NOTE, notation))
    CampfireTooltip(label = description) {
        // On the Tuner tab, a tab swap carries it over as the New button whose spot it takes on a wide line: one
        // container, only the icon crossing (see SharedTabChrome), which draws the container in its stead.
        val containerColor = MaterialTheme.colorScheme.primary
        val chrome = if (isTabChromePart) {
            rememberSharedTabChrome(
                element = TabChromeElement.NEW_BUTTON,
                containerColor = containerColor,
                content = if (isReferenceTonePlaying) TabChromeContent.STOP else TabChromeContent.PLAY,
            )
        } else {
            null
        }
        val drawnContainerColor = if (chrome == null) containerColor else Color.Transparent
        FilledIconToggleButton(
            modifier = Modifier
                .sharedTabChrome(chrome = chrome, zIndex = NEW_BUTTON_Z_INDEX, isContainerDrawnOutsideSwaps = true)
                .size(NEW_ITEM_BUTTON_SIZE),
            checked = isReferenceTonePlaying,
            onCheckedChange = { onToggleReferenceTone() },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = drawnContainerColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                checkedContainerColor = drawnContainerColor,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            PlayStopMark(
                modifier = Modifier.semantics { contentDescription = description },
                isPlaying = isReferenceTonePlaying,
                color = LocalContentColor.current,
            )
        }
    }
}
