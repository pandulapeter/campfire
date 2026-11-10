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

import com.pandulapeter.campfire.abc.api.AbcMessage

/**
 * The messages `campfire-abc-host.js` wrote after a rendering ([records]), placed on [tune]: abc2svg counts lines in the
 * whole source, whose first [headerLines] are the renderer's own format lines, so a message about one of those (none
 * is expected) is left out.
 *
 * abc2svg names an unknown decoration but gives no position for it, so it is placed on every `!name!` and `+name+` of
 * the tune: the text that has to change.
 */
internal fun abcMessagesOf(records: String, headerLines: Int, tune: String): List<AbcMessage> = records.split(RECORD_SEPARATOR)
    .filter { it.isNotEmpty() }
    .flatMap { record ->
        val fields = record.split(FIELD_SEPARATOR, limit = 3)
        val text = fields.getOrNull(2) ?: return@flatMap emptyList()
        val line = fields[0].toIntOrNull()?.minus(headerLines)
        when {
            line == null -> UNKNOWN_DECORATION.find(text)?.groupValues?.get(1)?.let { name -> decorationsNamed(tune, name, text) }
                ?.ifEmpty { null } ?: listOf(AbcMessage(text = text, line = null, column = null))
            line < 0 -> emptyList()
            else -> listOf(AbcMessage(text = text, line = line, column = fields.getOrNull(1)?.toIntOrNull()))
        }
    }
    .distinct()

private fun decorationsNamed(tune: String, name: String, text: String) = tune.split('\n').flatMapIndexed { lineIndex, line ->
    listOf("!$name!", "+$name+").flatMap { decoration ->
        generateSequence(line.indexOf(decoration).takeIf { it >= 0 }) { previous -> line.indexOf(decoration, previous + decoration.length).takeIf { it >= 0 } }
            .map { column -> AbcMessage(text = text, line = lineIndex, column = column) }
            .toList()
    }
}

/** What separates the systems from the messages in the host's output, and one message from the next. */
internal const val RECORD_SEPARATOR = '\u0001'
private const val FIELD_SEPARATOR = '\u0002'
private val UNKNOWN_DECORATION = Regex("""Unknown decoration '(.+)'""")
