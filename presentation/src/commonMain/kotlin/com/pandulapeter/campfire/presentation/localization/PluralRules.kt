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

import com.pandulapeter.campfire.presentation.localization.PluralCategory.FEW
import com.pandulapeter.campfire.presentation.localization.PluralCategory.MANY
import com.pandulapeter.campfire.presentation.localization.PluralCategory.ONE
import com.pandulapeter.campfire.presentation.localization.PluralCategory.OTHER
import com.pandulapeter.campfire.presentation.localization.PluralCategory.TWO
import com.pandulapeter.campfire.presentation.localization.PluralCategory.ZERO

/** A CLDR plural category, named by the `quantity` of the `<plurals>` item that reads a count falling into it. */
internal enum class PluralCategory(val quantity: String) {
    ZERO("zero"),
    ONE("one"),
    TWO("two"),
    FEW("few"),
    MANY("many"),
    OTHER("other"),
}

/**
 * CLDR's cardinal plural rules, by the language code of a string table: which category each count falls into. A count
 * the app shows is always whole, so of a rule's operands only n (the count) is left, every other being 0. A language is
 * here because its rule is, never because of a check naming it elsewhere, and every language of the app has a row
 * (a test holds that); one without would read like English.
 */
private val PLURAL_RULES: Map<String, (Int) -> PluralCategory> = mapOf(
    "he" to { n ->
        when (n) {
            1 -> ONE
            2 -> TWO
            else -> OTHER
        }
    },
    "en" to { n -> if (n == 1) ONE else OTHER },
    "de" to { n -> if (n == 1) ONE else OTHER },
    "hu" to { n -> if (n == 1) ONE else OTHER },
    "es" to { n -> if (n == 1) ONE else millionsOrOther(n) },
    "it" to { n -> if (n == 1) ONE else millionsOrOther(n) },
    "fr" to { n -> if (n == 0 || n == 1) ONE else millionsOrOther(n) },
    "ro" to { n ->
        when {
            n == 1 -> ONE
            n == 0 || n % 100 in 1..19 -> FEW
            else -> OTHER
        }
    },
)

/** The `many` of the Romance languages, which a whole million takes ("un million de chansons"). */
private fun millionsOrOther(n: Int) = if (n != 0 && n % 1_000_000 == 0) MANY else OTHER

/** The languages [PLURAL_RULES] has a rule for. */
internal val pluralRuleLanguages get() = PLURAL_RULES.keys

/** The category [count] falls into in the language [languageCode], a count below 0 (which no sentence shows) being other in every language. */
internal fun pluralCategoryOf(languageCode: String, count: Int): PluralCategory =
    (PLURAL_RULES[languageCode] ?: PLURAL_RULES.getValue(FALLBACK_LANGUAGE))(count)

/**
 * The item of a `<plurals>` (its [items] by quantity) that reads [count] in [languageCode]. A `zero` item reads 0 in any
 * language, before the category the rule gives it, since a sentence about nothing is often worded apart ("Aucune
 * chanson" where the French rule would say "0 chanson"). A category the plural has no item for reads as its `other`,
 * as Android reads it.
 */
internal fun pluralItemOf(items: Map<String, String>, languageCode: String, count: Int): String? =
    items[ZERO.quantity]?.takeIf { count == 0 } ?: items[pluralCategoryOf(languageCode, count).quantity] ?: items[OTHER.quantity]

private const val FALLBACK_LANGUAGE = "en"
