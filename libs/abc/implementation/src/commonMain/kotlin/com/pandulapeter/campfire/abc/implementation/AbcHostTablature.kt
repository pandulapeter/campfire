/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import com.pandulapeter.campfire.abc.api.AbcTablature

/**
 * The header line `campfire-abc-host.js` reads [tablature] from: an ABC comment, which abc2svg skips, holding it as JSON
 * after [HOST_TABLATURE_PREFIX], on one line, so that the lines abc2svg counts after the header are still the tune's.
 */
internal fun hostTablatureLineOf(tablature: AbcTablature) = buildString {
    append(HOST_TABLATURE_PREFIX)
    append("{\"tuning\":")
    append(tablature.tuning?.joinToString(separator = ",", prefix = "[", postfix = "]") ?: "null")
    append(",\"capo\":").append(tablature.capo)
    append(",\"maxFret\":").append(tablature.maxFret)
    append(",\"noteNames\":")
    append(tablature.noteNames.joinToString(separator = ",", prefix = "[", postfix = "]") { name -> "\"${name.jsonEscaped()}\"" })
    append("}\n")
}

/** A name as a JSON string's content: on one line, with no quote or backslash ending it early. */
private fun String.jsonEscaped() = buildString {
    this@jsonEscaped.forEach { character ->
        when {
            character == '"' || character == '\\' -> append('\\').append(character)
            character < ' ' -> append(' ')
            else -> append(character)
        }
    }
}

/** What opens the line, matched by the host. */
internal const val HOST_TABLATURE_PREFIX = "%campfire-tablature "
