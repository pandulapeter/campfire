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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_bass
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_more
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_ukulele

/**
 * Chromatic and the [InstrumentFamily] segments, one row of equal parts. Five of them share the width of a 360dp phone,
 * about 65dp each, so a segment carries no check mark (its fill marks it, as the strings' do), its label is padded
 * less than Material's and shrinks rather than being cut short where it is still too long, which "Chromatic" and the
 * Hungarian "Kromatikus" are.
 */
@Composable
internal fun InstrumentFamilySegments(
    modifier: Modifier = Modifier,
    selected: InstrumentFamily?,
    onSelected: (InstrumentFamily?) -> Unit,
) = SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
) {
    val families = listOf(null) + InstrumentFamily.entries
    families.forEachIndexed { index, family ->
        SegmentedButton(
            selected = family == selected,
            onClick = { onSelected(family) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = families.size),
            icon = {},
            contentPadding = SEGMENT_CONTENT_PADDING,
        ) {
            val style = MaterialTheme.typography.labelLarge
            Text(
                text = familyLabel(family),
                style = style,
                softWrap = false,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = SEGMENT_MIN_FONT_SIZE, maxFontSize = style.fontSize),
            )
        }
    }
}

@Composable
private fun familyLabel(family: InstrumentFamily?) = stringResource(
    when (family) {
        null -> Res.string.tuner_instrument_chromatic
        InstrumentFamily.GUITAR -> Res.string.tuner_instrument_guitar
        InstrumentFamily.BASS -> Res.string.tuner_instrument_bass
        InstrumentFamily.UKULELE -> Res.string.tuner_instrument_ukulele
        InstrumentFamily.MORE -> Res.string.tuner_instrument_more
    }
)

private val SEGMENT_MIN_FONT_SIZE = 10.sp

/** The vertical padding stays Material's, so the row keeps its height. */
private val SEGMENT_CONTENT_PADDING = PaddingValues(
    start = 4.dp,
    top = SegmentedButtonDefaults.ContentPadding.calculateTopPadding(),
    end = 4.dp,
    bottom = SegmentedButtonDefaults.ContentPadding.calculateBottomPadding(),
)
