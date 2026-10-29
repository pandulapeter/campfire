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

/**
 * What one script writes optionally on top of its letters, which sorting and searching look through: the marks a text
 * may or may not carry (vowel points, cantillation, short vowels), and the letters with a form of their own at the end
 * of a word, which are the same letter to someone looking for a word they may have typed only the start of.
 *
 * Only the sort and search keys are folded this way, never a file name (`LibraryFiles.normalizedName` keeps its own
 * rules): a name is never migrated, so a changed rule would file one title under two names over time.
 *
 * @param marks The code points whose marks are dropped. Only a nonspacing mark (Mn) among them is: a block mixes its
 *   marks with punctuation and letters (a hyphen or a verse end between the points), which stay.
 * @param letterForms The contextual forms of letters, each folded to the letter at the same index of [baseLetters].
 */
internal class ScriptFolding(
    val marks: List<CharRange>,
    val letterForms: String,
    val baseLetters: String,
)

/**
 * One row per script, Hebrew first: its points and cantillation, and its five final letters. Arabic's harakat and the
 * superscript alef follow; its letters' contextual forms are separate code points only in the presentation forms
 * blocks, which text typed today does not use.
 */
private val SCRIPT_FOLDINGS = listOf(
    ScriptFolding(
        marks = listOf('֑'..'ׇ'),
        letterForms = "ךםןףץ",
        baseLetters = "כמנפצ",
    ),
    ScriptFolding(
        marks = listOf('ً'..'ٟ', 'ٰ'..'ٰ'),
        letterForms = "",
        baseLetters = "",
    ),
)

/** Below the first script of [SCRIPT_FOLDINGS] nothing is folded, which is every character of a Latin or Cyrillic text. */
private val FIRST_FOLDED = SCRIPT_FOLDINGS.minOf { folding ->
    minOf(folding.marks.minOf { it.first }, folding.letterForms.minOrNull() ?: Char.MAX_VALUE)
}

/** A mark of one of [SCRIPT_FOLDINGS]' rows, which sorting and searching drop. */
internal fun Char.isFoldableMark() = this >= FIRST_FOLDED &&
    category == CharCategory.NON_SPACING_MARK &&
    SCRIPT_FOLDINGS.any { folding -> folding.marks.any { this in it } }

/** The letter behind a contextual form of one of [SCRIPT_FOLDINGS]' rows, or the character itself. */
internal fun Char.withBaseLetterForm(): Char {
    if (this < FIRST_FOLDED) return this
    SCRIPT_FOLDINGS.forEach { folding ->
        val index = folding.letterForms.indexOf(this)
        if (index >= 0) return folding.baseLetters[index]
    }
    return this
}
