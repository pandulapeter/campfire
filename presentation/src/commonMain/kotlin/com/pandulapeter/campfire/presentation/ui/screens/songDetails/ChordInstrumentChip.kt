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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.song_details_chord_instrument_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_instrument_tuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import org.jetbrains.compose.resources.painterResource

/**
 * The instrument the Chords section's diagrams are drawn for, and its tuning where that is not the standard one, named
 * after its pill and opening the sheet that changes them. It names them rather than showing an icon, since diagrams for
 * the wrong instrument or tuning look just as right as the ones for the right one, and its chevron says that it is a
 * choice rather than an edit of the song. The tuning's note is written in [notation], as Settings writes it. A tonal pill
 * like the steppers, as tall as the section's own pill ([height]), its name in the same style; the name crossfades as it
 * changes and the pill resizes with it, and a name too long for the row is cut short rather than wrapped.
 */
@Composable
internal fun ChordInstrumentChip(
    modifier: Modifier = Modifier,
    tuning: ChordTuning,
    notation: ChordNotation,
    height: Dp,
    style: TextStyle,
    chevronSize: Dp,
    onClick: () -> Unit,
) = Surface(
    modifier = modifier.height(height),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Row(
        modifier = Modifier
            .clickable(onClickLabel = stringResource(Res.string.song_details_chord_instrument_choose), role = Role.Button, onClick = onClick)
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
            .padding(start = CHIP_START_PADDING, end = CHIP_END_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            modifier = Modifier.weight(1f, fill = false),
            targetState = tuning,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        ) { shownTuning ->
            val instrumentName = stringResource(shownTuning.instrument.nameResource)
            Text(
                text = if (shownTuning.kind == ChordTuning.Kind.Standard) {
                    instrumentName
                } else {
                    stringResource(Res.string.song_details_chord_instrument_tuning, instrumentName, chordTuningLabel(shownTuning, notation))
                },
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            modifier = Modifier.padding(start = CHIP_CHEVRON_GAP).size(chevronSize),
            painter = painterResource(Res.drawable.ic_expand),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The name Settings gives the instrument, which is what the sheet this opens lists it as. */
private val ChordInstrument.nameResource
    get() = when (this) {
        ChordInstrument.GUITAR -> Res.string.settings_chord_instrument_guitar
        ChordInstrument.UKULELE -> Res.string.settings_chord_instrument_ukulele
        ChordInstrument.MANDOLIN -> Res.string.settings_chord_instrument_mandolin
        ChordInstrument.KEYBOARD -> Res.string.settings_chord_instrument_keyboard
    }

private val CHIP_START_PADDING = 12.dp
private val CHIP_END_PADDING = 8.dp
private val CHIP_CHEVRON_GAP = 2.dp
