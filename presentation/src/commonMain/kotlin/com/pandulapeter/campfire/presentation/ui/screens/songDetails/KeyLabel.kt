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

import androidx.compose.runtime.Immutable

/**
 * The muted words the key chip ([KeyChip]) sets around its key, in the order the language puts them ("Key of G", "G
 * shapes", "G-Griffe"): [before] and [after] the key, each with a gap beside the key only where the template spaces it
 * from the key, so that a hyphenated compound stays joined to it.
 */
@Immutable
internal data class KeyLabel(
    val before: String,
    val isBeforeSpaced: Boolean,
    val after: String,
    val isAfterSpaced: Boolean,
)

/**
 * The [KeyLabel] of a string resource's [template], which names the key as `%1$s`. A template naming no key is taken
 * for words that lead it, as the label once was.
 */
internal fun keyLabelOf(template: String): KeyLabel {
    val index = template.indexOf(KEY_PLACEHOLDER)
    if (index < 0) return KeyLabel(before = template.trim(), isBeforeSpaced = true, after = "", isAfterSpaced = false)
    val before = template.substring(0, index)
    val after = template.substring(index + KEY_PLACEHOLDER.length)
    return KeyLabel(
        before = before.trim(),
        isBeforeSpaced = before.lastOrNull()?.isWhitespace() == true,
        after = after.trim(),
        isAfterSpaced = after.firstOrNull()?.isWhitespace() == true,
    )
}

private const val KEY_PLACEHOLDER = "%1\$s"
