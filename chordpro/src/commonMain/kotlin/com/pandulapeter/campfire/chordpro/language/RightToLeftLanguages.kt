/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

/**
 * The languages written from the right, by the code a song files its language under (see
 * `ChordProMetaItems.languageCode`): CLDR's right-to-left languages that have a code of their own, Arabic, Aramaic,
 * Central Kurdish, Divehi, Persian, Hebrew, Kashmiri, Pashto, Sindhi, Syriac, Uyghur, Urdu, and Yiddish. The
 * `add-language` skill's `language.py` reads this list, so a language added there is laid out from the right too.
 */
public object RightToLeftLanguages {

    private val codes = setOf("ar", "arc", "ckb", "dv", "fa", "he", "ks", "ps", "sd", "syr", "ug", "ur", "yi")

    /** Whether [code] names a language written from the right, a retired code ([LanguageCodes]) included. */
    public fun contains(code: String): Boolean = LanguageCodes.current(code) in codes
}
