/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

/**
 * [this] in a first-strong isolate (U+2068 … U+2069): put into a sentence, it takes its direction from its own first
 * strong letter and leaves the sentence's alone, so a Hebrew title keeps its order in an English sentence, a Latin one
 * in a Hebrew sentence, and a title ending in "!" or a number keeps its punctuation on its own side. The marks are drawn
 * as nothing on every platform, but they are characters all the same: text written to a file, printed, copied, or
 * typed into a field takes the plain value, never this. An empty text is left as it is, there being nothing to order.
 */
internal fun String.isolated() = if (isEmpty()) this else "$FIRST_STRONG_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"

/** [this] in a left-to-right isolate (U+2066 … U+2069), for music (a list of note names) in a sentence of any direction. */
internal fun String.isolatedLeftToRight() = if (isEmpty()) this else "$LEFT_TO_RIGHT_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"

/**
 * [this] joined with [separator], each part [isolated], so that a separator between two parts of another direction than
 * the line's (an artist and an album in Hebrew under an English interface) stays between them.
 */
internal fun Iterable<String>.joinIsolated(separator: String) = joinToString(separator) { it.isolated() }

private const val LEFT_TO_RIGHT_ISOLATE = '⁦'
private const val FIRST_STRONG_ISOLATE = '⁨'
private const val POP_DIRECTIONAL_ISOLATE = '⁩'
