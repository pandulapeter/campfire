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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.tuner_in_tune
import com.pandulapeter.campfire.presentation.resources.tuner_string
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import org.jetbrains.compose.resources.painterResource

/**
 * One segment per string of [tuning], in the order the tuning is written and numbered down to the first, each named by
 * its note: the strings are one instrument, so they are one row of equal parts rather than chips wrapping wherever their
 * names end. A tap plays the string's tone, a second tap or another string ends it; the segment of the tone sounding is
 * selected and carries the stop mark, and so is the one being heard while none sounds, so the hand finds the string the
 * tuner is reading. A string heard in tune since the microphone was opened is ticked ([tunedNotes]), so that the player
 * sees which strings are still to do.
 */
@Composable
internal fun TunerStrings(
    tuning: InstrumentTuning,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    onToggleTone: (Int) -> Unit,
) = SingleChoiceSegmentedButtonRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
) {
    val inTune = stringResource(Res.string.tuner_in_tune)
    tuning.strings.forEachIndexed { index, note ->
        val name = noteNameWithOctave(note, notation)
        val description = stringResource(Res.string.tuner_string, tuning.strings.size - index, spokenNoteName(note, notation))
        val isSounding = tone == note
        val isTuned = note in tunedNotes
        val isSelected = if (tone != null) isSounding else heardNote == note
        SegmentedButton(
            // A segment is a radio button to Material, but a string is a button playing its tone, which a second tap
            // stops, and being selected only says which string is sounding or heard.
            modifier = Modifier.clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                selected = isSelected
                if (isTuned) stateDescription = inTune
                onClick {
                    onToggleTone(note)
                    true
                }
            },
            selected = isSelected,
            onClick = { onToggleTone(note) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = tuning.strings.size),
            icon = {},
            contentPadding = STRING_CONTENT_PADDING,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isSounding || isTuned,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    StringMark(isSounding = isSounding, color = LocalContentColor.current)
                }
                val style = MaterialTheme.typography.labelLarge
                Text(
                    text = name,
                    style = style,
                    fontWeight = if (isSounding) FontWeight.Bold else null,
                    softWrap = false,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = STRING_MIN_FONT_SIZE, maxFontSize = style.fontSize),
                )
            }
        }
    }
}

/** What a string's segment carries before its name: the stop mark while its tone sounds, the tick once it was in tune. */
@Composable
private fun StringMark(
    isSounding: Boolean,
    color: Color,
) = AnimatedContent(
    modifier = Modifier.padding(end = STRING_MARK_GAP),
    targetState = isSounding,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "tunerStringMark",
) { sounding ->
    if (sounding) {
        PlayStopMark(isPlaying = true, size = STRING_MARK_SIZE, color = color)
    } else {
        Icon(
            modifier = Modifier.size(STRING_MARK_SIZE),
            painter = painterResource(Res.drawable.ic_check),
            contentDescription = null,
            tint = color,
        )
    }
}

private val STRING_MARK_SIZE = 16.dp
private val STRING_MARK_GAP = 4.dp

/**
 * How far a string's name shrinks before it is cut: an eight-string guitar leaves each string about 40dp on a 360dp
 * phone, which a name with its mark beside it only fits in smaller.
 */
private val STRING_MIN_FONT_SIZE = 9.sp

/**
 * Six strings share the width of a 360dp phone, about 53dp each, and Material's 12dp at each side of a label would leave
 * a Latin `Sol3` with its mark no room. The vertical padding stays Material's, so the row keeps its height.
 */
private val STRING_CONTENT_PADDING = PaddingValues(
    start = 4.dp,
    top = SegmentedButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 4.dp,
    bottom = SegmentedButtonDefaults.ContentPadding.calculateBottomPadding(),
)
