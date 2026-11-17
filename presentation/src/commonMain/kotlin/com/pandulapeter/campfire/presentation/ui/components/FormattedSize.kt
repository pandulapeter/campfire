/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_library_size_bytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.resources.settings_library_size_gigabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_kilobytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_megabytes

/**
 * [bytes] in the largest unit that keeps the number at least one, in decimal units as the file browsers of Android and
 * Apple count them. One decimal below ten and none above, which is as precise as a number that changes with every
 * saved song is worth being. The separator comes from the strings rather than from the platform, so that it follows
 * the language chosen in the app like the words around it.
 */
@Composable
internal fun formattedSize(bytes: Long): String {
    val size = byteSizeOf(bytes)
    val number = when (val tenths = size.tenths) {
        null -> size.whole.toString()
        else -> "${size.whole}${stringResource(Res.string.settings_library_size_decimal_separator)}$tenths"
    }
    return stringResource(SIZE_UNITS[size.unit], number)
}

/**
 * The number [formattedSize] writes: [whole] of the unit at [unit] in [SIZE_UNITS], and [tenths] after the separator
 * where it writes a decimal.
 */
internal data class ByteSize(
    val unit: Int,
    val whole: Long,
    val tenths: Long? = null,
)

/** [bytes] rounded the way [formattedSize] writes them. */
internal fun byteSizeOf(bytes: Long): ByteSize {
    if (bytes < SIZE_STEP) return ByteSize(unit = 0, whole = bytes)
    // The unit is settled on the rounded number, so that 999 960 bytes read as 1.0 MB rather than as 1000 KB.
    val unit = (1..SIZE_UNITS.lastIndex).firstOrNull { unit -> (bytes + SIZE_STEP.pow(unit) / 2) / SIZE_STEP.pow(unit + 1) == 0L }
        ?: SIZE_UNITS.lastIndex
    val divisor = SIZE_STEP.pow(unit)
    val tenths = (bytes * 10 + divisor / 2) / divisor
    return if (tenths < 100) {
        ByteSize(unit = unit, whole = tenths / 10, tenths = tenths % 10)
    } else {
        ByteSize(unit = unit, whole = (bytes + divisor / 2) / divisor)
    }
}

private fun Long.pow(exponent: Int) = (1..exponent).fold(1L) { result, _ -> result * this }

private const val SIZE_STEP = 1000L

private val SIZE_UNITS = listOf(
    Res.string.settings_library_size_bytes,
    Res.string.settings_library_size_kilobytes,
    Res.string.settings_library_size_megabytes,
    Res.string.settings_library_size_gigabytes,
)
