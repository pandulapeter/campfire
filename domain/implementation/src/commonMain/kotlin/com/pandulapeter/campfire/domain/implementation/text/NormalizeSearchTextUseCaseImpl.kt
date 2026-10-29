/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.text

import com.pandulapeter.campfire.domain.api.text.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.text.NormalizeTextUseCase
import org.koin.core.annotation.Factory

@Factory
internal class NormalizeSearchTextUseCaseImpl(
    private val normalizeText: NormalizeTextUseCase,
) : NormalizeSearchTextUseCase {

    /**
     * The marks are kept rather than dropped with the punctuation: the Latin ones and those [ScriptFolding] names (the
     * optional vowel points of Hebrew and Arabic) are already gone by now, and what is left of them are the vowel signs
     * of scripts like Devanagari or Thai, which are as much a part of the word as a letter is.
     */
    override fun invoke(text: String): String {
        val normalized = normalizeText(text)
        if (normalized.all { it.isSearchable() }) return normalized
        return buildString(normalized.length) {
            normalized.forEach { character -> if (character.isSearchable()) append(character) }
        }
    }

    private fun Char.isSearchable() = isLetterOrDigit() || when (category) {
        CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK -> true
        else -> false
    }
}
