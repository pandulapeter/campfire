/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.presentation.ui.components.scaled

/**
 * How a [Stepper]'s value is written. Its digits are tabular, every one as wide as the others, so that a number is as
 * wide as any other with as many digits and the ends of a range are the widest values of it (see [StepperValueRoom]).
 */
@Composable
internal fun stepperValueStyle(fontScale: Float) = MaterialTheme.typography.labelLarge.scaled(fontScale).copy(
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)

/**
 * Takes the room of the widest of [values] and [StepperResetMark] after it in the box it is put in, measured and never
 * drawn or read aloud: the widest values a stepper can show, so that its pill keeps one length whatever it shows and
 * whether it can be reset, and the buttons stay under the finger.
 *
 * @param isResettable Whether the room of a reset icon is kept too.
 */
@Composable
internal fun StepperValueRoom(
    modifier: Modifier = Modifier,
    values: List<String>,
    style: TextStyle,
    isResettable: Boolean,
    fontScale: Float,
) = Row(
    modifier = modifier
        .clearAndSetSemantics {}
        .drawWithContent {},
) {
    Box {
        values.forEach { value ->
            Text(
                text = value,
                style = style,
                softWrap = false,
            )
        }
    }
    if (isResettable) Spacer(Modifier.width(stepperResetMarkWidth(fontScale)))
}
