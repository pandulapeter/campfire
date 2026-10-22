/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

/**
 * The names of the properties whose values differ between two `toString`s of one data class, read from the text, since
 * common code has no reflection: a property added later is covered without anybody remembering to. Null where either
 * text does not split into the same names, which a value with an unbalanced bracket or a ", name=" of its own can cause;
 * the caller then only says that something changed.
 */
internal fun changedPropertyNames(before: String, after: String): List<String>? {
    val old = topLevelProperties(before) ?: return null
    val new = topLevelProperties(after) ?: return null
    if (old.map { it.first } != new.map { it.first }) return null
    return old.zip(new).filter { (oldProperty, newProperty) -> oldProperty.second != newProperty.second }.map { it.first.first }
}

/** The `name=value` pairs between the outermost brackets of a data class's `toString`, nested ones kept whole. */
private fun topLevelProperties(text: String): List<Pair<String, String>>? {
    val start = text.indexOf('(')
    if (start == -1 || !text.endsWith(")")) return null
    val body = text.substring(start + 1, text.length - 1)
    val parts = mutableListOf<String>()
    var depth = 0
    var partStart = 0
    for (index in body.indices) {
        when (body[index]) {
            '(', '[', '{' -> depth++
            ')', ']', '}' -> if (--depth < 0) return null
            ',' -> if (depth == 0 && body.startsWith(", ", index)) {
                parts += body.substring(partStart, index)
                partStart = index + 2
            }
        }
    }
    if (depth != 0) return null
    parts += body.substring(partStart)
    return parts.map { part ->
        val separator = part.indexOf('=')
        val name = if (separator > 0) part.substring(0, separator) else return null
        if (!name.all { it.isLetterOrDigit() || it == '_' }) return null
        name to part.substring(separator + 1)
    }
}
