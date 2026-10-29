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

import org.jetbrains.compose.resources.PluralStringResource

/**
 * The template of [key] that reads [count] in [locale], its placeholders unfilled, the item picked by [pluralItemOf]
 * from the language's rule; a key the language lacks is read from the English table, as every other lookup is.
 */
internal fun pluralTemplate(key: PluralStringResource, count: Int, locale: AppLocale): String =
    (locale.plurals[key.key] ?: AppLocale.DEFAULT.plurals[key.key])?.let { pluralItemOf(it, locale.code, count) } ?: MISSING

/**
 * The `<plurals>` of [this] string table, by key and quantity. They are read here rather than through the generated
 * `LocalizedStrings.getPlural`, whose own choice of item knows no language's rule (it takes `two` for 2 and `many` for
 * anything above in every language and never reaches `few`). The `when` names each generated table, so a new language
 * does not build until it is added here.
 */
private val AppLocale.plurals
    get() = when (this) {
        AppLocale.DEFAULT -> StringsDefault.plurals
        AppLocale.DE -> StringsDe.plurals
        AppLocale.ES -> StringsEs.plurals
        AppLocale.FR -> StringsFr.plurals
        AppLocale.HE -> StringsHe.plurals
        AppLocale.HU -> StringsHu.plurals
        AppLocale.IT -> StringsIt.plurals
        AppLocale.RO -> StringsRo.plurals
    }

/** What the generated lookup shows for a key no table holds. */
private const val MISSING = "???"
