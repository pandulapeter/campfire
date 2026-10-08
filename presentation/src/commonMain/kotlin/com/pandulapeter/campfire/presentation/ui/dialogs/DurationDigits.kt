/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How many digits the field takes: `hh:mm:ss`. */
internal const val MAX_DURATION_DIGITS = 6

/** The longest duration the field's six digits can show. */
private val MAX_DURATION = 99.hours + 59.minutes + 59.seconds

/** What may be kept of [text] typed into the duration field: its digits, without leading zeros, at most six of them. */
internal fun durationDigitsTyped(text: String) = text.filter { it in '0'..'9' }.trimStart('0').take(MAX_DURATION_DIGITS)

/**
 * The digits the field shows for a song's `{duration}`, empty where there is none it can read, or one longer than the
 * field's six digits can show: a seventh digit would be cut off by the next one typed, and the value with it.
 */
internal fun durationDigitsOf(text: String?) = ChordProDuration.parse(text)?.takeIf { it <= MAX_DURATION }?.let { duration ->
    ChordProDuration.format(duration).filter { it != ':' }.trimStart('0')
}.orEmpty()

/**
 * The `{duration}` [digits] stand for, or an empty string for none. The last two digits are the seconds and the two
 * before them the minutes, and each may say more than its unit holds (`0:75`), as on a timer, which is carried over -
 * up to `99:59:59`, the most the field can show, so that what it writes it also opens with.
 */
internal fun durationTextOf(digits: String): String {
    if (digits.isEmpty()) return ""
    val padded = digits.padStart(MAX_DURATION_DIGITS, '0')
    val duration = (padded.dropLast(4).toInt().hours + padded.takeLast(4).take(2).toInt().minutes + padded.takeLast(2).toInt().seconds)
        .coerceAtMost(MAX_DURATION)
    return if (duration.isPositive()) ChordProDuration.format(duration) else ""
}

/** [digits] laid out with their colons as they are typed: `5` is `0:05`, `428` is `4:28`, `12345` is `1:23:45`. */
internal fun durationDigitsMask(digits: String): String {
    if (digits.isEmpty()) return ""
    val padded = digits.padStart(3, '0')
    val seconds = padded.takeLast(2)
    return if (padded.length <= 4) {
        "${padded.dropLast(2)}:$seconds"
    } else {
        "${padded.dropLast(4)}:${padded.takeLast(4).take(2)}:$seconds"
    }
}

/** The song metadata form's draft of [values]: the duration as the digits its field is typed in, the rest as they are. */
internal fun Map<Field, String>.toSongMetadataDraft() = this + (Field.DURATION to durationDigitsOf(this[Field.DURATION]))

/** What a draft made by [toSongMetadataDraft] writes into the song. */
internal fun Map<Field, String>.fromSongMetadataDraft() = this + (Field.DURATION to durationTextOf(this[Field.DURATION].orEmpty()))

/**
 * Shows the duration field's digits through [durationDigitsMask], so that the field is typed the way a timer is set:
 * digits only, filling in from the right, `428` reading `4:28`. A field that asked for `4:28` itself would need a
 * colon, which the number pads of most Android keyboards do not have, and a text keyboard for a number is worse than
 * either. The form keeps the digits as its draft ([toSongMetadataDraft]) and the song gets ChordProDuration's spelling
 * of them on Save. Every offset maps to the end, which is where a timer's digits are typed and deleted: a caret placed
 * between them would edit a mask whose colons move under it.
 */
internal object DurationDigitsTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val masked = durationDigitsMask(text.text)
        return TransformedText(
            text = AnnotatedString(masked),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = masked.length

                override fun transformedToOriginal(offset: Int) = text.length
            },
        )
    }
}
