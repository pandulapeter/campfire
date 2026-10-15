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

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Immutable

/**
 * What makes a [Stepper]'s value a field the number can also be typed into, for the values with a range too wide to
 * step across and a number people come with in mind (a tempo, a reference pitch). Everywhere else the value is tried
 * out a step at a time while its effect is watched, and stays plain text.
 *
 * @param number The value as a number, which is what is typed; the stepper's written value may hold more around it
 *   (`440 Hz`), which stays around what is typed.
 * @param onEntered Sets a number of [range], as soon as one is typed.
 */
@Immutable
internal data class StepperEntry(
    val number: Int,
    val range: IntRange,
    val onEntered: (Int) -> Unit,
)

/**
 * The number [text] sets as it stands, or null while it is none of [range]: nothing typed, or a number on its way to one
 * of the range - every number of 415..466 is reached through 4 and 44, and 300 through 30, which is in range and so set
 * on the way.
 */
internal fun typedNumber(text: CharSequence, range: IntRange) = text.toString().toIntOrNull()?.takeIf { it in range }

/**
 * What [value] writes before and after [number], so that the field shows the typed digits where the written value has
 * them. Around the last occurrence, since a unit comes after the number and what comes before it may hold digits of its
 * own.
 */
internal fun affixesAround(value: String, number: Int): Pair<String, String> {
    val digits = number.toString()
    val index = value.lastIndexOf(digits)
    return if (index < 0) "" to "" else value.substring(0, index) to value.substring(index + digits.length)
}

/** Digits only, and no more of them than the largest number of the range has, so that a paste cannot run on. */
internal class StepperDigits(private val maxLength: Int) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val digits = asCharSequence().filter(Char::isDigit).take(maxLength)
        // Filtering and truncating only ever shorten it, so the same length is the same text.
        if (digits.length != length) replace(0, length, digits)
    }
}
